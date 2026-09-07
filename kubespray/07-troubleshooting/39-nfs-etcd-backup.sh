#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# NFS VM의 root cron에서 실행합니다.
# - /var/lib/neuroplan/credentials/kube_encrypt_token.creds를 보호 상태로 확인
# - CP1에서 온라인 etcd snapshot 생성
# - /backup/etcd/<UTC timestamp>/에 snapshot과 SHA-256 저장
# - credential은 snapshot 디렉터리로 복사하지 않음
#
# NFS VM에 /usr/local/sbin/neuroplan-etcd-backup.sh로 설치해야 합니다.
# SSH는 비밀번호 없이 k8sadmin -> CP1 sudo가 가능해야 합니다.

if [[ ${EUID} -ne 0 ]]; then
  echo "[FAIL] run as root on the NFS VM" >&2
  exit 1
fi

SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"

fail() { echo "[FAIL] $*" >&2; exit 1; }
pass() { echo "[PASS] $*"; }
info() { echo "[INFO] $*"; }

CREDENTIAL_FILE="${CREDENTIAL_FILE:-/var/lib/neuroplan/credentials/kube_encrypt_token.creds}"
BACKUP_DIR="${BACKUP_DIR:-/backup/etcd}"
BACKUP_MARKER="${BACKUP_MARKER:-$BACKUP_DIR/.neuroplan-etcd-backup-root}"
CP1_HOST="${CP1_HOST:-192.168.14.31}"
SSH_USER="${SSH_USER:-k8sadmin}"
SSH_KEY="${SSH_KEY:-/root/.ssh/neuroplan_k8s}"
REMOTE_DIR="${REMOTE_DIR:-/var/backups/etcd}"
ETCDCTL="${ETCDCTL:-/usr/local/bin/etcdctl}"
ETCDUTL="${ETCDUTL:-/usr/local/bin/etcdutl}"
ETCD_ENDPOINT="${ETCD_ENDPOINT:-https://127.0.0.1:2379}"
ETCD_CACERT="${ETCD_CACERT:-/etc/ssl/etcd/ssl/ca.pem}"
ETCD_CERT="${ETCD_CERT:-/etc/ssl/etcd/ssl/node-cp1.pem}"
ETCD_KEY="${ETCD_KEY:-/etc/ssl/etcd/ssl/node-cp1-key.pem}"
RETENTION_DAYS="${RETENTION_DAYS:-0}"
TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
SNAPSHOT_BASENAME="neuroplan-etcd-${TIMESTAMP}.db"
SNAPSHOT_DIR="$BACKUP_DIR/$TIMESTAMP"
INCOMING_DIR="$BACKUP_DIR/.incoming-${TIMESTAMP}-$$"
LOCK_FILE="${LOCK_FILE:-/run/lock/neuroplan-etcd-backup.lock}"

for command in date find install stat awk grep sha256sum ssh flock findmnt; do
  command -v "$command" >/dev/null 2>&1 || fail "$command not found"
done

[[ -r "$CREDENTIAL_FILE" ]] || fail "credential file is missing: $CREDENTIAL_FILE"
[[ "$(stat -c '%u' "$CREDENTIAL_FILE")" == "0" ]] || fail "credential file must be owned by root"
[[ "$(stat -c '%g' "$CREDENTIAL_FILE")" == "0" ]] || fail "credential file group must be root"
[[ "$(stat -c '%a' "$CREDENTIAL_FILE")" == "600" ]] || fail "credential file mode must be 600"
[[ "$(stat -c '%s' "$CREDENTIAL_FILE")" == "32" ]] || fail "credential file must contain exactly 32 bytes"
LC_ALL=C grep -Eq '^[A-Za-z0-9]{32}$' "$CREDENTIAL_FILE" ||
  fail "credential file must contain a 32-character alphanumeric token"
pass "encryption credential is present and protected"

[[ -r "$SSH_KEY" ]] || fail "SSH key is not readable: $SSH_KEY"
[[ "$(stat -c '%u' "$SSH_KEY")" == "0" ]] || fail "SSH key must be owned by root"
SSH_KEY_MODE="$(stat -c '%a' "$SSH_KEY")"
[[ "$SSH_KEY_MODE" == "600" || "$SSH_KEY_MODE" == "400" ]] ||
  fail "SSH key mode must be 600 or 400 (current: $SSH_KEY_MODE)"

install -d -m 0700 "$BACKUP_DIR"
[[ -f "$BACKUP_MARKER" ]] ||
  fail "backup marker is missing: $BACKUP_MARKER (refusing to write to an unverified path)"
BACKUP_FSTYPE="$(findmnt -T "$BACKUP_DIR" -no FSTYPE 2>/dev/null || true)"
[[ "$BACKUP_FSTYPE" != "nfs" && "$BACKUP_FSTYPE" != "nfs4" ]] ||
  fail "$BACKUP_DIR is unexpectedly mounted as NFS on the NFS server"
[[ -w "$BACKUP_DIR" ]] || fail "$BACKUP_DIR is not writable"
pass "NFS export directory is local and writable"

install -d -m 0700 "$(dirname -- "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
flock -n 9 || fail "another etcd backup is already running"

SSH_OPTS=(
  -i "$SSH_KEY"
  -o BatchMode=yes
  -o StrictHostKeyChecking=yes
  -o ConnectTimeout=10
  -o ServerAliveInterval=15
  -o ServerAliveCountMax=3
)
REMOTE="$SSH_USER@$CP1_HOST"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'sudo -n true' ||
  fail "passwordless sudo over SSH is not available for $REMOTE"

REMOTE_FILE="$REMOTE_DIR/$SNAPSHOT_BASENAME"
REMOTE_HASH="$REMOTE_FILE.sha256"
LOCAL_HASH="$INCOMING_DIR/$SNAPSHOT_BASENAME.sha256"

cleanup() { rm -rf -- "$INCOMING_DIR" 2>/dev/null || true; }
trap cleanup EXIT

install -d -m 0700 "$INCOMING_DIR"
echo "===== etcd snapshot from $CP1_HOST: $TIMESTAMP UTC ====="

REMOTE_COMMAND="set -Eeuo pipefail; sudo -n test -x '$ETCDCTL'; sudo -n test -x '$ETCDUTL'; sudo -n test -r '$ETCD_CACERT'; sudo -n test -r '$ETCD_CERT'; sudo -n test -r '$ETCD_KEY'; sudo -n install -d -m 0700 '$REMOTE_DIR'; sudo -n '$ETCDCTL' --endpoints='$ETCD_ENDPOINT' --cacert='$ETCD_CACERT' --cert='$ETCD_CERT' --key='$ETCD_KEY' endpoint health --cluster; sudo -n '$ETCDCTL' --endpoints='$ETCD_ENDPOINT' --cacert='$ETCD_CACERT' --cert='$ETCD_CERT' --key='$ETCD_KEY' snapshot save '$REMOTE_FILE'; sudo -n '$ETCDUTL' snapshot status '$REMOTE_FILE' --write-out=table; sudo -n bash -c \"cd '$REMOTE_DIR' && sha256sum '$SNAPSHOT_BASENAME' > '$REMOTE_HASH'\""
ssh "${SSH_OPTS[@]}" "$REMOTE" "$REMOTE_COMMAND"
pass "CP1 etcd snapshot created and checked"

ssh "${SSH_OPTS[@]}" "$REMOTE" "sudo -n cat '$REMOTE_FILE'" > "$INCOMING_DIR/$SNAPSHOT_BASENAME"
ssh "${SSH_OPTS[@]}" "$REMOTE" "sudo -n cat '$REMOTE_HASH'" > "$LOCAL_HASH"
chmod 600 "$INCOMING_DIR/$SNAPSHOT_BASENAME" "$LOCAL_HASH"

(cd "$INCOMING_DIR" && sha256sum -c "$(basename -- "$LOCAL_HASH")")
pass "local snapshot SHA-256 verified"

printf 'created_at_utc=%s\nsource=cp1\ncredential_file=%s\n' \
  "$TIMESTAMP" "$CREDENTIAL_FILE" > "$INCOMING_DIR/metadata.txt"
chmod 600 "$INCOMING_DIR/metadata.txt"

[[ ! -e "$SNAPSHOT_DIR" ]] || fail "snapshot destination already exists: $SNAPSHOT_DIR"
mv -- "$INCOMING_DIR" "$SNAPSHOT_DIR"
pass "snapshot stored: $SNAPSHOT_DIR"

if [[ "$RETENTION_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d \
    -name '20????????-??????' -mtime "+$RETENTION_DAYS" \
    -exec rm -rf -- {} +
  info "retention applied: $RETENTION_DAYS days"
fi

echo "[PASS] $SCRIPT_NAME completed"

#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
DB_HOST="${DB_HOST:-192.168.44.21}"
DB_PORT="${DB_PORT:-4006}"
DB_NAME="${DB_NAME:-infraready}"
DB_USERNAME="${DB_USERNAME:-ir_app}"

command -v mariadb >/dev/null 2>&1 || { echo "[FAIL] mariadb client not found" >&2; exit 1; }

echo "[INFO] idempotent learning seed: ${DB_USERNAME}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "[INFO] base subjects and certification subjects are seeded; DDL is not executed"
{
  cat "$APP_DIR/db/01-seed-learning-content.sql"
  printf '\n'
  cat "$APP_DIR/db/05-seed-certification-subjects.sql"
} | mariadb --no-defaults --disable-ssl \
  --protocol=TCP \
  -h "$DB_HOST" \
  -P "$DB_PORT" \
  -u "$DB_USERNAME" \
  -p \
  "$DB_NAME"

echo "[PASS] learning subjects, certification subjects, and minimum diagnosis content are ready"

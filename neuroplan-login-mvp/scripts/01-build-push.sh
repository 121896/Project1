#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
REGISTRY="${REGISTRY:-harbor.nplan.local:80/neuroplan}"
VERSION="${VERSION:-0.8.0}"
AUTHFILE="${REGISTRY_AUTH_FILE:-$HOME/.config/containers/auth.json}"

command -v podman >/dev/null 2>&1 || { echo "[FAIL] podman not found" >&2; exit 1; }
[[ -f "$APP_DIR/frontend/index.html" ]] || {
  echo "[FAIL] expected $APP_DIR/frontend/index.html" >&2
  exit 2
}

BUILD_AUTH=()
if [[ -s "$AUTHFILE" ]]; then
  BUILD_AUTH=(--authfile "$AUTHFILE")
  echo "[INFO] Container registry auth file: $AUTHFILE"
else
  echo "[WARN] Container registry auth file not found; Harbor push may require a prior podman login"
fi

echo "[INFO] building frontend:${VERSION}"
podman build --tls-verify=false "${BUILD_AUTH[@]}" \
  -f "$APP_DIR/frontend/Dockerfile" \
  -t "$REGISTRY/frontend:$VERSION" \
  "$APP_DIR/frontend"

echo "[INFO] building backend:${VERSION}"
podman build --tls-verify=false "${BUILD_AUTH[@]}" \
  -f "$APP_DIR/backend/Dockerfile" \
  -t "$REGISTRY/backend:$VERSION" \
  "$APP_DIR/backend"

podman push --tls-verify=false "$REGISTRY/frontend:$VERSION"
podman push --tls-verify=false "$REGISTRY/backend:$VERSION"

echo "[PASS] images pushed"
printf '  %s\n' "$REGISTRY/frontend:$VERSION" "$REGISTRY/backend:$VERSION"

#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_ENV="${DEPLOY_ENV:-prod}"
BACKUP_ARG=""

usage() {
  cat <<'EOF'
Usage:
  scripts/rollback_release.sh --env <prod|test> --backup <path>
EOF
}

log() { echo "[$(date '+%F %T')] $*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      DEPLOY_ENV="${2:-}"
      shift 2
      ;;
    --backup)
      BACKUP_ARG="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$BACKUP_ARG" ]] || fail "--backup is required"
[[ -f "$BACKUP_ARG" ]] || fail "Backup file not found: $BACKUP_ARG"

cd "$APP_DIR"
# shellcheck disable=SC1091
source "$APP_DIR/scripts/lib/deploy_env.sh"
setup_stack_env

: "${POSTGRES_USER:?POSTGRES_USER is required in $ENV_FILE}"
: "${POSTGRES_DB:?POSTGRES_DB is required in $ENV_FILE}"

log "Starting database"
"${COMPOSE[@]}" up -d "$DB_SERVICE"
wait_for_service_healthy "$DB_SERVICE"

log "Stopping application"
"${COMPOSE[@]}" stop "$APP_SERVICE" || true

log "Recreating database $POSTGRES_DB"
"${COMPOSE[@]}" exec -T "$DB_SERVICE" dropdb -U "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB"
"${COMPOSE[@]}" exec -T "$DB_SERVICE" createdb -U "$POSTGRES_USER" "$POSTGRES_DB"

log "Restoring database from $BACKUP_ARG"
cat "$BACKUP_ARG" | "${COMPOSE[@]}" exec -T "$DB_SERVICE" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges

log "Starting application"
"${COMPOSE[@]}" up -d "$APP_SERVICE"

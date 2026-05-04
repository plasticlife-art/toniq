#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_ENV="${DEPLOY_ENV:-prod}"
APP_IMAGE_REPOSITORY="${APP_IMAGE_REPOSITORY:-ghcr.io/plasticlife-art/toniq}"
APP_IMAGE_TAG="${APP_IMAGE_TAG:-latest}"
APP_IMAGE="${APP_IMAGE_REPOSITORY}:${APP_IMAGE_TAG}"
APP_GIT_REF="${APP_GIT_REF:-unknown}"

log() { echo "[$(date '+%F %T')] $*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      DEPLOY_ENV="${2:-}"
      shift 2
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

cd "$APP_DIR"
# shellcheck disable=SC1091
source "$APP_DIR/scripts/lib/deploy_env.sh"
setup_stack_env

: "${POSTGRES_USER:?POSTGRES_USER is required in $ENV_FILE}"
: "${POSTGRES_DB:?POSTGRES_DB is required in $ENV_FILE}"

TS="$(date +%F_%H-%M-%S)"
BACKUP_DIR="$BACKUP_ROOT_DIR/$TS"
mkdir -p "$BACKUP_DIR"

APP_CID="$(compose_app_container_id)"
OLD_IMAGE_ID="$(docker inspect "$APP_CID" --format '{{.Image}}' 2>/dev/null || true)"
{
  echo "DEPLOY_ENV=$DEPLOY_ENV"
  echo "ROLLOUT_AT=$TS"
  echo "APP_GIT_REF=$APP_GIT_REF"
  echo "OLD_IMAGE_ID=$OLD_IMAGE_ID"
  echo "APP_IMAGE=$APP_IMAGE"
  echo "POSTGRES_DB=$POSTGRES_DB"
} > "$BACKUP_DIR/release_meta.env"

log "Pulling image $APP_IMAGE"
"${COMPOSE[@]}" pull "$APP_SERVICE" || true

log "Starting database"
"${COMPOSE[@]}" up -d "$DB_SERVICE"
wait_for_service_healthy "$DB_SERVICE"

log "Creating database backup"
"${COMPOSE[@]}" exec -T "$DB_SERVICE" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$BACKUP_DIR/${POSTGRES_DB}.dump" || true

log "Restarting application"
"${COMPOSE[@]}" up -d "$APP_SERVICE"

APP_CID="$(compose_app_container_id)"
container_is_running "$APP_CID" || fail "App service '$APP_SERVICE' is not running after rollout"

log "Done"
echo "Backups: $BACKUP_DIR"
echo "Metadata: $BACKUP_DIR/release_meta.env"

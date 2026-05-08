#!/usr/bin/env bash

if [[ -z "${APP_DIR:-}" ]]; then
  echo "APP_DIR must be set before sourcing deploy_env.sh" >&2
  return 1
fi

resolve_stack_path() {
  local raw_path="$1"
  case "$raw_path" in
    /*) printf '%s\n' "$raw_path" ;;
    ./*) printf '%s\n' "$APP_DIR/${raw_path#./}" ;;
    *) printf '%s\n' "$APP_DIR/$raw_path" ;;
  esac
}

deploy_env_fail() {
  local message="$1"
  if declare -F fail >/dev/null 2>&1; then
    fail "$message"
  fi
  echo "ERROR: $message" >&2
  return 1
}

validate_deploy_env() {
  case "$1" in
    prod|test) ;;
    *) deploy_env_fail "Unsupported --env value: $1 (expected: prod or test)"; return 1 ;;
  esac
}

setup_stack_env() {
  local default_env_file default_project default_app_port default_frontend_port
  DEPLOY_ENV="${DEPLOY_ENV:-prod}"
  validate_deploy_env "$DEPLOY_ENV"

  case "$DEPLOY_ENV" in
    prod)
      default_env_file=".env.prod"
      default_project="toniq-prod"
      default_app_port="8080"
      default_frontend_port="8081"
      ;;
    test)
      default_env_file=".env.test"
      default_project="toniq-test"
      default_app_port="18080"
      default_frontend_port="18081"
      ;;
  esac

  ENV_FILE="${ENV_FILE:-$default_env_file}"
  ENV_FILE="$(resolve_stack_path "$ENV_FILE")"
  [[ -f "$ENV_FILE" ]] || {
    deploy_env_fail "Env file not found: $ENV_FILE"
    return 1
  }

  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$default_project}"
  APP_SERVICE="${APP_SERVICE:-app}"
  FRONTEND_SERVICE="${FRONTEND_SERVICE:-frontend}"
  DB_SERVICE="${DB_SERVICE:-db}"
  APP_IMAGE_REPOSITORY="${APP_IMAGE_REPOSITORY:-ghcr.io/plasticlife-art/toniq}"
  APP_IMAGE_TAG="${APP_IMAGE_TAG:-latest}"
  FRONTEND_IMAGE_REPOSITORY="${FRONTEND_IMAGE_REPOSITORY:-ghcr.io/plasticlife-art/toniq-frontend}"
  FRONTEND_IMAGE_TAG="${FRONTEND_IMAGE_TAG:-$APP_IMAGE_TAG}"
  APP_PORT="${APP_PORT:-$default_app_port}"
  FRONTEND_PORT="${FRONTEND_PORT:-$default_frontend_port}"
  BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-${HEALTH_URL:-http://127.0.0.1:${APP_PORT}/login}}"
  FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1:${FRONTEND_PORT}/}"
  LOCK_FILE="${LOCK_FILE:-$APP_DIR/.deploy-${DEPLOY_ENV}.lock}"
  BACKUP_ROOT_DIR="${BACKUP_ROOT_DIR:-$APP_DIR/backups/${DEPLOY_ENV}}"

  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose --env-file "$ENV_FILE" -p "$COMPOSE_PROJECT_NAME" -f "$APP_DIR/docker-compose.yml")
  elif docker-compose version >/dev/null 2>&1; then
    COMPOSE=(docker-compose --env-file "$ENV_FILE" -p "$COMPOSE_PROJECT_NAME" -f "$APP_DIR/docker-compose.yml")
  else
    deploy_env_fail "docker compose / docker-compose not found"
    return 1
  fi
}

compose_app_container_id() {
  "${COMPOSE[@]}" ps -q "$APP_SERVICE" | head -n1
}

compose_frontend_container_id() {
  "${COMPOSE[@]}" ps -q "$FRONTEND_SERVICE" | head -n1
}

compose_service_container_id() {
  local service_name="$1"
  "${COMPOSE[@]}" ps -q "$service_name" | head -n1
}

wait_for_service_healthy() {
  local service_name="$1"
  local cid health
  cid="$(compose_service_container_id "$service_name")"
  [[ -n "$cid" ]] || deploy_env_fail "Cannot resolve container id for service '$service_name'" || return 1

  for _ in {1..60}; do
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || echo missing)"
    if [[ "$health" == "healthy" || "$health" == "none" ]]; then
      return 0
    fi
    sleep 2
  done

  deploy_env_fail "Service '$service_name' did not become healthy"
}

container_is_running() {
  local container_id="$1"
  [[ -n "$container_id" ]] || return 1
  [[ "$(docker inspect -f '{{.State.Running}}' "$container_id" 2>/dev/null || true)" == "true" ]]
}

#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_ENV="${DEPLOY_ENV:-prod}"
COMMAND=""

usage() {
  cat <<'EOF'
Usage:
  scripts/runtime_control.sh <stop|start|restart|reload-env> [--env <prod|test>]
EOF
}

fail() { echo "ERROR: $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    stop|start|restart|reload-env)
      COMMAND="$1"
      shift
      ;;
    --env)
      DEPLOY_ENV="${2:-}"
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

[[ -n "$COMMAND" ]] || fail "Runtime command is required"

cd "$APP_DIR"
# shellcheck disable=SC1091
source "$APP_DIR/scripts/lib/deploy_env.sh"
setup_stack_env

case "$COMMAND" in
  stop)
    "${COMPOSE[@]}" stop "$APP_SERVICE"
    ;;
  start)
    "${COMPOSE[@]}" start "$APP_SERVICE"
    ;;
  restart)
    "${COMPOSE[@]}" restart "$APP_SERVICE"
    ;;
  reload-env)
    "${COMPOSE[@]}" up -d --no-deps --force-recreate "$APP_SERVICE"
    ;;
esac

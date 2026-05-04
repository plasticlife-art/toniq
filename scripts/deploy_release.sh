#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_ENV="${DEPLOY_ENV:-prod}"
APP_IMAGE_REPOSITORY="${APP_IMAGE_REPOSITORY:-ghcr.io/plasticlife-art/toniq}"
HEALTH_RETRIES="${HEALTH_RETRIES:-60}"
HEALTH_SLEEP_SEC="${HEALTH_SLEEP_SEC:-2}"
ASSUME_YES=0
DEPLOY_TAG=""
DEPLOY_GIT_REF=""
DEPLOY_IMAGE_TAG=""
TARGET_GIT_REF="main"
APP_IMAGE_TAG="latest"

usage() {
  cat <<'EOF'
Usage:
  scripts/deploy_release.sh [options]

Options:
  --env <prod|test>   Target stack. Default: prod.
  --yes               Non-interactive mode.
  --tag <vX.Y.Z>      Deploy exact release tag.
  --git-ref <ref>     Deploy exact git ref/commit.
  --image-tag <tag>   Deploy exact image tag.
  -h, --help          Show this help.
EOF
}

log() { echo "[$(date '+%F %T')] $*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

validate_release_tag() {
  [[ "$1" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Release tag must match vX.Y.Z, got: $1"
}

ensure_clean_git_worktree() {
  if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    fail "Git worktree has tracked modifications. Commit/stash them before deploy."
  fi
}

prompt_continue() {
  if [[ "$ASSUME_YES" == "1" ]]; then
    return 0
  fi
  read -r -p "Continue deploy for '$DEPLOY_ENV'? [y/N] " ans
  [[ "${ans:-}" =~ ^[Yy]$ ]] || fail "Cancelled by user"
}

update_repo_for_deploy() {
  git fetch --prune --tags origin

  if [[ -n "$DEPLOY_TAG" ]]; then
    validate_release_tag "$DEPLOY_TAG"
    git checkout -q "$DEPLOY_TAG"
    TARGET_GIT_REF="$DEPLOY_TAG"
    APP_IMAGE_TAG="${DEPLOY_TAG#v}"
    return 0
  fi

  if [[ -n "$DEPLOY_GIT_REF" ]]; then
    git checkout -q "$DEPLOY_GIT_REF"
    TARGET_GIT_REF="$(git rev-parse HEAD)"
    APP_IMAGE_TAG="${DEPLOY_IMAGE_TAG:-sha-$TARGET_GIT_REF}"
    return 0
  fi

  git checkout -q main
  git pull --ff-only origin main
  TARGET_GIT_REF="main"
  APP_IMAGE_TAG="${DEPLOY_IMAGE_TAG:-latest}"
}

post_rollout_check() {
  local attempt response body code
  for attempt in $(seq 1 "$HEALTH_RETRIES"); do
    response="$(curl -sS -m 10 -w $'\n%{http_code}' "$HEALTH_URL" 2>&1 || true)"
    body="${response%$'\n'*}"
    code="${response##*$'\n'}"
    if [[ "$code" == "200" ]]; then
      log "Health check passed: $HEALTH_URL"
      return 0
    fi
    sleep "$HEALTH_SLEEP_SEC"
  done

  echo "$body" >&2
  fail "Health check failed: $HEALTH_URL"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      DEPLOY_ENV="${2:-}"
      shift 2
      ;;
    --yes)
      ASSUME_YES=1
      shift
      ;;
    --tag)
      DEPLOY_TAG="${2:-}"
      shift 2
      ;;
    --git-ref)
      DEPLOY_GIT_REF="${2:-}"
      shift 2
      ;;
    --image-tag)
      DEPLOY_IMAGE_TAG="${2:-}"
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

cd "$APP_DIR"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "Not a git repository: $APP_DIR"
ensure_clean_git_worktree

# shellcheck disable=SC1091
source "$APP_DIR/scripts/lib/deploy_env.sh"
setup_stack_env

prompt_continue
update_repo_for_deploy

log "Deploy plan: env=$DEPLOY_ENV git_ref=$TARGET_GIT_REF image_tag=$APP_IMAGE_TAG"
APP_GIT_REF="$TARGET_GIT_REF" APP_IMAGE_TAG="$APP_IMAGE_TAG" APP_IMAGE_REPOSITORY="$APP_IMAGE_REPOSITORY" \
  "$APP_DIR/scripts/rollout_release.sh" --env "$DEPLOY_ENV"

post_rollout_check

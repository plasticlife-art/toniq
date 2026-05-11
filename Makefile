SHELL := /bin/bash

DEPLOY_FLAGS ?=
tag ?=
sha ?=
env ?= prod
side ?=
ENV_FILE ?= .env

.PHONY: clean-cache run dev dev-frontend dev-frontend-stop dev-all docker deploy deploy-prod deploy-test stop start restart reload-env

clean-cache:
	@echo "Cleaning build and caches..."
	rm -rf build .gradle .jmix/conf

run:
	@if [[ " $(MAKECMDGOALS) " == *" docker "* ]]; then \
	  docker compose up --build; \
	else \
	  ./gradlew bootRun; \
	fi

dev:
	@set -euo pipefail; \
	test -f "$(ENV_FILE)"; \
	set -a; \
	source "$(ENV_FILE)"; \
	set +a; \
	docker compose --env-file "$(ENV_FILE)" up -d db; \
	DB_NAME="$${POSTGRES_DB:-toniq}"; \
	DB_USER="$${POSTGRES_USER:-toniq}"; \
	DB_PORT="$$(docker compose --env-file "$(ENV_FILE)" port db 5432 | sed -E 's/.*:([0-9]+)$$/\1/')"; \
	for _ in {1..60}; do \
	  if docker compose --env-file "$(ENV_FILE)" ps --format json db | grep -q '"Health":"healthy"'; then \
	    break; \
	  fi; \
	  sleep 1; \
	done; \
	if ! docker compose --env-file "$(ENV_FILE)" exec -T db psql -U "$${DB_USER}" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$${DB_NAME}'" | grep -q 1; then \
	  docker compose --env-file "$(ENV_FILE)" exec -T db createdb -U "$${DB_USER}" "$${DB_NAME}"; \
	fi; \
	MAIN_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$${DB_PORT}/$${DB_NAME}" \
	MAIN_DATASOURCE_USERNAME="$${DB_USER}" \
	MAIN_DATASOURCE_PASSWORD="$${POSTGRES_PASSWORD:-toniq}" \
	./gradlew bootRun

dev-frontend:
	@docker compose --env-file "$(ENV_FILE)" -f docker-compose.dev.yml up -d frontend-dev

dev-frontend-stop:
	@docker compose --env-file "$(ENV_FILE)" -f docker-compose.dev.yml stop frontend-dev

dev-all:
	@$(MAKE) dev-frontend
	@$(MAKE) dev

docker:
	@:

deploy:
	@set -euo pipefail; \
	test -x scripts/deploy_release.sh || chmod +x scripts/deploy_release.sh; \
	if [[ -n "$(tag)" && -n "$(sha)" ]]; then \
	  echo "Use either tag=<X.Y.Z> or sha=<full-commit-sha>, not both"; \
	  exit 1; \
	elif [[ -n "$(tag)" ]]; then \
	  if [[ -z "$(side)" ]]; then \
	    echo "Use side=backend or side=frontend with tag=<X.Y.Z>"; \
	    exit 1; \
	  elif [[ "$(side)" != "backend" && "$(side)" != "frontend" ]]; then \
	    echo "Unsupported side: $(side). Use side=backend or side=frontend"; \
	    exit 1; \
	  fi; \
	  ./scripts/deploy_release.sh --env "$(env)" --tag "$(side)-v$(tag)" $(DEPLOY_FLAGS); \
	elif [[ -n "$(side)" ]]; then \
	  echo "side=... is only supported together with tag=<X.Y.Z>"; \
	  exit 1; \
	else \
	  if [[ -n "$(sha)" ]]; then \
	    ./scripts/deploy_release.sh --env "$(env)" --git-ref "$(sha)" --image-tag "sha-$(sha)" $(DEPLOY_FLAGS); \
	  else \
	    ./scripts/deploy_release.sh --env "$(env)" $(DEPLOY_FLAGS); \
	  fi; \
	fi

deploy-prod:
	@$(MAKE) deploy env=prod DEPLOY_FLAGS='$(DEPLOY_FLAGS)'

deploy-test:
	@$(MAKE) deploy env=test DEPLOY_FLAGS='$(DEPLOY_FLAGS)'

stop:
	@set -euo pipefail; \
	test -x scripts/runtime_control.sh || chmod +x scripts/runtime_control.sh; \
	./scripts/runtime_control.sh stop --env "$(env)"

start:
	@set -euo pipefail; \
	test -x scripts/runtime_control.sh || chmod +x scripts/runtime_control.sh; \
	./scripts/runtime_control.sh start --env "$(env)"

restart:
	@set -euo pipefail; \
	test -x scripts/runtime_control.sh || chmod +x scripts/runtime_control.sh; \
	./scripts/runtime_control.sh restart --env "$(env)"

reload-env:
	@set -euo pipefail; \
	test -x scripts/runtime_control.sh || chmod +x scripts/runtime_control.sh; \
	./scripts/runtime_control.sh reload-env --env "$(env)"

SHELL := /bin/bash

DEPLOY_FLAGS ?=
tag ?=
sha ?=
env ?= prod
side ?=

.PHONY: clean-cache run docker deploy deploy-prod deploy-test stop start restart reload-env

clean-cache:
	@echo "Cleaning build and caches..."
	rm -rf build .gradle .jmix/conf

run:
	@if [[ " $(MAKECMDGOALS) " == *" docker "* ]]; then \
	  docker compose up --build; \
	else \
	  ./gradlew bootRun; \
	fi

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

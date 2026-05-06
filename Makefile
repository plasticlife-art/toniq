SHELL := /bin/bash

DEPLOY_FLAGS ?=
tag ?=
sha ?=
env ?= prod

.PHONY: clean-cache run docker deploy stop start restart reload-env

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
	  echo "Use either tag=<vX.Y.Z> or sha=<full-commit-sha>, not both"; \
	  exit 1; \
	elif [[ -n "$(tag)" ]]; then \
	  ./scripts/deploy_release.sh --env "$(env)" --tag "$(tag)" $(DEPLOY_FLAGS); \
	elif [[ -n "$(sha)" ]]; then \
	  ./scripts/deploy_release.sh --env "$(env)" --git-ref "$(sha)" --image-tag "sha-$(sha)" $(DEPLOY_FLAGS); \
	else \
	  ./scripts/deploy_release.sh --env "$(env)" $(DEPLOY_FLAGS); \
	fi

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

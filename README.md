# Toniq

Toniq is a web application for event operations and public event presentation.

## Getting Started

Requirements:
* Java 17 or 21
* Docker, if you want to run the containerized stack

## Development

For local development, run the backend directly:

```bash
./gradlew bootRun
```

## Running

The backend application will be available at <http://localhost:8080>.

Default admin credentials:
* Login: `admin`
* Password: `admin`

## Docker Compose

To run the application with PostgreSQL in Docker:

```bash
docker compose up --build
```

By default, the stack starts:
* Caddy edge proxy on <http://localhost>
* PostgreSQL on `127.0.0.1:5432`
* Backend/admin application on <http://127.0.0.1:8080>
* Public frontend on <http://127.0.0.1:8081>

By default, Caddy routes:
* `localhost` to the public frontend
* `admin.localhost` to the backend/admin UI

You can override ports, domains, and database credentials with variables such as `CADDY_HTTP_PORT`, `CADDY_HTTPS_PORT`, `PUBLIC_DOMAIN`, `ADMIN_DOMAIN`, `APP_PORT`, `FRONTEND_PORT`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`.

The public event-detail app is built and deployed separately from the backend:
* backend image: Spring/Jmix admin UI + public API
* frontend image: static public app served by Nginx

In the default Docker setup, Caddy terminates external traffic and routes by host:
* `PUBLIC_DOMAIN` -> frontend container
* `ADMIN_DOMAIN` -> backend container

Inside the stack, the frontend still proxies `/api/*` to the backend over Docker network/DNS, so the public site stays on one origin and no CORS configuration is required.

## Deploy

The repository includes the basic operational files for containerized deployment and runtime control:
* `Makefile`
* `scripts/deploy_release.sh`
* `scripts/rollout_release.sh`
* `scripts/rollback_release.sh`
* `scripts/runtime_control.sh`

Prepare environment files from the templates:

```bash
cp .env.prod.example .env.prod
cp .env.test.example .env.test
```

For VPS deployment, set at least:
* `PUBLIC_DOMAIN`
* `ADMIN_DOMAIN`
* `POSTGRES_PASSWORD`
* `MEGATIX_*`

Point both domains to the VPS IP. Caddy will obtain and renew TLS certificates automatically.

Useful commands:

```bash
make deploy-prod
make deploy-test
make deploy env=prod sha=<commit>
make deploy side=backend env=prod tag=1.2.3
make deploy side=frontend env=prod tag=1.2.3
make restart env=prod
make reload-env env=test
```

Deploy semantics:
* `make deploy-prod` and `make deploy-test` deploy the default image tags from `.env.prod` / `.env.test`
* `make deploy env=prod sha=<commit>` deploys both images from `sha-<commit>`
* `make deploy side=backend env=prod tag=1.2.3` resolves to release tag `backend-v1.2.3`
* `make deploy side=frontend env=prod tag=1.2.3` resolves to release tag `frontend-v1.2.3`

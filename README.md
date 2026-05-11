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

To use PostgreSQL from Docker without rebuilding the application image on every change:

```bash
make dev
```

This starts only the `db` container and then runs the backend locally with
`MAIN_DATASOURCE_*` pointed at that PostgreSQL instance. By default it reads
variables from `.env`. You can override that, for example:

```bash
make dev ENV_FILE=.env.test
```

To run the public frontend in fast dev mode without rebuilding its image:

```bash
make dev-frontend
```

This starts an `nginx` container on <http://127.0.0.1:8081> with `frontend/src`
mounted directly from the working tree and `/api/*` proxied to the local backend
on `127.0.0.1:8080`. It also uses `.env` by default and supports
`ENV_FILE=...` the same way as `make dev`.

To run both together:

```bash
make dev-all
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
* PostgreSQL on `127.0.0.1:5432`
* Backend/admin application on <http://127.0.0.1:8080>
* Public frontend on <http://127.0.0.1:8081>

You can override local ports and database credentials with variables such as `APP_PORT`, `FRONTEND_PORT`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`.

The public event-detail app is built and deployed separately from the backend:
* backend image: Spring/Jmix admin UI + public API
* frontend image: static public app served by Nginx

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

Point both domains to the VPS IP and configure a system-level reverse proxy such as Caddy to route:
* `PUBLIC_DOMAIN` -> `127.0.0.1:<FRONTEND_PORT>`
* `ADMIN_DOMAIN` -> `127.0.0.1:<APP_PORT>`

The project no longer includes Caddy inside `docker-compose.yml`; on VPS, `prod` and `test` are expected to share one system-level proxy.

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

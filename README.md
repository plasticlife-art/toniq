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
* PostgreSQL on `localhost:5432`
* Backend/admin application on <http://localhost:8080>
* Public frontend on <http://localhost:8081>

You can override ports and database credentials with variables such as `APP_PORT`, `FRONTEND_PORT`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`.

The public event-detail app is built and deployed separately from the backend:
* backend image: Spring/Jmix admin UI + public API
* frontend image: static public app served by Nginx

In the default Docker setup, the frontend proxies `/api/*` to the backend over Docker network/DNS, so the browser stays on one origin and no CORS configuration is required.

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

Useful commands:

```bash
make deploy env=test
make deploy env=prod tag=v1.2.3
make restart env=prod
make reload-env env=test
```

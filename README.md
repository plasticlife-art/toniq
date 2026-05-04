# Toniq

This is a web application based on the [Jmix](https://www.jmix.io) framework.

## Getting Started

The newly created project requires Java 17 or 21 and uses the embedded HSQL database.

Use the following resources to learn more about Jmix:
* [Jmix Documentation](https://docs.jmix.io)
* [Online Demo Applications](https://www.jmix.io/live-demo)
* [Jmix AI Assistant](https://ai-assistant.jmix.io) (also available in the **Jmix AI** tool window of IntelliJ IDEA)

## Development

- [Setup](https://docs.jmix.io/jmix/setup.html) your development environment.
- [Open](https://docs.jmix.io/jmix/studio/project.html#opening-existing-project) the project in the IDE.
- If you want to use AI agents to develop the application, check out the [Jmix AI Agent Guidelines](https://github.com/jmix-framework/jmix-agent-guidelines) repository.

## Running

To start the application, use the **Toniq Jmix Application** run configuration in your IDE, or run the following command in the project root directory:

```bash
./gradlew bootRun
```

The application will be available at <http://localhost:8080>.

The default user credentials are:
* Login: `admin`
* Password: `admin`

## Docker Compose

To run the application with PostgreSQL in Docker:

```bash
docker compose up --build
```

By default, the stack starts:
* PostgreSQL on `localhost:5432`
* Application on <http://localhost:8080>

You can override ports and database credentials with environment variables such as `APP_PORT`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`.

## Deploy

The repository now includes the same basic operational layout as `bots/lottery`:
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

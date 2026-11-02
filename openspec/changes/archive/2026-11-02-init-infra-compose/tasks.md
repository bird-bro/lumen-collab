# Tasks

## 1. Compose stack

- [x] 1.1 Create `docker/docker-compose.yml` (postgres:16-alpine + nacos server standalone + redis:7-alpine), healthchecks, named volumes, `restart: unless-stopped`, ports/credentials via `.env`. Verify: `docker compose config -q` exits 0.
- [x] 1.2 Create `docker/.env.example` (PG_PORT=15432, PG_PASSWORD placeholder, NACOS_PORT=8848, REDIS_PORT=6379) + gitignore `docker/.env`. Verify: `git check-ignore docker/.env` matches; `.env.example` committed.

## 2. PG initialization

- [x] 2.1 Create `docker/init/01-create-dbs.sql`: idempotent six-database creation (lumen_auth/patient/radiology/us/endo/integration) mounted at `/docker-entrypoint-initdb.d/`. Verify: script contains six CREATE DATABASE with existence guards.

## 3. Verification and docs

- [x] 3.1 `docker compose config -q` exit 0 (no daemon required).
- [x] 3.2 OPTIONAL smoke (requires daemon) - **SKIPPED: Docker daemon not running at apply time**; re-run when daemon is up (`docker compose up -d` + psql six-DB check + Nacos/Redis probes) to upgrade the verify credential.
- [x] 3.3 Create `docker/README.md`: container map, port contract, how lumen services will join later (executable-jar note), JAVA/JDBC examples for lumen-auth.

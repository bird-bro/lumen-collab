# LumenCollab

**LumenCollab is an open-source medical imaging examination collaboration platform. It pairs
with the battle-tested open-source PACS
[dcm4chee-arc-light](https://github.com/dcm4che/dcm4chee-arc-light) rather than replacing
it. Studies stay in the PACS; LumenCollab owns the clinical and diagnostic-service workflows
around them - a Radiology Information System (RIS), ultrasound and endoscopy departments.
The project is developed end-to-end with Loop-engineering-driven AI coding. If your hospital
runs dcm4chee and needs a lightweight RIS - LumenCollab is built for that gap.**

> 中文说明：[docs/README.zh-CN.md](docs/README.zh-CN.md)

**Status: 🚧 active development.** The backend *system service* (auth, RBAC, org tree,
users, roles, menus, dictionaries, operation logs) is implemented, verified and documented;
the web console is the work in progress.

## What it does (v1)

- **Exam workflow** — registration → exam → report, as a reusable kernel (`lumen-exam-core`)
  shared by every department service
- **Multi-tenancy ready** — hierarchical org tree (hospital roots + sub-orgs), departments
  anchored to it
- **RBAC** — users, roles, menu tree (directory / menu / button) with `perms` strings for
  dynamic navigation and button-level gating
- **System data** — dictionaries (label↔value) and operation/sign logs, shared across
  services in a `public` schema
- **PACS pairing** — studies linked by Accession Number via dcm4chee-arc-light's QIDO-RS;
  MWL/MPPS + HL7v2 handled by a dedicated integration service

## Repository layout

| Path | What |
|:--|:--|
| `lumen-sev/` | Backend — Maven multi-module aggregator (Java 11 · Spring Boot 2.5.15) |
| ├ `lumen-bom` | Single version source (OPS-verified release train, pinned 1:1) |
| ├ `framework` / `common` | OPS-lineage shared platform layer: security chain (JWT filter, token service), response envelope, audit fill, dict/log machinery, A-class error codes |
| ├ `lumen-system` | **The live service** — auth + RBAC + org/department/user/role/menu + dict/log administration (port 8081) |
| `lumen-web/` | Frontend SPA (React 18 · TypeScript · Vite · Ant Design 5, pnpm) |
| `docker/` | Reserved for lumen app containers + the nginx entry — **never deploys data services** |
| `openspec/` | Spec-driven development: `specs/` (authoritative behavior contracts) + `changes/` (proposals → apply → verify → archive) |
| `docs/` | Deployment handoff (`pg-init.sql`), proposal summaries (`proposals/*.zh.md`), guide (`GUIDE.zh.md`) |

**Data model** — one PostgreSQL database `lumen`, one schema per service
(`system` / `patient` / `radiology` / `us` / `endo` / `integration`), one connection role per
service (GRANT-confined), plus shared system tables in `public`. Redis holds token state.
**Entry** — nginx (80/443) routes `/api/v1/<service>/**`; no Java gateway.
**Configuration** — committed `application.yml` (no credentials) + gitignored
`application-dev.yml` per machine; no registry or config center.

## Quick start

Prerequisites: JDK 11, PostgreSQL 14+, Redis, Node 18+ with pnpm.

```bash
# 1. Database (as a superuser — creates the lumen DB, per-service schemas, role templates)
psql -f docs/deployment/pg-init.sql

# 2. Backend configuration
cp lumen-sev/lumen-system/src/main/resources/application-dev.yml.example \
   lumen-sev/lumen-system/src/main/resources/application-dev.yml
#    → fill in your PostgreSQL/Redis addresses + credentials (gitignored, never commit)

# 3. Build and run the backend (port 8081)
cd lumen-sev
./mvnw install -DskipTests
./mvnw -pl lumen-system org.springframework.boot:spring-boot-maven-plugin:run

# 4. Swagger UI
open http://localhost:8081/swagger-ui/index.html

# 5. Frontend
cd ../lumen-web && pnpm install && pnpm dev
```

Seeded administrator: `admin` / `admin123` — the account carries a
must-change-password flag; change it on first login.

### Tests

```bash
cd lumen-sev
# create .env-test.local (gitignored) with the AUTH_TEST_* variables, or export them:
#   AUTH_TEST_PG_HOST / AUTH_TEST_PG_PORT / AUTH_TEST_PG_USER / AUTH_TEST_PG_PASSWORD
#   AUTH_TEST_DB (optional, default: lumen)
#   AUTH_TEST_REDIS_HOST / AUTH_TEST_REDIS_PORT / AUTH_TEST_REDIS_PASSWORD (optional)
./mvnw test
```

Backend tests run against a real PostgreSQL + Redis (the team test server or a local pair) —
connections come from the gitignored `.env-test.local` (`AUTH_TEST_*` variables). An
unconfigured environment fails the tests explicitly; nothing is silently skipped.

## How this repository is run

Development is **spec-first** (OpenSpec): `openspec/specs/` holds the authoritative
WHEN/THEN behavior contracts; every change goes through proposal → design → tasks → TDD
implementation → three-layer verification → archive, with the main specs synced at each
archive. `CLAUDE.md` / `AGENTS.md` are the entry points for AI coding agents. Chinese
proposal summaries live in `docs/proposals/*.zh.md`; the guide is `docs/GUIDE.zh.md`.

## Roadmap

- [x] `lumen-system` — auth loop (JWT + Redis sessions), RBAC, org tree, departments,
      users, roles + menu assignment, dictionaries, operation/sign logs
- [ ] Web console — OPS-style design system, auth flow, dynamic navigation, system
      administration pages *(in progress)*
- [ ] `lumen-patient` — EMPI (master patient index)
- [ ] Radiology (v1 core) — exam workflow on `lumen-exam-core`, DICOM MWL/MPPS + HL7v2 via
      `lumen-integration`, dcm4chee-arc-light linkage
- [ ] Ultrasound / endoscopy department services

## License

[Apache-2.0](LICENSE) — see also [NOTICE](NOTICE).

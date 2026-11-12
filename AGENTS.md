<!-- auto-loaded: English only. Human notes: docs/GUIDE.zh.md -->
# lumen-collab

> Structured per OpenSpec + Superpowers + Harness (Loop Engineering).
> Launch agents from their subdirectories (Claude Code: `claude`; Codex: `codex`).

## Project Map
```
lumen-collab/
├── CLAUDE.md          ← nav hub (≤120 lines)
├── AGENTS.md          ← Codex mirror of this file
├── openspec/          ← WHAT: specs/, changes/, archive/, coordination/
├── .claude/           ← HOW: rules/, skills/, agents/, settings.json
├── lumen-sev/         ← Backend Agent (Java 11 · Spring Boot 2.5 · Maven aggregator)
├── lumen-web/         ← Frontend Web Agent (React 18 + TS + Vite + AntD 5)
└── docker/            ← docker-compose incl. dcm4chee-arc-light
```

## Business Context
Open-source medical imaging collaboration platform (brand: LumenCollab) that pairs with the open-source PACS **dcm4chee-arc-light**. v1 delivers a RIS (radiology); ultrasound / endoscopy departments follow as independent microservices, all sharing one exam-workflow kernel (`lumen-exam-core`: booking → registration → exam → report).

## Tech Stack
| Layer | Tech |
|:--|:--|
| Backend | Java 11 · Spring Boot 2.5.15 · Spring Cloud 2020.0.3 · Spring Cloud Alibaba 2021.1 · OpenFeign · unified entry nginx, no Java gateway (OPS-verified train - versions copied 1:1 from the OPS reference project; do not "upgrade" without a new proposal) |
| Data | PostgreSQL (per-service DB) · MyBatis-Plus · Flyway · Redis · RocketMQ |
| Integration | dcm4che (MWL / MPPS SCP) · HAPI HL7v2 · dcm4chee QIDO-RS |
| Frontend (web) | React 18 · TypeScript · Vite · Ant Design 5 (pnpm, single SPA) |

## Agents
| Agent | Scope | Role file |
|:--|:--|:--|
| Backend | `lumen-sev/` - all backend Maven modules | `lumen-sev/CLAUDE.md` |
| Frontend (web) | `lumen-web/` | `lumen-web/CLAUDE.md` |

## Conventions
- File names: kebab-case (`user-service.ts`)
- Never modify existing public methods - use overloading
- Auto-loaded files English only (Chinese notes in `docs/GUIDE.zh.md`)

## Development Workflow
### New Feature
1. Write spec: `/opsx:propose <change>` (auto-triggers Superpowers brainstorm)
2. Backend Agent: `cd lumen-sev` → launch AI (claude/codex) → implement to spec (TDD)
3. Frontend Agent (after the backend API is user-verified): `cd lumen-web` → build against the REAL backend (`pnpm dev` proxies `VITE_API_ENTRY_URL`; no mock layer - user ruling 2026-11-05) → `frontend-design` skill → UI prototype (new pages only; static hard-coded demo data allowed, never via `src/api/`; existing-page edits skip) → **user confirms** → `/opsx:apply`
4. Verify against WHEN/THEN; `/opsx:archive` when done (auto-commit after archive: `git add -A && git commit -m "archive: <name>"`, no confirmation needed)

### Small cross-stack changes (bypass)

If ALL of the following are true, skip the full coordination flow:
- No new API contract, no new data model, no schema change
- <=2 files per stack AND <=5 lines of meaningful change per file → **Direct change** (run tests, no proposal)
- <=3 files per stack AND no new API contract → **Single lightweight proposal** (one change in the primary stack; mention the other stack's files in Scope; no coordination doc)

Otherwise (new contract/model, multi-file, or new feature) → use the full coordination flow below.
Cross-domain ban unchanged: each agent still writes only its own stack's code.

### Cross-Stack Feature (>=2 stacks - one coordination doc tracks all per-stack changes)
1. Parent: create `openspec/coordination/<feature>.md` (shared design/decisions + change registry)
2. Per stack (parallel): `openspec new change <name> --goal "<feature>"` (soft-tagged to the feature)
3. Implement each in its own stack (cross-domain ban unchanged; backend first - the frontend builds against the real API, no mock layer); coordinate via the coordination doc
4. Gate: verify each stack independently; defer archive until ALL registered changes have verify.md overall = PASS, then batch-archive them
   Single-stack: skip coordination; plain `openspec new change`. Open all stacks: `openspec workset open lumen-collab --tool code`.

### AI Coding Rules
- Spec first - read `openspec/specs/` before writing code
- UI prototype first (new pages only) - validate with text/DOM checks and confirm visuals with the user; no automatic screenshots (screenshots only on explicit user request and if the model supports images); existing-page edits skip the prototype
- TDD - Superpowers auto-enforces
- No cross-domain - each agent writes only its own stack
- Never modify existing methods - use overloading
- Dangerous ops (`rm -rf`, `git push --force`, `git reset --hard`) gated by sandbox/approval or .claude/settings.json deny list
- Chinese proposal intro - do NOT auto-generate. Write `docs/proposals/<name>.zh.md` only on explicit user request for a stakeholder summary: <=20 lines covering problem, approved approach, impact, deployment order, and authoritative links; never duplicate implementation details. `openspec/` English stays source of truth.

### Token Discipline
- Read only the current change, affected stack guidance, and relevant spec sections; use targeted search, not whole-repo scans.
- Redirect build/test output to files; report exit code + counts + log path. Quote only key failure lines, never full logs.
- During apply, run affected tests; run the configured suite during verify.
- Ask clarifications in one batch (max 3); do not ask serial one-off questions.
- On resume, read loop-state/progress/tasks first; do not re-read archives or unrelated changes.

### Session Commands
`/resume` · `/branch` · `/rewind`

## Build & Test
```
cd lumen-sev && ./mvnw compile -q | cd lumen-sev && ./mvnw test -q          # backend (JDK 11; reactor builds - build from lumen-sev/, not -f from root)
cd lumen-web && pnpm dev | cd lumen-web && pnpm build                        # lumen-web (details: lumen-web/CLAUDE.md)
docker compose up -d                                                     # docker/ - full stack incl. dcm4chee-arc-light
```

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
├── lumen-sev/         ← Backend Agent (Java 17 · Spring Boot 3 · Maven aggregator)
├── lumen-web/         ← Frontend Web Agent (React 18 + TS + Vite + AntD 5)
└── docker/            ← docker-compose incl. dcm4chee-arc-light
```

## Business Context
Open-source medical imaging collaboration platform (brand: LumenCollab) that pairs with the open-source PACS **dcm4chee-arc-light**. v1 delivers a RIS (radiology); ultrasound / endoscopy departments follow as independent microservices, all sharing one exam-workflow kernel (`lumen-exam-core`: booking → registration → exam → report).

## Tech Stack
| Layer | Tech |
|:--|:--|
| Backend | Java 17 · Spring Boot 3.x · Spring Cloud Alibaba (Nacos, Sentinel) · Spring Cloud Gateway · OpenFeign |
| Data | PostgreSQL (per-service DB) · MyBatis-Plus · Flyway · Redis · RabbitMQ |
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
3. Frontend Agent (parallel): `cd lumen-web` → mock from spec → `frontend-design` skill → UI prototype (new pages only; existing-page edits skip) → **user confirms** → `/opsx:apply`
4. Verify against WHEN/THEN; `/opsx:archive` when done

### Cross-Stack Feature (>=2 stacks - one coordination doc tracks all per-stack changes)
1. Parent: create `openspec/coordination/<feature>.md` (shared design/decisions + change registry)
2. Per stack (parallel): `openspec new change <name> --goal "<feature>"` (soft-tagged to the feature)
3. Implement each in its own stack (cross-domain ban unchanged; frontend mocks first); coordinate via the coordination doc
4. Gate: feature done = ALL registered changes verify PASS (`openspec/verify.config.yaml`) -> archive each
   Single-stack: skip coordination; plain `openspec new change`. Open all stacks: `openspec workset open lumen-collab --tool code`.

### AI Coding Rules
- Spec first - read `openspec/specs/` before writing code
- UI prototype first (new pages only) - `frontend-design` skill before `/opsx:apply`; existing-page edits skip the prototype
- TDD - Superpowers auto-enforces
- No cross-domain - each agent writes only its own stack
- Never modify existing methods - use overloading
- Dangerous ops (`rm -rf`, `git push --force`, `git reset --hard`) gated by sandbox/approval or .claude/settings.json deny list
- Chinese proposal intro - after `/opsx:propose` artifacts, write `docs/proposals/<name>.zh.md` (Chinese overview incl. proposal explanation, file paths+roles, next-step action suggestions, flow plan; non-authoritative disclaimer header; `openspec/` English stays source of truth). `<name>` = change name, or feature name for cross-stack.

### Session Commands
`/resume` · `/branch` · `/rewind`

## Build & Test
```
mvn -f lumen-sev/pom.xml compile -q | mvn -f lumen-sev/pom.xml test -q   # backend
pnpm dev | pnpm build                                                    # lumen-web (details: lumen-web/CLAUDE.md)
docker compose up -d                                                     # docker/ - full stack incl. dcm4chee-arc-light
```

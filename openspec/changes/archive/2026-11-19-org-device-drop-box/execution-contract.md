# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: org-device-drop-box
- **Problem to solve**:
The device form no longer edits the Box (采集盒) parameters, and the backend's D9
NULL-aware full-replace update writes `NULL` into all five box columns on every
save — the receive→overwrite pipeline for data nobody sends is dead code that
misleads readers. Clear the fields end-to-end (entity, DTO, service, DB schema,
API contract, web type mirror) so the device model matches what the product
actually uses. User ruling 2026-11-19 (device drawer rework session).

<!-- AI: summarized - see proposal Why for the full statement -->
- **In scope**: removal of the five box fields from the device body contract
  (MODIFIED `api` delta), `OrgDevice` entity, both device DTOs,
  `OrgDeviceServiceImpl.update()`, new Flyway `V48` column drop, backend/web
  test fixtures, `lumen-web/src/api/types.ts` mirror.
- **Out of scope**: `ae/ip/port` and the capture/prune family (they stay); any
  US-workstation capture design (v2); further web drawer rework (already a
  direct UI change); compatibility shims for old clients (none exist).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/api/spec.md`
5:### Requirement: Device Create Validation
18:#### Scenario: Create device
28:#### Scenario: Device create rejects foreign or duplicate identifiers

Confirmed: both scenarios are the delta's MODIFIED requirement (full-superset
copy of the main spec per the layering-archive rule) — behavior narrows only by
dropping the box params from the optional acquisition block. The A0400 duplicate
guard scenario is unchanged (regression guard).

## Design Constraints

- **Architecture/Decisions**:
- **D1 — Forward-only column drop (V48), never rewrite V27.** New migration
  `V48__org_device_drop_box.sql` in `lumen-system` executes five
  `ALTER TABLE system.org_device DROP COLUMN IF EXISTS ...` statements.
  Alternative (keeping nullable columns, dead in contract) rejected: it leaves
  the misleading receive→null pipeline in place, which is the problem the user
  is clearing. `IF EXISTS` keeps the migration idempotent-friendly for the
  delete-history-row-and-rerun recovery path (memory ruling: idempotent data
  migrations re-run cleanly).
- **D2 — Full removal, not deprecation.** The fields leave entity, both DTOs,
  the update `.set(...)` chain, and the contract in one change. No
  compatibility shims (HERO scope: no wrappers for materially cheaper work).
  Old clients sending box fields: none exist in production (single SPA).
- **Technical constraints**:
  - JDK 11 + OPS-verified version train; reactor builds from `lumen-sev/` only.
  - Flyway: schema changes ONLY via migrations; V27 is applied history — never
    rewritten; V48 must not collide with the ledger (in-flight
    rep-temp-normal-range-sev claims V47 — this change yields to it).
  - Cross-domain ban: backend files by the backend agent, web files by the web
    agent; single SPA means no old clients to support (D2).
  - Shared test DB (pgsql-test-lumen): verify window must be exclusive
    (memory: parallel-suite fixture hazards); test sweeps hard-delete.
  - Web edits are type-mirror only — `pnpm tsc --noEmit` + the two page suites
    are the gates.

## Task Batches

<!-- AI: reviewed - batch goals/inputs/outputs -->
**Batch 1 (tasks 1.1–1.3) — backend contract fields out.** Output: entity/DTOs/
service carry no box fields; fixture-shrink-first (1.1) proves zero
dependencies BEFORE the removal lands. Verify: 1.2 grep gate + 1.3 suite run.
5:- [ ] 1.1 TDD red: extend `AuthDeviceApiTests` so the create/update fixture bodies
10:- [ ] 1.2 Remove `boxAdapter/boxIp/boxPort/boxMac/boxPrint` from
16:- [ ] 1.3 Run `./mvnw -pl lumen-system -am test -q` (JDK 11) — full lumen-system
**Batch 2 (task 2.1) — schema migration.** Output: V48 drops the five columns;
suite boots Flyway and migrates cleanly. Verify: ledger ordinal scan + suite run.
22:- [ ] 2.1 Add `lumen-system/src/main/resources/db/migration/V48__org_device_drop_box.sql`
**Batch 3 (task 3.1) — web type mirror.** Output: types.ts + two fixtures lose
the box fields, no behavior change. Verify: tsc + device/room suites green.
31:- [ ] 3.1 Remove the five box fields from `OrgDevice` and `DeviceCreateBody` in
**Batch 4 (task 4.1) — integration.** Output: full reactor green + validate
PASS. Verify: `./mvnw test -q` exit 0; `openspec validate` PASS.
40:- [ ] 4.1 Full reactor check from `lumen-sev/`:

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: none new — this change REMOVES
  surface; the 1.1 fixture-shrink-first order is the red-analog (suite must
  compile+pass with box-free fixtures before the fields leave the code,
  proving zero dependencies).
- **Required edge cases**: create/update succeed with box-free bodies (delta
  Scenario: Create device); a request that still carries box JSON keys is
  ignored by Jackson against the narrowed DTOs (rolling-upgrade tolerance);
  duplicate/foreign `did`/`code` guard unchanged (delta Scenario: rejects
  foreign or duplicate identifiers).
- **Regression-sensitive areas**: device create/update round-trip (D9
  full-replace .set chain after removing five lines); Flyway migrate on the
  shared test DB (exclusive window); room picker feed (shares the device
  table); AuthDeviceApiTests + AuthDeviceRoomSeedTests fixtures.

## Execution Mode

- **Mode**: `SDD`
- **Selection rationale**: 6 tracked tasks spanning two stacks
  (lumen-system + lumen-web) plus a shared-test-DB schema migration — not
  "same module", and the migration carries risk indicators (memory:
  shared-test-server verification hazards). Matches the rep-temp-scope-perms
  precedent.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: after Batch 1+2 (backend removal + migration —
  reviewer confirms no box references remain and V48 follows the
  never-rewrite/idempotency rules); after Batch 3 (web mirror — reviewer
  confirms zero behavior diff beyond type narrowing).
- **Blocking categories**: correctness; schema-migration safety; contract
  drift (any reintroduction of box fields).

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-registration-redesign-api
  date: 2026-11-23
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "27/27"
      L3: PASS
---

# Verification: us-registration-redesign-api

Overall: **PASS**

Single-stack change (backend agent: lumen-us + lumen-system + one additive lumen-core
jsonb key; no web surface — proposal ③).

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 27/27 | PASS | logs/build-backend.log |

- **L1**: `./mvnw compile -q` from `lumen-sev/` (JDK 11) — exit 0.
- **L3**: full reactor `./mvnw test -Dsurefire.rerunFailingTestsCount=2 -q` — every suite
  green except the two incidents below, each isolated-rerun green with the run evidence
  in /tmp (the ① credential shape: isolated-green + issues recorded). The five change
  suites all green: UsSchemaMigrationTests 7/7, UsRegistrationApiTests 24/24,
  UsSlotConfigApiTests 5/5, UsSnapshotPollerTests (repinned) green,
  AuthSlotConfigMenuSeedTests 3/3 + AuthSearchFilterTests 2/2.

## Scenario Alignment

Deltas: `specs/us-station/spec.md` (Registration Surface MODIFIED superset + 2 ADDED),
`specs/api/spec.md` (1 ADDED), `specs/system-data/spec.md` (2 MODIFIED supersets +
1 ADDED).

### us-station — Registration Surface (7)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Create a booked order (derived entry) | IMPLEMENTED | walk-in → REGISTERED `UsRegistrationApiTests.java:85`; future → BOOKED `:196`; composition `RegistrationOrderServiceImpl.java:195` (TransactionTemplate, port resolve outside) |
| Invalid pre-registration refused verbatim | IMPLEMENTED | past `:249` (预登记时间不能早于当前时间), unconfigured `:274` (该检查类型未开通预登记), outside window `:296` (不在开台时间窗内), at capacity `:315` (该时段已满) — all four D5 tokens, nothing persisted |
| Below-capacity pre-registration occupies the slot | IMPLEMENTED | `UsRegistrationApiTests.java:196` (real rows; window count 0→1, the service's own first-item-modality count predicate) |
| The create result carries the derived state | IMPLEMENTED | result/view echo assertions `:85` (bookingTime null) + `:196` (bookingTime echoed); `RegistrationCreateResult` + view carry the fields |
| Illegal transition is refused verbatim | IMPLEMENTED | `UsRegistrationApiTests.java:711` (非法状态迁移 BOOKING_CANCELLED → REGISTERED, seeded BOOKED); the transitional break `:352` (REGISTERED → REGISTERED) |
| Cancel carries a reason | IMPLEMENTED | `UsRegistrationApiTests.java:739` (both branches, reasons stored) |
| Modality reaches the issuer port | IMPLEMENTED | `UsRegistrationApiTests.java:583` (port receives 登记号 + token verbatim; jsonb persists) |

### us-station — Workbench Query Surface (5)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Status multi-select composes | IMPLEMENTED | `UsRegistrationApiTests.java:371` (IN semantics, row-scoped) |
| Snapshot conditions hit the jsonb document | IMPLEMENTED | `:393` (cardNo/patId/patientSource exact + three negative narrowing pins) |
| Exam-item name fuzzy per-element | IMPLEMENTED | `:438` (itemName fragment matches; a modality-key fragment does NOT — the whole-document false positive pinned dead) |
| Apply-time range bounds create_time | IMPLEMENTED | `:464` (inside + future-only absence) |
| Status counts respect the filters | IMPLEMENTED | `:488` (zero-filled six states; the filter narrows 2+1; `RegistrationOrderServiceImpl.java:243` group-by, `:272` shared wide predicate) |

### us-station — Slot Config Administration (6)

| Scenario | Status | Evidence |
|:--|:--|:--|
| V4 vendoring lands both kernel artifacts | IMPLEMENTED | `UsSchemaMigrationTests.java:139` (booking_time + exam_slot_config in us schema) + the V1→V4 scratch-replay split-safety proof |
| Create stores one trimmed live row per modality | IMPLEMENTED | `UsSlotConfigApiTests.java:62` (padded token stored trimmed; structured windows decode) |
| Duplicate live modality refuses | IMPLEMENTED | `:96` (A0400 names the token) |
| Update edits the policy, token immutable | IMPLEMENTED | `:110` (granularity/capacity update + the token-change refusal) |
| Delete is soft | IMPLEMENTED | `:140` (deleted=1, off the live list, the freed token re-creates) |
| The perms family gates every action | IMPLEMENTED | `:165` (tokenless 203, A0301, per-verb gates) |

### api — Saved Search Filter Endpoints (5)

| Scenario | Status | Evidence |
|:--|:--|:--|
| List returns the caller's filters | IMPLEMENTED | `AuthSearchFilterTests.java:100` (uid-scoped, foreign-uid row invisible) |
| Create stores a named filter | IMPLEMENTED | `:100` (query jsonb verbatim; uid binds server-side — never client-bindable) |
| Set default replaces the previous default | IMPLEMENTED | `:100` (full-replace in one transaction `SearchFilterServiceImpl.java:89`; the partial unique index backstop `:119`) |
| Update/delete touch only the owner's rows | IMPLEMENTED | `AuthSearchFilterTests.java:100` (ownership miss = NOT FOUND, no content leak `SearchFilterServiceImpl.java:109`) |
| Unauthenticated access is refused | IMPLEMENTED | `AuthSearchFilterTests.java:100` (tokenless 203) |

### system-data — menus + table (7)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Fresh deployment seeds the exam-setting group (superset) | IMPLEMENTED | preserved suite green (reactor) + the new leaf rides V51 |
| Migrated database keeps non-admin grants alive (superset) | IMPLEMENTED | untouched migrations; existing suite green |
| Re-execution is a no-op (superset) | IMPLEMENTED | existing suite green; the V51 family adds its own replay proof below |
| The 预约设置 leaf seeds additively | IMPLEMENTED | `AuthSlotConfigMenuSeedTests.java:37` (leaf under 检查配置, path/component ruled, ancestor chain to 设置) |
| Admin role receives the button grants | IMPLEMENTED | `AuthSlotConfigMenuSeedTests.java:59` (3 F nodes + role-1 grants) |
| The table lands with the house shape | IMPLEMENTED | `AuthSearchFilterTests.java:79` (columns + the partial unique default index) |
| One default per user and page | IMPLEMENTED | `AuthSearchFilterTests.java:100` + the index (DB backstop) |

## Issues

Environmental + one repin — none leave failing behavior:

1. **Shared-server W-grant drift (reactor)**: the ①-identical `AuthWorkstationSeedTests`
   incident (expected 1 was 3) — self-repairing by the committed V22 re-execution; DB
   verified 3 W-nodes/3 grants after; isolated rerun green. Same known hazard family.
2. **Public-network drop cluster (mid-run)**: earlier runs killed 3-4 random tests with
   `SocketTimeoutException`/`EOFException` (zero assertion failures); the rerunFailingTestsCount
   tolerance + isolated reruns cleared them; the final reactor ran during a stable window
   except the incidents above.
3. **Missed repin caught by the reactor (honest RED tail)**: the FIXED-BOOKED flip reached
   one suite outside the batch-2 impact scan — `UsSnapshotPollerTests` cancelled-history
   branch expected BOOKING_CANCELLED from a create+cancel flow; repinned to the derived
   CANCELLED (both cancel states sit outside the live-set alike — the merge semantics
   unchanged); isolated rerun green.

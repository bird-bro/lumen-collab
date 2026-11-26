# Proposal: registration-order-status-dict-sev

> Cross-stack feature `registration-order-status-dict` — backend half. The frontend
> half is `registration-order-status-dict-web`; shared decisions live in
> `openspec/coordination/registration-order-status-dict.md`.

## Why

The kernel document state machine's vocabulary no longer matches the desk's reality
(user ruling 2026-11-26): 处理中 (`IN_PROGRESS`) cannot tell 检查中 (exam being
performed) from 诊断中 (report being written), 已打印 has no state at all, and
`BOOKED`/`BOOKING_CANCELLED` are dead vocabulary since the 预约=预登记 ruling
(2026-11-24) — no flow produces them, and the cancel service still carries their
branch as dead code. The status labels are hard-coded frontend constants, so the
vocabulary cannot be operator-maintained like every other desk vocabulary
(患者来源 1023, 妊娠状态 1002).

## What Changes

- **BREAKING** — the kernel `ExamOrderStatus` enum collapses to the six registration
  statuses: `REGISTERED` (已登记), `IN_EXAM` (检查中), `IN_DIAGNOSIS` (诊断中),
  `COMPLETED` (已完成), `PRINTED` (已打印), `CANCELLED` (已取消).
  `BOOKED` and `BOOKING_CANCELLED` are removed entirely (user ruling 2026-11-26,
  fork 3): cancel always lands `CANCELLED`.
- **BREAKING** — state machine edges: 已登记 →(检查开始 = MPPS IN PROGRESS)→ 检查中
  →(进报告页抢占)→ 诊断中 →(完成报告)→ 已完成 →(打印)→ 已打印, with 已取消 reachable
  from 已登记/检查中/诊断中 (report not yet 已出) and refused afterwards. Forward edges
  stay permissive (every later stage reachable from each earlier live state) so orders
  do not strand while the MPPS/report callers are absent.
- The 登记 transition endpoint (the retired `BOOKED`→`REGISTERED` arrival step) is
  removed — its edge no longer exists in the machine.
- New dict catalog **1031 「登记单状态」** (seed V55 in the system ledger; six details,
  `code` = the catalog-id-prefixed family 103101–103106 (the 100401 性别 anchor shape —
  the user's same-day correction superseding the draft's bare 1–6), `value` = the
  中文 label, `tag` = the configured color token) — the label AND color authority for
  every status surface; the stored status token stays the enum name (varchar column
  unchanged). The enum-name ↔ dict-code bridge is the cross-stack contract recorded in
  the coordination doc.
- The slim dict-lookup projection carries the V9 spare `tag` as OPTIONAL display
  metadata (`@JsonInclude(NON_NULL)` — catalogs without tags keep the strict
  `{code, value}` shape; the recorded "no tag on the payload" stance is amended for
  this first legitimate consumer), so the page renders the DB-configured status
  colors (the user's 2026-11-26 color ruling).
- Legacy status data remap (V56, us ledger): `BOOKED`→`REGISTERED`,
  `BOOKING_CANCELLED`→`CANCELLED`, `IN_PROGRESS`→`IN_EXAM`.
- The 检查中 trigger is the MPPS `IN PROGRESS` event and the 诊断中 trigger is the
  report-page preemption lock (diagnosis prototype rulings) — this change lands the
  machine edges and the trigger contract only; the MPPS SCP caller and the workbench
  lock caller arrive with their own proposals (lumen-integration is currently empty).
- US status-count endpoint zero-fill keys and live-poll predicates follow the
  six-state enum; the cancel branch simplifies to the single `CANCELLED` landing.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `exam-workflow-kernel`: Document State Machine — the six-state machine, no
  已预约/取消预约 states, trigger annotations, updated scenarios; Pre-Registration
  Support — the "edges unchanged (`BOOKED → REGISTERED` stays defined)" clause dropped.
- `us-station`: Registration Surface — cancel always lands 已取消, the 登记 transition
  endpoint removed, status vocabulary follows the six-state enum; Workbench Query
  Surface — status counts zero-fill over the six states.
- `api`: Dict Items Lookup By Catalog Name — the slim projection gains the optional
  `tag` display-metadata key (the color carrier; NON_NULL so untagged catalogs keep
  the two-key shape).

## Impact

- `lumen-core`: `ExamOrderStatus`, the state-machine edge table + javadoc, the removed
  constants' consumers (`PreRegistrationPolicy` untouched — already returns
  `REGISTERED`; the cancel guard is unchanged).
- `lumen-us`: cancel branch simplification (no `BOOKED` fork), live-poll
  `notIn(CANCELLED)`, status-count zero-fill keys, the removed transition endpoint;
  `V56__exam_order_status_six_states.sql` under the us ledger.
- `lumen-system`: `V55__registration_status_dict.sql` under the system ledger (dict
  1031 seed, the V52 shape: `catalId*1000+seq` detail ids, ON CONFLICT + setval
  discipline).
- Wire break: the `status` string family and the status IN-filter values change on
  `/api/v1/us/**` order endpoints; the status-count response keys change; no other
  service consumes them. Deployment order: the single-jar boot runs Flyway (remap)
  before serving, so the remap and the new enum land together.

# Design: registration-order-status-dict-sev

> Shared decisions live in `openspec/coordination/registration-order-status-dict.md`;
> this file holds the backend-specific mechanics only.

## D1 — The six-state enum and edge table

`ExamOrderStatus` (lumen-core) becomes: `REGISTERED`, `IN_EXAM`, `IN_DIAGNOSIS`,
`COMPLETED`, `PRINTED`, `CANCELLED`. Javadoc carries the trigger annotations (检查开始 =
MPPS IN PROGRESS; 诊断抢占 = report-page lock; 完成报告; 打印). The edge table
(`ExamOrderStateMachineServiceImpl.EDGES`) becomes:

- `REGISTERED → {IN_EXAM, IN_DIAGNOSIS, COMPLETED, CANCELLED}`
- `IN_EXAM → {IN_DIAGNOSIS, COMPLETED, CANCELLED}`
- `IN_DIAGNOSIS → {COMPLETED, CANCELLED}`
- `COMPLETED → {PRINTED}` — `PRINTED` and `CANCELLED` terminal (absent from the map).

Forward-permissive by design (coordination doc): the strict presentation (动作条启用矩阵)
is the workbench UX's job, not the machine's. The cancel guard (report 已出 refuses every
cancel branch) is unchanged — it sits in front of the edge check and keeps its
`ExamReportStatus` read.

## D2 — Trigger callers are out of scope

This change lands the machine + the trigger CONTRACT (the javadoc + the coordination
table). The MPPS SCP (lumen-integration is an empty module), the report-page preemption
lock, and the print action arrive with their own proposals and call
`ExamOrderStateMachineService.transition` like every other consumer.

## D3 — Removed surface: the 登记 transition endpoint

The us 登记 transition endpoint existed only to advance the retired `BOOKED` arrival
step; its edge is gone, so the endpoint is deleted outright (controller route + the
service transition method if dedicated + their tests). The cancel and count endpoints
stay.

## D4 — V55 dict seed (system ledger)

`V55__registration_status_dict.sql` follows the V52 shape verbatim: `dict_catal`
(1031, '登记单状态', enable 1, sort 99, remark) + six `dict_detail` rows
(`1031001`–`1031006`, `ca_id` 1031, `code` the catalog-id-prefixed family
`103101`–`103106` (the 100401 性别 anchor shape — the user's same-day correction
superseding the draft's bare 1–6), `value` the 中文 labels, `tag` the configured
color tokens (`blue`/`amber`/`amber`/`green`/`green`/`gray` — the user's same-day
color ruling; the operator retunes in 字典管理), `sort` 1–6),
`ON CONFLICT (id) DO NOTHING` + the two `setval` tails. `code` is the bridge half;
`value` is the label and `tag` the color the frontend renders.
**History note**: this uncommitted migration was re-applied twice through the
never-rewrite ruling's sanctioned path (drop the history row + replay — ON CONFLICT
keeps the operator-corrected rows): once for the code-prefix correction, once for the
tag seeds.

## D8 — The slim lookup payload carries the optional `tag`

`DictItemDto` gains `tag` (the V9 spare pair's string half) with
`@JsonInclude(Include.NON_NULL)`: catalogs whose details carry no tag serialize the
exact two-key `{code, value}` shape (every existing consumer + the size==2 test
assertions hold), tagged items add the third key. Both projections
(`listEnabledItemsByName` / `listEnabledItemsByCaId`) map the entity's tag when
non-null. The DTO javadoc's "tag/flag/color/css have no place on a lookup payload"
stance is amended: the spare pair IS the designed extension point and this is its
first legitimate consumer; `flag` stays off (no consumer).

## D5 — V56 legacy remap (us ledger)

`V56__exam_order_status_six_states.sql` (us ledger, the only live `exam_order`):

```sql
UPDATE exam_order SET status = 'CANCELLED'   WHERE status = 'BOOKING_CANCELLED';
UPDATE exam_order SET status = 'REGISTERED'  WHERE status = 'BOOKED';
UPDATE exam_order SET status = 'IN_EXAM'     WHERE status = 'IN_PROGRESS';
```

Idempotent by construction (each WHERE goes false after the first pass). Mapping
rationale: 处理中 meant exam-in-progress → the 检查中 stage; legacy `BOOKED` rows fold
into 已登记 (the arrival-free model treats booking as registered);
`BOOKING_CANCELLED` → 已取消.

**Accepted risk (rollback)**: the single-jar boot runs Flyway before serving, so the
remap and the new enum always land together going forward; a rollback to the old jar
against remapped data would explode the old enum on `IN_EXAM`/`IN_DIAGNOSIS`/`PRINTED`
names (MyBatis name-based enum handling) and needs the reverse remap — accepted
pre-production; no compat shim (HERO scope).

## D6 — Consumer touch points

- `RegistrationOrderServiceImpl`: the cancel fork collapses (`status == BOOKED ? …` →
  always `CANCELLED`); the two `notIn(BOOKING_CANCELLED, CANCELLED)` live predicates
  become `notIn(CANCELLED)` (line ~232, ~324); the status-count loop already iterates
  `values()` (zero-fill follows the enum).
- `PatientSnapshotPoller`: same `notIn` narrowing (two sites).
- `HisTwoPhaseIntakeServiceImpl` / `PreRegistrationPolicy`: already `REGISTERED`-only —
  untouched.
- No department schema DDL change: `status` stays varchar(32) (the longest new name,
  `IN_DIAGNOSIS`, fits).

## D7 — Wire contract

Status strings and the status IN-filter values move to the six enum names; the
status-count response keys follow. The dict-1031 bridge (enum-name ↔ code ↔ label) is
the cross-stack contract recorded in the coordination doc — the backend does NOT
consume the dict (statuses are machine vocabulary, not dict-validated data); the dict
serves the frontend label surfaces.

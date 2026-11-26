# Coordination: registration-order-status-dict

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "registration-order-status-dict"`.

## Why

The kernel document state machine's vocabulary no longer matches the desk reality
(user ruling 2026-11-26): 处理中 (`IN_PROGRESS`) conflates 检查中 (exam being performed)
with 诊断中 (report being written), 已打印 has no state, and `BOOKED`/`BOOKING_CANCELLED`
are dead vocabulary since the 预约=预登记 ruling (2026-11-24). The desk's status labels
must become operator-maintainable dict vocabulary, like 患者来源 1023 / 妊娠状态 1002.

## Shared Design

- **The six statuses** (user ruling 2026-11-26): 已登记 `REGISTERED` → 检查中 `IN_EXAM`
  → 诊断中 `IN_DIAGNOSIS` → 已完成 `COMPLETED` → 已打印 `PRINTED`, plus 已取消
  `CANCELLED`. `BOOKED` and `BOOKING_CANCELLED` are removed from the kernel enum
  entirely; cancel always lands `CANCELLED`.
- **Edges** (forward-permissive, cancel-guarded — recorded backend decision): each
  later stage is reachable from every earlier live state
  (`REGISTERED→{IN_EXAM,IN_DIAGNOSIS,COMPLETED,CANCELLED}`,
  `IN_EXAM→{IN_DIAGNOSIS,COMPLETED,CANCELLED}`,
  `IN_DIAGNOSIS→{COMPLETED,CANCELLED}`, `COMPLETED→{PRINTED}`), terminal
  `PRINTED`/`CANCELLED`. Forward permissiveness keeps orders from stranding while the
  stage callers are absent; the workbench UX (diagnosis prototype 动作条矩阵) enforces
  the strict presentation. Cancel refuses when the report is 已出 (guard unchanged).
- **Trigger contract** (edges land now; callers arrive with their own proposals):
  检查中 = MPPS `IN PROGRESS` event (lumen-integration, currently an empty module);
  诊断中 = the report-page preemption lock (diagnosis workbench); 已完成 = 完成报告;
  已打印 = the print action.
- **Dict 1031 「登记单状态」** (seed V55, system ledger, the V52 shape): six details
  `code` = 目录ID前缀 (catalId*100 + seq — the 100401 性别 anchor-family shape; the
  user's 2026-11-26 same-day correction superseding the draft's bare 1-6),
  `value` = the 中文 label, `tag` = the configured color token (the user's same-day
  color ruling: the page renders the DB-configured colors), detail ids
  `1031001`–`1031006`. The stored status token STAYS the enum name (the
  `exam_order.status` varchar(32) column and the API wire are untouched in shape).
  The enum-name ↔ dict-code bridge is the recorded cross-stack contract:

  | code | enum | value | tag (色) | sort |
  |:--|:--|:--|:--|:--|
  | 103101 | REGISTERED | 已登记 | blue | 1 |
  | 103102 | IN_EXAM | 检查中 | amber | 2 |
  | 103103 | IN_DIAGNOSIS | 诊断中 | amber | 3 |
  | 103104 | COMPLETED | 已完成 | green | 4 |
  | 103105 | PRINTED | 已打印 | green | 5 |
  | 103106 | CANCELLED | 已取消 | gray | 6 |

- **Color carrier** (the DictItemDto amendment): the slim lookup projection carries
  the V9 spare `tag` as OPTIONAL display metadata (`@JsonInclude(NON_NULL)` — catalogs
  without tags keep the strict `{code, value}` shape, the recorded "no tag on the
  payload" stance is amended for this first legitimate consumer; `flag` stays off).
  The frontend maps the tag through a token map — blue → `colorPrimary`/`colorPrimaryBg`,
  green → `colorSuccess`/`colorSuccessBg`, amber → `colorWarning`/`colorWarningBg`,
  red → `colorError`/`colorErrorBg`, gray/unknown → the neutral fills — never ad-hoc
  hex (the house token rule); the operator retunes colors in 字典管理 with zero code.

- **Bridge consumers** (frontend): `US_ORDER_STATUS_DICT_ID = 1031` constant +
  the enum-name→code map resolve chip labels (and colors) through `dictItemsById`
  (id-anchored ruling 2026-11-19); chip ORDER follows the dict `sort`.
  Dict failure degrades to the bridge's own 中文 labels + neutral colors (the desk
  meter must survive; unlike the four FORM vocabularies, whose failure renders empty
  option sets — a deliberate, recorded deviation for a display-only vocabulary).
- **Legacy data remap** (V56, us ledger): `BOOKED→REGISTERED`,
  `BOOKING_CANCELLED→CANCELLED`, `IN_PROGRESS→IN_EXAM` (处理中 during exam → the exam
  stage). Single-jar boot applies it before the new enum serves.
- **Removed surface**: the us 登记 transition endpoint (the retired `BOOKED` arrival
  step) is deleted; the wide-list status IN-filter and status-count keys move to the
  six enum names.

## Decisions

- Six statuses exactly (已登记/检查中/诊断中/已完成/已打印/已取消), dict-maintained in
  catalog 1031 - user ruling - 2026-11-26
- 检查中 trigger = MPPS IN PROGRESS event (fork 1, recommended) - user ruling - 2026-11-26
- 已打印 = an independent machine state (COMPLETED→PRINTED edge), NOT the prototype
  annotation's COMPLETED sub-state - user ruling supersedes the 2026-11-24 prototype
  note (fork 2) - 2026-11-26
- BOOKED / BOOKING_CANCELLED removed from the kernel entirely; cancel always lands
  已取消 (fork 3) - user ruling - 2026-11-26
- Forward-permissive edges (later stage reachable from each earlier live state) so
  orders cannot strand while MPPS/report callers are absent - backend design - 2026-11-26
- Status token stays the enum name on the wire; dict 1031 is the LABEL authority only
  (codes = the catalog-id-prefixed family 103101-103106, the user's same-day code
  correction; the 100401 性别 anchor shape) - backend design - 2026-11-26
- Status colors are DB-CONFIGURED (the V55 `tag` seeds, the user's same-day color
  ruling): the slim lookup payload carries the optional `tag`, the page maps the hue
  through design tokens (never hex), the operator retunes in 字典管理 - 2026-11-26
- Chip-label dict failure degrades to bridge fallback labels (meter survives), unlike
  form vocabularies' empty-options degrade - frontend design - 2026-11-26

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | registration-order-status-dict-sev | proposing | - |
| frontend | registration-order-status-dict-web | proposing | - |

## Gate

Verify each stack independently. When ALL registered changes have an acceptable verify
credential (verify.md overall = PASS), batch-archive them and sync both spec deltas.

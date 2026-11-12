# Proposal — log-query-date-range

## Why

The `/system/logs` audit page filters by a bare month code (`dateYm`, e.g.
`202611`) — ops must already know the month before searching — and carries an
operator-uid filter nobody needs. User ruling 2026-11-11 (in session): drop the
uid filter from the page, replace 月份 with a date range (with common presets),
and the 类别 dropdown must show dict values, not codes (that label bug is
already fixed in-spec in `lumen-web` on 2026-11-11; this change locks it into
the spec). The date range necessarily changes the API contract, so both stacks
share this single change (user chose the simple-proposal form over the
two-change cross-stack ceremony).

## What Changes

- **lumen-sev (additive)**: `GET /api/v1/system/logs` gains optional
  `beginDate`/`endDate` (`yyyy-MM-dd`), each optional, AND-combined with the
  existing filters; double-closed day bounds (`beginDate 00:00:00` inclusive →
  `endDate` end-of-day inclusive). Unparseable dates or `beginDate > endDate`
  → `A0400`. The predicate derives spanned `date_ym` month keys so the
  `RANGE (date_ym)` partitions still prune. `uid`/`dateYm` are RETAINED — old
  callers unaffected (user-ratified additive strategy).
- **lumen-web**: filter bar becomes 类别 / 账号 / 日期段 (RangePicker with
  presets 今日 / 近7天 / 近30天 / 本月); the 操作人ID and 月份 inputs are
  removed; queries send `beginDate`/`endDate` only when both bounds are set.
  Explicit `dayjs` dependency (pnpm strict resolution — antd's transitive dep
  is not importable).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system-data` — § Log Query Endpoint: filter list gains `beginDate`/`endDate`
  with double-closed day semantics, A0400 validation, and a partition-pruning
  guarantee; existing filters explicitly preserved.
- `web-system-console` — § Operation Log Query: filter bar rework (类别/账号/
  日期段), uid + 月份 removal, preset behavior, and a guard scenario that the
  类别 dropdown options render dict values.

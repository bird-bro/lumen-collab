# Spec Delta — system-data (log-query-date-range)

## MODIFIED Requirements

### Requirement: Log Query Endpoint
The service SHALL expose the SINGLE paged audit-log query under `/api/v1/system/logs`
(filters: `type` Integer, `uid`, `account`, `dateYm`, and — added by this change —
`beginDate` and `endDate` `yyyy-MM-dd` strings; ordered by `actionTime` descending,
`id` descending tie-break), gated server-side by `system:log:list`. Responses SHALL
select-exclude the `session` column — credential material never reaches admin screens.
The sign-log endpoint `GET /api/v1/system/logs/sign` and the `system:signlog:list`
permission are REMOVED. The operation-log WRITE path (`LogAspect` + `AsyncFactory` +
entities/mappers/service) stays in the shared `framework` library; `lumen-system` hosts
the admin query facade. There is deliberately no mutation endpoint.

The `beginDate`/`endDate` bounds are each optional and AND-combined with the other
filters. Day bounds are double-closed: `beginDate` includes the whole start day
(`action_time >= begin 00:00:00`), `endDate` includes the whole end day (implemented as
`action_time < endDate+1d 00:00:00`). An unparseable date value or a `beginDate` after
`endDate` SHALL reject with `A0400`. The range predicate SHALL constrain `date_ym` to
the spanned month keys so `RANGE (date_ym)` partition pruning is preserved. The pre-
existing `uid`/`dateYm` filters are RETAINED unchanged (additive contract).

#### Scenario: Query operation logs
- **WHEN** an administrator holding `system:log:list` queries the log endpoint with any
  combination of the filters
- **THEN** `log_info` rows (both event families) return newest-first with the filters
  applied

#### Scenario: Account filter matches sign and operation rows
- **WHEN** the `account` filter is applied
- **THEN** rows from BOTH event families whose account matches return (sign-page parity)

#### Scenario: Integer type filter
- **WHEN** the `type` filter carries an Integer code (e.g. 11)
- **THEN** only rows with that code return; a non-integer value is rejected as a bad
  request parameter

#### Scenario: Sign-log endpoint is gone
- **WHEN** any client calls `GET /api/v1/system/logs/sign`
- **THEN** the endpoint no longer exists (404)

#### Scenario: Session column excluded
- **WHEN** audit rows are returned by the endpoint
- **THEN** no row carries a `session` field

#### Scenario: Date-range bounds filter action_time
- **WHEN** an administrator holding `system:log:list` queries with
  `beginDate=2026-10-20&endDate=2026-11-11`
- **THEN** rows whose `action_time` falls in `[2026-10-20 00:00:00, 2026-11-12 00:00:00)`
  return newest-first with paging
- **AND** the query constrains `date_ym` to the spanned month keys (202610, 202611) —
  partitions outside the span are pruned

#### Scenario: Single-bound range
- **WHEN** only `endDate` is sent
- **THEN** rows are bounded by `action_time < endDate+1d 00:00:00` with no start bound
- **WHEN** only `beginDate` is sent
- **THEN** rows are bounded by `action_time >= beginDate 00:00:00` with no end bound

#### Scenario: Invalid range rejected
- **WHEN** a bound is unparseable as `yyyy-MM-dd`, or `beginDate` is after `endDate`
- **THEN** the endpoint rejects with `A0400` and returns no rows

#### Scenario: Existing filters unchanged (additive guarantee)
- **WHEN** the endpoint is queried with `uid` and/or `dateYm` alone, or combined with
  the new range bounds
- **THEN** they filter exactly as before this change (regression guard)

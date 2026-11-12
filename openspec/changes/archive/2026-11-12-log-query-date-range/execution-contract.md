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

- **Change name**: log-query-date-range
- **Problem to solve**:
The `/system/logs` audit page filters by a bare month code (`dateYm`, e.g.
`202611`) — ops must already know the month before searching — and carries an
operator-uid filter nobody needs. User ruling 2026-11-11 (in session): drop the
uid filter from the page, replace 月份 with a date range (with common presets),
and the 类别 dropdown must show dict values, not codes (that label bug is
already fixed in-spec in `lumen-web` on 2026-11-11; this change locks it into
the spec). The date range necessarily changes the API contract, so both stacks
share this single change (user chose the simple-proposal form over the

<!-- AI: summarize the above into 1-2 sentences -->
- **Intent summary**: Make the audit-log query triage-shaped — API gains optional
  `beginDate`/`endDate` (additive; uid/dateYm retained), the page filter bar becomes
  类别 / 账号 / 日期段 (RangePicker + presets), dropping 操作人ID and 月份.
- **In scope**: sev — controller params + new service overload + partition-aware range
  predicate + A0400 validation; web — filter-bar rework with presets, `dayjs` dep,
  wire-call changes; the 类别 dict-value dropdown guard scenario.
- **Out of scope**: removing `uid`/`dateYm` from the API (retained per user-ratified
  additive strategy); log write-path changes; log retention/archive; new endpoints.

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/system-data/spec.md`
5:### Requirement: Log Query Endpoint
24:#### Scenario: Date-range bounds filter action_time
32:#### Scenario: Single-bound range
38:#### Scenario: Invalid range rejected
42:#### Scenario: Existing filters unchanged (additive guarantee)
- `specs/web-system-console/spec.md`
5:### Requirement: Operation Log Query
20:#### Scenario: Filtered log query
25:#### Scenario: Date-range drives beginDate/endDate
30:#### Scenario: Presets populate both bounds
35:#### Scenario: Clearing the range removes both params
39:#### Scenario: Dict values render in the 类别 dropdown (guard)
43:#### Scenario: Dict-driven category filter and labels
50:#### Scenario: Sign rows show em-dash for operation-only columns
54:#### Scenario: Read-only surface
58:#### Scenario: Menu-driven visibility follows the page permission


## Design Constraints

- **Architecture/Decisions**: per `design.md` D1–D6 — additive API (new
  `ILogInfoService` overload; existing overloads byte-identical); strict
  `yyyy-MM-dd` parse (ISO_LOCAL_DATE) with double-closed day bounds; `A0400` on
  unparseable/inverted range; `date_ym` month-key derivation for partition pruning
  (skip IN when span > 12 months); RangePicker presets 今日/近7天/近30天/本月; page
  sends both bounds only when both picked; backend-first gate before web tasks.
- **Technical constraints**: JDK 11 / Spring Boot 2.5.15 pinned train (no version
  moves); schema changes none (query-only); `dayjs` must become an explicit
  lumen-web dependency (pnpm strict resolution); frontend builds against the REAL
  backend — no mock layer.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 TDD red: `AuthLogQueryTests` range scenarios — two-month span
11:- [ ] 1.2 `LogController.list` gains `beginDate`/`endDate` String params with
18:- [ ] 2.1 `pnpm add dayjs`. TDD red: `logs-page.test.tsx` — no 操作人ID/月份
23:- [ ] 2.2 Full `pnpm test` + `pnpm build` exit 0.
27:- [ ] 3.1 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md.

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: date-range bounds (two-month span +
  month-key assertion), single-bound queries, `A0400` on unparseable/inverted range,
  wire-call carries `beginDate`/`endDate` + page reset, preset fills both bounds,
  clear sends neither, filter-bar shape without uid/月份 inputs.
- **Required edge cases**: only-`endDate`; only-`beginDate`; `beginDate == endDate`
  (single day inclusive); span > 12 months (IN-constraint skipped, still correct);
  combined with uid/dateYm.
- **Regression-sensitive areas**: existing `uid`/`dateYm` behavior (additive
  guarantee); `deleted = 0` + session select-exclusion + actionTime DESC ordering
  unchanged; 类别 dict-value dropdown guard scenario.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 5 sequential tasks in one change, no parallel sessions —
  the sev→web ordering is a dependency, not a coordination need; SDD ceremony
  declined by the user (simple-proposal form).

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: DP-3 user approval of this contract before any apply;
  user verification of the real-backend API after sev tasks (gates web tasks 2.x).
- **Blocking categories**: any change touching `uid`/`dateYm` removal (out of scope);
  API contract changes beyond the two new optional params.

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

# Spec Delta — web-system-console (log-query-date-range)

## MODIFIED Requirements

### Requirement: Operation Log Query
The console SHALL provide a SINGLE paged audit-log view of `GET /api/v1/system/logs` at
the route `/system/logs`, reachable as the 审计日志 menu entry in the 安全 group
(supersedes the separate 日志查询 / 登录日志 entries), gated by `system:log:list`,
rows newest first, no write affordances (append-only data). The page SHALL show BOTH
event families in one table with: 日期, 操作人, 类别 (dict-driven label from the
「审计日志类型」 catalog, raw-code fallback), 标题, 描述, IP, 操作系统, 浏览器, and
the operation-only 方法/URL columns rendering — for sign rows. The filter bar SHALL
offer: 类别 (dict-driven Select whose options render the dict VALUES — never the raw
codes), 账号 (account), and 日期段 (an antd RangePicker with the presets 今日 / 近7天 /
近30天 / 本月); the former 操作人ID (uid) and 月份 (dateYm) filters are REMOVED from
the page. The page SHALL send `beginDate`/`endDate` (`yyyy-MM-dd`) only when both
bounds are picked; filters AND-combine server-side. The page SHALL NOT render any
`session` field (credential anchor, server-excluded).

#### Scenario: Filtered log query
- **WHEN** an administrator queries the audit page with any filter combination
  (类别, 账号, 日期段)
- **THEN** rows from both event families return newest-first with paging

#### Scenario: Date-range drives beginDate/endDate
- **WHEN** the administrator picks a range and clicks 查询
- **THEN** the query carries `beginDate` and `endDate` as `yyyy-MM-dd` strings and the
  list resets to page 1

#### Scenario: Presets populate both bounds
- **WHEN** the administrator picks the 今日 preset
- **THEN** both bounds fill with today's date (近7天/近30天/本月 populate their spans
  the same way)

#### Scenario: Clearing the range removes both params
- **WHEN** the administrator clears the 日期段 picker and queries
- **THEN** the query carries neither `beginDate` nor `endDate`

#### Scenario: Dict values render in the 类别 dropdown (guard)
- **WHEN** the 类别 Select opens with the loaded 「审计日志类型」 catalog
- **THEN** each option displays its dict value (新增 / 登录失败 / …), not the raw code

#### Scenario: Dict-driven category filter and labels
- **WHEN** the page loads the 「审计日志类型」 dict
- **THEN** the 类别 filter is a Select of the catalog's items and the 类别 column
  renders the matching label
- **AND** when the dict fetch fails or lacks an entry, the column degrades to the raw
  code and one error message surfaces — the page never blocks

#### Scenario: Sign rows show em-dash for operation-only columns
- **WHEN** the table renders a sign-family row (type 10/11/12)
- **THEN** the 方法/URL cells show — ; an operation row's cells show the values

#### Scenario: Read-only surface
- **WHEN** the audit page renders
- **THEN** it offers no mutation controls for log rows

#### Scenario: Menu-driven visibility follows the page permission
- **WHEN** a signed-in user's permission set lacks `system:log:list`
- **THEN** the 审计日志 menu entry is not visible to that user

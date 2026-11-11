# Spec Delta — web-system-console (audit-log-unify-web)

## MODIFIED Requirements

<!-- The merged requirement keeps the Operation Log Query header (the surviving
     capability slot); the Sign-in Log Query requirement is REMOVED below. -->

### Requirement: Operation Log Query
The console SHALL provide a SINGLE paged audit-log view of `GET /api/v1/system/logs` at
the route `/system/logs`, reachable as the 审计日志 menu entry in the 安全 group
(supersedes the separate 日志查询 / 登录日志 entries), gated by `system:log:list`,
rows newest first, no write affordances (append-only data). The page SHALL show BOTH
event families in one table with: 时间, 类别 (dict-driven label from the
「审计日志类型」 catalog, raw-code fallback), 标题 (title; sign rows carry
Sign-IN/Sign-OUT), 描述, 操作人, IP, 操作系统, 浏览器, and the operation-only
方法/URL columns rendering — for sign rows. The filter bar SHALL offer: 类别
(dict-driven Select of the audit type codes), 操作人ID (uid), 账号 (account), and
月份 (dateYm); filters AND-combine server-side. The page SHALL NOT render any
`session` field (credential anchor, server-excluded).

#### Scenario: Filtered log query
- **WHEN** an administrator queries the audit page with any filter combination
- **THEN** rows from both event families return newest-first with paging

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

## REMOVED Requirements

### Requirement: Operation Log Query (standalone 日志查询 page)
The console SHALL NO LONGER provide a standalone 日志查询 page — the merged audit page
(MODIFIED Operation Log Query above) is its successor at the same route.

**Reason**: Merged into the unified audit-log requirement — same endpoint, same
permission, one page (coordination audit-log-unify D6).
**Migration**: The route `/system/logs` and component `system/log/index` are UNCHANGED;
the merged page is the successor. Filters carry over (type becomes the Integer-code
Select, uid, dateYm; account joins).

#### Scenario: The old standalone page no longer exists as a separate surface
- **WHEN** an administrator visits `/system/logs` after this change
- **THEN** the unified audit page renders (both event families) — no separate
  日志查询 surface remains

### Requirement: Sign-in Log Query (登录日志 page)
The console SHALL NO LONGER provide the 登录日志 page — the sign family is visible in
the unified audit page (MODIFIED Operation Log Query above).

**Reason**: Backend endpoint and permission retired by `audit-log-unify-api`
(coordination D4/D8); the sign family is visible in the unified page.
**Migration**: Account/uid/dateYm filters carry into the unified page; sign events are
narrowed by 类别 codes 10/11/12. The `/system/logs/sign` route and the
`system:signlog:list` visibility rule disappear with menu 17.

#### Scenario: The 登录日志 route and menu entry are gone
- **WHEN** an administrator opens the console after this change
- **THEN** `/system/logs/sign` matches no route (404) and the menu tree carries no
  登录日志 entry

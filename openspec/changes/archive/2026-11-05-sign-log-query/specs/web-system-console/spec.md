# Spec Delta

## ADDED Requirements

### Requirement: Sign-in Log Query
The console SHALL provide a paged sign-in log view of `GET /api/v1/system/logs/sign` at the
route `/system/logs/sign`, reachable as the 登录日志 menu entry in the 安全 group (sibling
of 日志查询), with the documented filters (`account`, `uid`, `dateYm`), newest first, and
no write affordances (append-only data). The page SHALL render the audit columns: 账号,
事件, 描述, IP, 操作系统, 浏览器, 操作时间.

#### Scenario: Filtered sign-log query
- **WHEN** an administrator queries the sign-in log page with an account or month filter
- **THEN** matching rows return newest-first and paging works

#### Scenario: Read-only surface
- **WHEN** the sign-in log page renders
- **THEN** it offers no mutation controls for log rows

#### Scenario: Menu-driven visibility follows the page permission
- **WHEN** a signed-in user's permission set lacks `system:signlog:list`
- **THEN** the 登录日志 menu entry is not visible to that user

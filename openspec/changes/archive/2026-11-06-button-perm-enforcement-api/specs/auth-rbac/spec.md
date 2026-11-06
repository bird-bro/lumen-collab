# auth-rbac Delta: button-perm-enforcement-api

## ADDED Requirements

### Requirement: Button-Level Permission Enforcement

Every console write endpoint SHALL be enforced at button level: the endpoint carries a
method-level `@PreAuthorize` whose authority is the button node's `perms` string (the
method annotation replaces the class-level page gate for that method; read endpoints
keep the page gate). The button inventory is the coordination doc's perms table - 用户
(create/reset-pwd/enable), 角色 (create/update/delete/assign), 菜单 (create/update/
delete), 机构 (create/update/delete), 科室 (create/update/delete), 字典目录
(dict-catal:create/update/delete) and 字典明细 (dict-detail:create/update/delete).

The Flyway seed SHALL insert the 22 button nodes (`type = F`) under their console C
pages with the inventory's `perms` strings, and SHALL grant every new node to the admin
role (role 1) - idempotently (guarded inserts + sequence resync, the V5 discipline), so
the super administrator sees and can re-grant every button without a manual step.

#### Scenario: Write endpoint rejects without the button perm

- **WHEN** an authenticated user holding only the page perm (`system:user:list`, via a
  role granted just the 用户管理 page) calls `POST /api/v1/system/users`
- **THEN** the request is denied with the `A0301` envelope and no user row is created

#### Scenario: Button perm alone authorizes the write

- **WHEN** a role is granted only the 新建用户 button node (not the page) and its user
  calls `POST /api/v1/system/users`
- **THEN** the request executes (the button perm is the action authority)
- **AND** the same user's `GET /api/v1/system/users/list` is still denied by the
  class-level page gate

#### Scenario: Admin sees every button after seeding

- **WHEN** the migrations run on a database where the admin role owned every menu
- **THEN** the 22 button nodes exist under their console pages with their `perms`
  strings
- **AND** the admin role's login/me permission set contains every button perms string

#### Scenario: Re-running the seed is a no-op

- **WHEN** `V15` runs again on an already-migrated database
- **THEN** no duplicate menu or role_menu rows appear (guarded inserts)

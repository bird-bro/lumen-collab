# Spec Delta: web-system-console

## MODIFIED Requirements

### Requirement: User Management

The console SHALL provide a user list view (`GET /api/v1/system/users/list/profile`,
which returns a plain list - the view paginates client-side) with create
(`POST /api/v1/system/users`), profile detail (`GET /api/v1/system/users/{id}`), profile
edit (`PUT /api/v1/system/users/{id}`), delete (`DELETE /api/v1/system/users/{id}`),
password reset (`PUT /api/v1/system/users/{id}/password`), and
enable/disable (`PUT /api/v1/system/users/{id}/enable`). A duplicate-username rejection SHALL
surface the backend's `A`-class `msg` on the form. The create request's `password` and the
reset request's `newPassword` SHALL be SM2-encrypted with the session-cached public key from
`GET /api/v1/system/auth-key` (C1C3C2 hex — coordination password-transport-encryption D4/D6)
inside the API-client boundary (`src/api/users.ts` keeps raw-password signatures; the typed
value never reaches the wire as plaintext). A backend `A0400` rejection SHALL surface
through the same message path as other `A`-class failures on the form. The list SHALL render
the columns 姓名+账号 / 角色 / 科室 / 职称 / 手机号 / 邮箱 / 创建时间 / 操作; the
role chips come from the row payload's `roleNames` (no session-profile substitute). The
create and edit dialogs SHALL carry the shared profile form (姓名/手机号/邮箱/性别/职称/
所属科室/角色/检查类型/审核权限/签名) — the edit dialog prefills from the detail endpoint
and keeps 登录账号 disabled. Gender/title/exam-type/audit-tier options SHALL resolve through
the dict-items-by-name lookup with raw-code fallback; the department options SHALL come from
the departments list client; the signature SHALL upload through the common image upload
endpoint as a controlled form control. Edit and delete affordances SHALL be gated by the
seeded `system:user:update` / `system:user:delete` button permissions; delete SHALL confirm
before calling the endpoint and a protected-account rejection SHALL surface the backend's
`msg`.

#### Scenario: Paged user list

- **WHEN** an administrator opens the user page
- **THEN** the user list renders and re-queries on filter/page change

#### Scenario: List columns render the profile payload

- **WHEN** the list returns rows with profile fields and `roleNames`
- **THEN** the 姓名 column shows the display name above the account, and the
  角色/科室/职称/手机号/邮箱 columns render the payload values (an unbound value renders
  as an empty dash, not an error)

#### Scenario: Role chips come from the row

- **WHEN** any row carries `roleNames`
- **THEN** the chips render exactly those names — including rows that are not the
  current session user (the former 未提供 chip no longer appears)

#### Scenario: Duplicate username feedback

- **WHEN** a user is created with an existing username
- **THEN** the backend's duplicate-username message is shown and the dialog stays open

#### Scenario: Create dialog carries the profile form

- **WHEN** the administrator opens the create dialog
- **THEN** it offers 用户名/姓名/手机号/邮箱/性别/职称/所属科室/角色/检查类型/审核权限/
  签名/初始密码 with the required-field markers on 用户名/姓名/初始密码, and a successful
  submit returns to the refreshed list

#### Scenario: Exam-type and audit-tier selects are dict-driven

- **WHEN** the 检查类型 multi-select or the 审核权限 select renders its options
- **THEN** the options resolve through the dict-by-name lookup (catalogs 检查类型 /
  审核权限) with the raw-code fallback on a missing catalog or failed fetch (the
  role-type-dict-web D4 precedent); 检查类型 submits a code array serialized as CSV

#### Scenario: Signature upload reuses the common transport

- **WHEN** the administrator picks a signature image in either dialog
- **THEN** the image flows through the common image upload endpoint, the control
  previews the stored path, and the dialog submits the path value (client-side
  extension/size validation mirrors the logo control; the path stays verbatim in the
  profile payload)

#### Scenario: Edit opens prefilled with detail

- **WHEN** the administrator presses 编辑 on a row
- **THEN** the dialog opens prefilled from `GET /api/v1/system/users/{id}` with 登录账号
  disabled, and a successful submit calls the profile update and refreshes the list

#### Scenario: Edit failure keeps the dialog

- **WHEN** the update is rejected (A-class msg or validation)
- **THEN** the dialog stays open with the backend `msg` on the form; no list reload

#### Scenario: Delete confirms then removes

- **WHEN** the administrator presses 删除 and confirms the Popconfirm
- **THEN** the delete endpoint is called and the row disappears from the list

#### Scenario: Protected-account delete rejection surfaces

- **WHEN** the backend rejects a delete (self or seed admin) with an `A`-class msg
- **THEN** the message surfaces as an error toast and the row stays

#### Scenario: Dict-driven gender and title with raw-code fallback

- **WHEN** the 性别/职称 options resolve (dict catalogs 性别/职称)
- **THEN** the selects render dict labels for stored codes; a code missing from the dict
  or a failed fetch renders the raw code as the label and one error message, never
  blocking the page (the role-type-dict-web D4 precedent)

#### Scenario: Department select lists departments

- **WHEN** the create or edit dialog opens
- **THEN** the 所属科室 select lists the live departments from the departments list
  client (label = department name, value = department id)

#### Scenario: Password and enable actions

- **WHEN** an administrator resets a user's password or toggles enable
- **THEN** the corresponding documented endpoint is called and the list reflects the result

#### Scenario: Encrypted password fields on create and reset

- **WHEN** the create dialog submits or the reset action confirms
- **THEN** the captured request body's `password` / `newPassword` value is a hex SM2
  ciphertext that differs from the typed value
- **AND** the backend accepts it (decrypt-then-BCrypt), so the operation succeeds exactly
  as the plaintext flow did before password-transport-encryption

#### Scenario: Edit and delete follow the new button permissions

- **WHEN** the session holds (or lacks) `system:user:update` / `system:user:delete`
- **THEN** the 编辑 / 删除 affordances render (or hide) accordingly, matching the
  button-perm-enforcement gating of the other row actions

<!-- synced from: user-edit-delete-profile-web 2026-11-11 -->

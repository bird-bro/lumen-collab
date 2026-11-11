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

- **Change name**: user-edit-delete-profile
- **Problem to solve**: The console user page is a minimal scaffold (create carries only
  username/name/roles/password; row actions only reset-pwd + enable; no edit/delete). This
  change adds the RCP-shaped admin surface: user edit (profile + role rebind), logical
  delete (self + seed-admin protected), richer profile fields (phone/email/gender/title/
  department), the business fields 检查类型/审核权限/签名, and list rows that self-describe
  (roleNames/depName from the payload).
- **In scope**: V26 migration (user_info 8 profile columns + 检查类型/审核权限 dict
  catalogs 1021/1022 + system:user:update/delete F nodes); list/detail/update/delete
  endpoints under /api/v1/system/users (new service methods, never-modify overloads);
  auth tests per task. Backend only — the web half is the sibling change
  `user-edit-delete-profile-web`.
- **Out of scope**: 设备权限 (awaits the org-domain room/device registry — see design
  Non-Goals); avatar; the extended archive set; consumer wiring of the new fields in
  radiology worklist/report flows; user_join-style tables; server-side paging.

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/api/spec.md`
5:### Requirement: User Management Endpoints
24:#### Scenario: Paged user list
30:#### Scenario: List rows carry the profile and role names
38:#### Scenario: Create user
44:#### Scenario: Duplicate username
49:#### Scenario: User detail for the edit dialog
56:#### Scenario: Detail of a missing user
61:#### Scenario: Profile update
69:#### Scenario: Signature stores the uploaded path
76:#### Scenario: Username is immutable through the update
81:#### Scenario: Update validates role ids and department
87:#### Scenario: User logical delete with cascade unbind
95:#### Scenario: Self-delete rejected
100:#### Scenario: Seed-admin delete rejected
105:#### Scenario: Password reset and enable toggle
110:#### Scenario: Encrypted password fields
117:#### Scenario: Plaintext or undecryptable value rejected
124:#### Scenario: New button permissions gate the new endpoints
- `specs/auth-rbac/spec.md`
5:### Requirement: User Management
16:#### Scenario: Administrator creates a user
21:#### Scenario: Duplicate username rejected
26:#### Scenario: Administrator edits a user's profile
34:#### Scenario: Role rebinding is a full replace
40:#### Scenario: Administrator deletes a user
46:#### Scenario: Protected accounts cannot be deleted
- `specs/data/spec.md`
5:### Requirement: User Profile Columns
13:#### Scenario: Profile columns added without breaking existing rows
19:#### Scenario: Department anchor references a live department
26:#### Scenario: Gender and title store dict codes
32:#### Scenario: Exam-type visibility stores a CSV of codes
38:#### Scenario: Signature stores a served path
- `specs/system-data/spec.md`
5:### Requirement: Dict Seed Data
20:#### Scenario: Fresh database carries the starter dictionary
26:#### Scenario: Re-run is a no-op
31:#### Scenario: Exam-type vocabulary renders abbreviations
37:#### Scenario: Audit-authority vocabulary carries the three tiers
- `specs/web-system-console/spec.md`
5:### Requirement: User Management
33:#### Scenario: Paged user list
38:#### Scenario: List columns render the profile payload
45:#### Scenario: Role chips come from the row
51:#### Scenario: Duplicate username feedback
56:#### Scenario: Create dialog carries the profile form
63:#### Scenario: Exam-type and audit-tier selects are dict-driven
70:#### Scenario: Signature upload reuses the common transport
78:#### Scenario: Edit opens prefilled with detail
84:#### Scenario: Edit failure keeps the dialog
89:#### Scenario: Delete confirms then removes
94:#### Scenario: Protected-account delete rejection surfaces
99:#### Scenario: Dict-driven gender and title with raw-code fallback
106:#### Scenario: Department select lists departments
112:#### Scenario: Password and enable actions
117:#### Scenario: Encrypted password fields on create and reset
125:#### Scenario: Edit and delete follow the new button permissions


## Design Constraints

- **Architecture/Decisions**:
- **D1 — Columns on `user_info`, not a join table.** `phone varchar(32)`, `email
  varchar(128)`, `gender integer`, `title integer`, `dep_id integer` (FK `org_dep`),
  all nullable. Rationale: one live department per user in the controlled model; the
  RCP `user_join` split solves a multi-org problem lumen does not have. Alternative
  (copy `user_join`) rejected: joins N tables for fields the console renders once, and
  RCP's own docs call the layout 乱 (REFERENCE.md: "表结构也比较乱需要重新整理").
- **D2 — Role names joined once per list, in SQL.** `listUsers` assembles
  `UserItemDto.roleNames` by one query over live `user_role` rows of all listed users
  + one `role_info` name fetch (no N+1 per-row mapper calls); `depName` resolves the
  same way (one batch fetch of the referenced `org_dep` rows). Alternative (frontend
  resolves role names from `roles/list`) rejected: the frontend would need EVERY role
  (page 1 defaults to 20) to label a user bound to role 500 — the payload must be
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
9:- [ ] 1.1 Write `V26__user_profile_columns.sql`: eight guarded `ADD COLUMN IF NOT EXISTS` on
18:- [ ] 1.2 Extend `UserInfo` entity with the eight nullable fields (Lombok combo
24:- [ ] 2.1 Add `roleNames`/`depName`/`phone`/`email`/`gender`/`title`/`depId`/`modalitys`/
31:- [ ] 2.2 Add `GET /{id}` detail: `UserDetailDto` (profile + roleIds + modalitys CSV +
38:- [ ] 3.1 Write `UpdateUserRequest` DTO (name/phone/email/gender/title/depId/roleIds/
42:- [ ] 3.2 Implement `UserInfoService.updateUser(Integer, UpdateUserRequest)` (NEW interface
48:- [ ] 3.3 Implement `UserInfoService.deleteUser(Integer)` (NEW interface method): self-delete
56:- [ ] 4.1 Wire the three new controller methods (`GET /{id}`, `PUT /{id}`, `DELETE /{id}`)
59:- [ ] 4.2 Run the aggregator build and the auth test family:
65:- [ ] 5.1 Create `openspec/coordination/user-edit-delete-profile.md` from `_template.md`:
68:- [ ] 5.2 Create the sibling change `user-edit-delete-profile-web` (proposal/spec deltas

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: detail endpoint (live profile + no password;
  dead id B0031); update partial semantics + roleIds full-replace + bad-id A0400 no-change
  + username immutability; delete happy path + self/seed-admin rejections + username
  re-creatability; list rows carry roleNames/depName with batch (≤ N+1 guard) resolution;
  V26 migration columns + catalogs + nodes + re-run no-op.
- **Required edge cases**: user with no roles/dep renders empty, not error; user_role
  unbind on delete is logical (deleted=1); duplicate username on create still A0111.
- **Regression-sensitive areas**: existing create/reset/enable endpoints byte-stable;
  AuthLoginTests family; the V25 audit-log migration's shape untouched.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 11 tasks, single module (lumen-system), sequential deps
  (migration → entity → read → write → wire → coordination docs). Batches follow
  tasks.md's numbered groups; coordination-doc tasks (5.1/5.2) are planning-file edits,
  not code. SDD subagent dispatch is unjustified overhead for one-module TDD with the
  pattern precedents already on disk (role-list-api is the nearest sibling).

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: after each tasks.md group (1-4) — diff vs the group's
  spec scenarios; after task 4.2 the full-suite gate before declaring complete.
- **Blocking categories**: any spec scenario not evidenced; a migration non-idempotence
  claim untested; entity/DTO password-hash leakage anywhere in a read path.

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

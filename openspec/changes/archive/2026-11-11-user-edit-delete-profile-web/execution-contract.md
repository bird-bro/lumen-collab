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

- **Change name**: user-edit-delete-profile-web
- **Problem to solve**:
The console user page (`/system/users`) cannot administer a RIS: the create dialog
carries four fields, the row actions carry only 重置密码 + the enable switch, and the
角色 column can only name the current session user's roles (the 未提供 chip). The
backend change (`user-edit-delete-profile`) now ships the real surface: profile
columns, role/department names in the payload, detail/update/delete endpoints, and the
two new button perms (`system:user:update` / `system:user:delete`).

- **In scope**: `src/api/users.ts` + `types.ts` additive clients (listWithProfile/
  detail/update/delete); `signature-upload.tsx` control (logo-upload pattern);
  `/system/users` page rebuild (profile columns, roleNames chips, edit/delete actions,
  shared create+edit drawer, dict-driven selects, deps select, perm gates);
  `users-page.test.tsx` rewrite (vi.mock at the client seam); real-backend smoke.
- **Out of scope**: avatar, 设备权限 (no backend field), server-side paging, auth
  stores/router/password-cipher changes, modifying `logo-upload.tsx` (compose, never
  modify), any backend code (the `-api` change owns it; verify PASS 32/32 2026-11-11).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
5:### Requirement: User Management
31:#### Scenario: Paged user list
36:#### Scenario: List columns render the profile payload
43:#### Scenario: Role chips come from the row
49:#### Scenario: Duplicate username feedback
54:#### Scenario: Create dialog carries the profile form
61:#### Scenario: Exam-type and audit-tier selects are dict-driven
68:#### Scenario: Signature upload reuses the common transport
76:#### Scenario: Edit opens prefilled with detail
82:#### Scenario: Edit failure keeps the dialog
87:#### Scenario: Delete confirms then removes
92:#### Scenario: Protected-account delete rejection surfaces
97:#### Scenario: Dict-driven gender and title with raw-code fallback
104:#### Scenario: Department select lists departments
110:#### Scenario: Password and enable actions
115:#### Scenario: Encrypted password fields on create and reset
123:#### Scenario: Edit and delete follow the new button permissions


## Design Constraints

- **Architecture/Decisions**:
- **D1 — One drawer for create AND edit** (the RCP page's single-drawer pattern): the
  drawer title and the submit path switch on an `editing` state; the edit prefill fetches
  `GET /users/{id}` on open. `登录账号` renders disabled in edit mode (spec: immutable).
- **D2 — Field unification**: 检查类型's Select (`mode="multiple"`) submits the code
  array JOINED to the CSV string the payload carries (`join(',')`); the edit prefill
  SPLITS the CSV back to the array (the RCP page's split/join precedent). 审核权限's
  Select value = the dict code.
- **D3 — Signature control**: `signature-upload.tsx`, a controlled form control mirroring
  `logo-upload.tsx` (value/onChange injected by the wrapping Form.Item, client-side
  extension whitelist jpg/png/jpeg + 2MB cap, `uploadImage()` → `onChange(path)`), the
  preview renders the stored path. jpg/png/jpeg only (not webp): the signature is a
  report-embedded image, the narrower whitelist matches the RCP control's rule.
- **Technical constraints**: TypeScript strict; function components + hooks only; AntD 5
  first, styling via design tokens (no ad-hoc hex); no red as a primary color; the page
  requests go through the REAL backend at runtime (vite proxy), unit tests stub the API
  client modules at the seam; never modify existing components - compose.

## Task Batches

- Batch 1 (1.1-1.2): clients + types (additive; no existing export renamed).
- Batch 2 (2.1): signature-upload.tsx control + validation-path tests.
- Batch 3 (3.1-3.2): page rebuild + shared drawer, RED→GREEN per behavior.
- Batch 4 (4.1-4.2): full suite + build; real-backend smoke (user browser confirm is
  the verify gate).

## Test Obligations

- **Behaviors requiring failing-test-first**: profile list columns render the payload
  (dash on empty); roleNames chips for ANY row (not session-only); edit prefill from
  getUserDetail with 登录账号 disabled; delete Popconfirm → endpoint → reload, backend
  A-class msg surfaces as toast and the row stays; dict-driven selects with raw-code
  fallback; modalitys CSV split/join round-trip; perm gating of 编辑/删除; duplicate
  username stays on-form.
- **Required edge cases**: dict fetch failure degrades without blocking; signature
  extension/size rejection fires NO request; update failure keeps the dialog open.
- **Regression-sensitive areas**: create/reset SM2 flows (untouched), enable switch,
  reset-password drawer, prior console suites (menus/deps/roles/dicts/logs).

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 7 tasks in one module (lumen-web), sequential deps
  (types → clients → control → page → verify), the seam-mock test pattern and all
  hooks/controls already on disk; SDD dispatch is overhead for single-page TDD.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: after each batch (1-3) — diff vs the batch's spec
  scenarios; after 4.1 the full-suite gate; 4.2 needs the user's browser confirmation
  before verify can PASS.
- **Blocking categories**: any spec scenario not evidenced; a test mocking page DATA
  instead of the client seam; an invented perms string or endpoint; red as a primary
  color.

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

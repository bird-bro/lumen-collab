# Design: user-edit-delete-profile-web

## Context

The backend change (`user-edit-delete-profile`) ships the real surface. The page today
(batch 6a + role-type-dict polish) paginates a plain list client-side with the
console-shell chrome, a create drawer, a reset drawer, and the enable switch. The dict
hook (`useDictItems`), the deps clients (`listDeps`), and the logo upload control
pattern (`logo-upload.tsx`) all exist - this change composes them.

## Goals / Non-Goals

Goals: the RCP-shaped list, edit/delete row actions, the shared profile form in both
dialogs, dict-driven selects, the signature upload, perm-gated affordances.

Non-Goals: no avatar, no 设备权限 (the backend does not carry the field), no
server-side paging, no changes to the auth stores/router/password-cipher, no modification
of `logo-upload.tsx` (compose a sibling control, never-modify).

## Decisions

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
- **D4 — Delete affordance**: Popconfirm (the deps page's delete precedent) + the
  console action glyph; a rejection surfaces `message.error` with the backend `msg` and
  the row stays (the spec scenario). No optimistic removal - the delete reloads.
- **D5 — Perms**: `canUpdate = hasPerm('system:user:update')`,
  `canDelete = hasPerm('system:user:delete')` alongside the existing create/reset/enable
  gates; the seeded F nodes (V26) are the server-side pins.
- **D6 — Tests**: `users-page.test.tsx` rewrites at the `vi.mock('@/api/...')` seam -
  the list-payload shape, the edit prefill flow, the delete confirm/reject, the
  dict-fallback, the perm gating. `useDictItems`' session cache means one catalog per
  session (the role-type-dict pattern).

## Risks / Trade-offs

- [Drawer form complexity (11 fields)] → Single-page form (the field-range ruling
  rejected the RCP two-tab split); AntD grid keeps it scannable.
- [dict catalogs missing on a fresh deployment] → The raw-code fallback (D4 of
  role-type-dict-web) never blocks the page; the catalogs seed with V26.

## Open Questions

(none - the surface is pinned by the backend change's shipped API and the spec delta.)

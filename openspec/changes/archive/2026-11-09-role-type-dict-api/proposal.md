# Proposal: role-type-dict-api

## Why

The role classification (`role_info.type`) is currently a fixed two-value
vocabulary (`RoleConstants`: 10 = `TYPE_GLOBAL` 系统角色, 11 = `TYPE_RCP` RCP角色)
whose display labels are hardcoded in the frontend map `ROLE_TYPE_LABELS`. The
user ruled (2026-11-09) that the type vocabulary belongs in the data dictionary —
"后面可能会有多种分类" — so operations can add categories in 字典管理 with zero
code change. The platform's dict model (`dict_detail.code` numeric + `value`
single text, V9/V12) already carries the right shape; what is missing is a seed
catalog for the role types and a lookup endpoint the role console can call (the
existing `GET /dicts/detail/list?caId=` is gated `system:dict:list` and
id-addressed — role managers would get 403, and the frontend cannot resolve a
stable catalog id).

This is the backend half of the cross-stack feature `role-type-dict`
(coordination: `openspec/coordination/role-type-dict.md`, decisions D1-D6). The
frontend half (`role-type-dict-web`) consumes the endpoint built here — backend
first, no mock layer (user ruling 2026-11-05).

## What Changes

- **V23 seed migration**: one dict catalog 「角色类型」 (id 1020, ON CONFLICT
  guarded) with two details mirroring `RoleConstants` — code 10 = 系统角色
  (id 1020001), code 11 = RCP角色 (id 1020002).
- **New read-only endpoint** `GET /api/v1/system/dicts/items/{catalogName}`:
  returns the ENABLED catalog's live details as slim `{code, value}[]` sorted by
  `sort` asc. Anchored by the catalog's unique `name`. Unknown catalog name →
  empty array (never an error — the frontend falls back to the raw code).
- **Ungated**: the endpoint requires authentication but NO permission string —
  same class as the city reference queries (auth-rbac). Role managers hold only
  `system:role:list`; the dict page gate would 403 them.
- **Role enable field (user addition, same review session — coordination D7)**:
  V24 migration adds `role_info.enable integer NOT NULL DEFAULT 1`; the toggle
  endpoint `PUT /api/v1/system/roles/{id}/enable` (body `{"enable": 0|1}`,
  strict rejection otherwise) mirrors the org-status-toggle precedent with the
  `system:role:update` authority; `roles/list` rows carry `enable`. Behavioral
  consequences of a disabled role (perm recalc, login rejection) are OUT of
  scope — store/display/toggle only.
- **NOT changed**: `roles/list` keeps returning the raw int `type` (no server-side
  dict join, D3); role create/update keep accepting the int without dict-membership
  validation (D5 non-goal); zero edits to `common`/`framework`.

## Capabilities

### New Capabilities

(none — the endpoints, seed and column extend existing capabilities)

### Modified Capabilities

- `api`: new dict lookup endpoint `GET /api/v1/system/dicts/items/{catalogName}`
  added to the API contract; the Role Management Endpoints requirement gains the
  `enable` field on list rows and the `PUT /{id}/enable` toggle.
- `system-data`: the dict seed gains the 「角色类型」 catalog + its two items.
- `data`: `role_info` gains the `enable` column (V24, default 1).
- `auth-rbac`: the ungated (authenticated-only) endpoint class grows to include
  the dict items lookup, mirroring the city reference precedent.

# Proposal: role-type-dict-web

## Why

The roles console hardcodes the role-type vocabulary in
`ROLE_TYPE_LABELS = {10: '系统角色', 11: 'RCP角色'}` — a compile-time map that
breaks the moment operations add a category in 字典管理. The user ruled
(2026-11-09) the type vocabulary belongs to the data dictionary. The backend half
(`role-type-dict-api`, built first per the no-mock ruling) ships the
`GET /api/v1/system/dicts/items/{catalogName}` lookup and the V23 seed; this
change consumes it so the 类型 column and the create/edit drawer render whatever
the dictionary currently defines — zero code change for new categories.

Cross-stack feature `role-type-dict`; coordination:
`openspec/coordination/role-type-dict.md` (decisions D1-D6, D3/D4 are this
side's constraints).

## What Changes

- **New typed client** `dictItemsByName(name)` in `src/api/dicts.ts` — calls the
  by-name endpoint, returns `{code, value}[]` (slim `DictItem` type added to
  `src/api/types.ts`).
- **Session-cached resolver** `src/utils/dict-items.ts` (the `password-cipher.ts`
  shape: module-level cache, one wire call per catalog name per session) exposing
  `dictItemsByName` + `resolveDictLabel(items, code)` (fallback = `String(code)`).
- **类型 column** (roles page): label resolved from the 「角色类型」 dict at list
  load, raw-code fallback when the dict has no entry or the fetch fails (one
  `message.error`, page never blocks). Tag chrome unchanged (blue 系统角色 / cyan
  other codes per the 2026-11-09 ruling — the 10/11 special-casing generalizes:
  code 10 blue, everything else cyan).
- **Drawer type field**: the numeric 类型 Input in the create/edit drawers
  becomes a dict-driven Select (option value = `code`, label = `value`,
  initialValue 10 kept). On dict-fetch failure the Select renders empty and the
  submission path is unchanged (backend accepts the int as-is, D5).
- **状态 switch column (user addition, same review session — coordination D7)**:
  `RoleItem` gains `enable: number`; the roles table renders a centered 状态
  column (deps/orgs switch chrome, aria-label 启用 {name}) whose toggle calls
  `PUT /api/v1/system/roles/{id}/enable` (mirroring the orgs status switch);
  failure reverts the switch and surfaces the backend message. Create defaults
  to enabled — no drawer field.
- **NOT changed**: the wire `type` stays a raw int (D3); the menu-grant tree; any
  other page.

## Capabilities

### New Capabilities

(none — extends the existing console capability)

### Modified Capabilities

- `web-system-console`: the role management section gains the dict-driven type
  rendering (column label + drawer Select + fallback behavior) and the enable
  switch column wired to the toggle endpoint.

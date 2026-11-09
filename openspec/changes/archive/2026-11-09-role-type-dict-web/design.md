# Design: role-type-dict-web

## Context

The roles page currently resolves `RoleItem.type` through
`ROLE_TYPE_LABELS: Record<number, string>` (2026-11-09 interim: 10 系统角色 /
11 RCP角色) and the drawers take a numeric `<Input type="number">` for 类型.
The backend feature `role-type-dict-api` ships the real lookup
(`GET /api/v1/system/dicts/items/{catalogName}` → slim `{code, value}[]`, V23
seed). Frontend conventions: no `src/hooks/` dir — shared cached helpers live in
`src/utils/` (`password-cipher.ts` precedent: module-level session cache);
pages fetch via typed clients in `src/api/` and keep results in component
state. The build runs against the REAL backend through the Vite proxy — no mock
layer (user ruling 2026-11-05); unit tests stub the API client at the seam.

## Goals / Non-Goals

**Goals:**
- One session-cached dict resolver (`src/utils/dict-items.ts`) usable by later
  dict-driven pages
- 类型 column + drawer Select render the dict vocabulary, raw-code fallback
- TDD at the established seam (`vi.mock('@/api/dicts')`), 264+ suite stays green

**Non-Goals:**
- No change to `RoleItem` / wire contracts (D3)
- No generic "dict provider" context/store — a module cache is enough for the
  first consumer; promote when a second pattern emerges
- No menu-grant or other-page changes

## Decisions

- **Resolver shape**: `src/utils/dict-items.ts` —
  `dictItemsByName(name): Promise<DictItem[]>` with a module `Map<string,
  Promise<DictItem[]>>` cache (dedupes concurrent calls, survives route
  remounts within the session, mirrors `password-cipher.ts`'s cached key
  fetch), and `resolveDictLabel(items, code): string` → dict `value` ?? `
  ${code}`. Failed fetches are NOT cached (next mount retries); success is.
- **Catalog name constant**: `'角色类型'` lives in the roles page (the only
  consumer today) — promoting it to a shared constants module waits for the
  second consumer (recorded in the coordination doc as the promotion trigger).
- **Column render**: the roles page holds `dictItems` in state, loaded in the
  same effect as the first list fetch (Promise.all with `listRoles`; dict
  failure → `message.error` once, `dictItems` stays empty → `resolveDictLabel`
  falls back per code). Tag chrome: `code === 10` blue / otherwise cyan —
  generalizes the 2026-11-09 two-value styling without a per-code map.
- **Drawer Select**: shared between create/edit drawers via the same `dictItems`
  state; `<Select options={dictItems.map(i => ({value: i.code, label: i.value}))}`
  — codes are numbers on the wire, so no string coercion; initialValue 10
  preserved on create. While `dictItems` is empty (loading/failure) the Select
  renders with no options; the form still submits the int (D5 soft constraint).
- **`RoleItem.type` tag render stays typed** on `number | null`; the dict label
  lookup is a pure function over the fetched items — trivially unit-testable.
- **Enable switch (D7)**: `RoleItem` gains `enable: number`; the 状态 column
  (width ~90, centered) renders AntD `Switch` per the deps/orgs precedent
  (aria-label 启用 {name}), toggling calls the new `toggleRoleEnable(id,
  enable)` client (`PUT /roles/{id}/enable`) and re-lists on success — no
  optimistic update (failure reverts naturally because state refetches); create
  drawer gains NO field (column default 1 = born enabled). The dict-column and
  switch are independent concerns sharing only the page state.

## Risks / Trade-offs

- **Dict round-trip on every cold session**: one extra request per session per
  catalog — accepted; the cache bounds it.
- **Stale cache after dict edits**: a category added mid-session appears on the
  next full reload (session cache is not invalidated) — accepted for a
  low-frequency administrative vocabulary; the coordination doc records the
  invalidation trigger as future work if it bites.
- **Renaming the catalog breaks lookup**: bounded by the raw-code fallback (api
  D2); the name is a recorded cross-stack contract.

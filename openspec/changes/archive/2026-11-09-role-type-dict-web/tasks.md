# Tasks: role-type-dict-web

> TDD house rule: red test first at the `vi.mock('@/api/dicts')` seam, then
> implementation. Prerequisite: `role-type-dict-api` endpoint live on the
> backend (no mock layer in dev — user ruling 2026-11-05).

## 1. Client + resolver (TDD)

- [x] 1.1 RED: `dicts-api.test.ts` — `dictItemsByName('角色类型')` hits
      `GET /api/v1/system/dicts/items/角色类型`, unwraps the envelope to
      `{code, value}[]`; a second same-name call within the session does NOT
      re-fetch (module cache); a failed call is not cached (retry on next call).
- [x] 1.2 GREEN: `DictItem` type in `src/api/types.ts`; `dictItemsByName` in
      `src/api/dicts.ts`; `src/utils/dict-items.ts` resolver
      (`resolveDictLabel(items, code)` → dict value ?? `String(code)`).

## 2. Roles page wiring (TDD)

- [x] 2.1 RED: `roles-page.test.tsx` — with the dict mocked
      (10 → 系统角色, 11 → RCP角色): the 类型 cell renders the dict label
      (blue for 10, cyan for others); a row with an unknown code renders the raw
      number; dict-fetch failure surfaces one message and renders raw codes.
- [x] 2.2 GREEN: load `dictItems` alongside the first list fetch
      (`Promise.all`), resolve labels in the 类型 column render; generalize the
      tag chrome to code 10 blue / others cyan; delete the hardcoded
      `ROLE_TYPE_LABELS` map.
- [x] 2.3 RED: drawer tests — create drawer 类型 is a Select listing the dict
      items with 系统角色 preselected; edit drawer reflects the row code;
      submitting posts the numeric code unchanged.
- [x] 2.4 GREEN: replace the two numeric 类型 Inputs with the dict-driven
      Select (shared `dictItems` state, initialValue 10 kept on create).

## 3. Enable switch column (TDD, D7 — after the api toggle endpoint is live)

- [x] 3.1 RED: `roles-page.test.tsx` — rows render the 状态 switch reflecting
      `enable` (on/off, aria-label 启用 {name}); toggling calls
      `toggleRoleEnable(id, 1|0)` and re-lists; a rejected toggle surfaces the
      backend message and the switch state reverts (re-list restores).
- [x] 3.2 GREEN: `RoleItem.enable` in `src/api/types.ts`; `toggleRoleEnable`
      client in `src/api/roles.ts`; centered 状态 column (deps/orgs switch
      chrome) inserted after 类型; no drawer field.

## 4. Regression + verify

- [x] 4.1 Full suite green (`pnpm test`), `tsc --noEmit` clean, `pnpm build`
      clean; smoke against the REAL backend (`pnpm dev` + proxy): column labels,
      drawer options, and the switch round-trip against the V23/V24 backend.
- [x] 4.2 `/opsx:verify` three layers; record evidence in verify.md.

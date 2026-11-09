# role-type-dict — Coordination (Cross-Stack Feature)

> Feature: maintain the role classification (`role_info.type`) through the data
> dictionary instead of the frontend-hardcoded `ROLE_TYPE_LABELS` map, so new
> categories (beyond 10 系统角色 / 11 RCP角色) are added in 字典管理 with zero
> code change. Origin: user review 2026-11-09 — "角色类型应该数据字典中维护,
> 后面可能会有多种分类". The project's dict model (V9/V12: `dict_detail.code`
> numeric + `value` single text, catalog anchored by id/name) already carries
> exactly this shape; this feature makes role management its FIRST consumer and
> establishes the dict-driven-label pattern for later pages.

## Change Registry

| Change | Stack | Scope |
|:--|:--|:--|
| `role-type-dict-api` | lumen-sev | V23 seed (catalog 角色类型 + 2 items) + read-only by-name dict items endpoint (ungated, city-dict precedent) + V24 `role_info.enable` column + toggle endpoint (org-status-toggle mirror) |
| `role-type-dict-web` | lumen-web | `dictItemsByName` client + session-cached `useDictItems` hook; 类型 column label from dict (raw-code fallback); drawer type field → dict-driven Select; 状态 switch column → toggle endpoint |

**Gate**: SATISFIED 2026-11-09 — both changes verify PASS (api: 10/10 scenarios,
145/145 tests; web: 10/10, 279/279) and are archived as
`2026-11-09-role-type-dict-{api,web}`; specs synced into api / system-data / data /
auth-rbac / web-system-console (sync markers added). Git commit is a separate track.
Outstanding user-side: the browser UI smoke (类型列字典标签 / 抽屉 Select / 状态开关),
recorded as a process note in both verify.md credentials. Backend first; the frontend
built against the real endpoint (no mock layer, user ruling 2026-11-05). Session
additions beyond the original proposal: D7 (role enable field + 状态 switch) and the
remark-textarea 130px pin (out-of-band user ruling, recorded in the web verify.md).

## Shared Design (decisions 2026-11-09; user ruled the direction and approved
proceeding on the recommendations below)

- **D1 — Dict catalog "角色类型" is the single source of the type vocabulary**
  (user ruling). Seed (V23): one catalog + two items mirroring the copied
  `RoleConstants` lineage — code **10 = 系统角色**, code **11 = RCP角色**
  (`common/Constants/RoleConstants.java`: `TYPE_GLOBAL=10`, `TYPE_RCP=11`; the
  earlier frontend gloss "业务角色" for 11 was wrong and is corrected here).
  Later categories are operator-managed rows in 字典管理 — no code change.
- **D2 — By-name items endpoint** (recommended, user proceeded): `GET
  /api/v1/system/dicts/items/{catalogName}` → slim `{code, value}[]` of the
  ENABLED catalog sorted by `sort`, **ungated**. Rationale: the consumers are
  role managers holding only `system:role:list` — the dict page gate
  `system:dict:list` (DictDetailController class gate) would 403 them; a
  read-only classification lookup is non-sensitive, matching the city-dict
  precedent (auth-rbac: "login/me and city stay ungated"). Unknown catalog name
  → empty array (NOT an error): the frontend falls back to the raw code, so a
  mis-named catalog can never break a page.
- **D3 — No server-side join into RoleItemDto** (recommended): `roles/list`
  keeps returning the raw int `type` — the role-list-api DTO shape stays stable,
  no N+1 dict join per row. Label resolution is a frontend concern (D4).
- **D4 — Frontend hook + dict-driven Select** (recommended): `dictItemsByName`
  typed client + a `useDictItems(name)` hook with session-level cache (one wire
  call per catalog name per session); 类型 column label = dict `value` matched
  by `code`, falling back to `String(type)` when the dict has no entry or the
  fetch fails; the create/edit drawer's numeric 类型 input becomes a Select
  (option value = `code`, label = `value`, initialValue 10 kept). A failed fetch
  surfaces one message.error and degrades to raw codes — never blocks the page.
- **D5 — No backend type-∈-dict validation** (non-goal): create/update keep
  accepting the int as-is. A strict join would lock role creation whenever the
  dict catalog is missing/disabled; the coupling stays a soft constraint
  enforced by the frontend Select. The V1 `role_info.type DEFAULT 11` column
  quirk is left untouched (frontend initialValue 10 is the operative default).
- **D6 — Deterministic V23 seed ids** (V6 house style): catalog id **1020**
  "角色类型" (clear of V6's 1001-1007 range), detail ids **1020001** (code 10) /
  **1020002** (code 11) — clear of V6's +100000 detail offset band (100001+);
  every statement ON CONFLICT-guarded so a re-run is a no-op.
- **D7 — Role enable field** (user addition 2026-11-09, same review session):
  `role_info` gains `enable integer NOT NULL DEFAULT 1` (V24 migration, backfill
  implicit via default; dicts already use the `enable` name, so the column
  follows the dict vocabulary rather than orgs' `status`). Toggle endpoint
  `PUT /api/v1/system/roles/{id}/enable` with body `{"enable": 0|1}` mirrors the
  org-status-toggle precedent exactly (orgs: `PUT /orgs/{id}/status`, strict 0/1
  rejection, existing `system:role:update` authority, `data: true`).
  `roles/list` rows carry `enable`; the console renders a 状态 Switch column
  (deps/orgs chrome: centered, aria-label 启用 {name}). Create defaults to
  enabled — no drawer field (vocabulary: a role is born live; disable is an
  operational act on existing rows). **Behavioral consequences of disabling**
  (session perm recalculation, login rejection of disabled-role users) are
  explicitly OUT of scope — the field is stored/displayed/toggleable only, and
  consumers of the flag are future work; recorded here so the boundary is
  deliberate.

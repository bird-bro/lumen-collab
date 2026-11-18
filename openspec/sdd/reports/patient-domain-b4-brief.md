# patient-domain Batch 4 Brief — tasks 4.1–4.4 (API surface, perms, menu seeds, full suite)

Change: `openspec/changes/patient-domain/`. Tasks source: `tasks.md` lines 25–28.
Prereqs: Batches 1–3 landed. FIRST read the b1–b3 reports, then TRUST THE LANDED CODE. Task 5 (web) is a separate gated phase — out of scope here.

- **4.1** Controllers: `POST /patients/resolve`, `POST /patients`, `PUT /patients/{id}`, `GET /patients/{id}`, `GET /patients` (multi-number + name/pinyin search), `POST /patients/{id}/resolve-or-merge`, `GET/POST/PUT/DELETE /patient-number-rules` under `/api/v1/patient/**` with the A/B/C error-code family and HTTP-200 envelope. Verify: endpoint tests (WebMvc layer) for each route + conflict/perm rejection payloads.
- **4.2** Perms enforcement: rule CRUD family `patient:rule:list/create/update/delete`; archive endpoints per the auth-rbac pattern. Verify: tests assert 403-equivalent rejection without the perm.
- **4.3** Menu-seed migration in the patient ledger (cross-schema writes into `system.menu_info`/`role_menu`, V22/V32 CONTENT-guard discipline): L2 「患者配置」 group (type M, NULL perms, sort 3, family-resident icon), 号码规则 page (path `number-rule`, component `patient/number-rule/index`, perms `patient:rule:list`), F buttons 新建/编辑/删除 (`patient:rule:{create,update,delete}`), admin auto-grant, ancestor-chain completion, sequence convergence. Verify: seed tests — fresh-seed shape, non-admin grant chain survival, re-execution no-op.
- **4.4** Full backend suite `./mvnw test -q` from `lumen-sev/` (JDK 11) — exit 0 + counts; fix cross-module fallout before proceeding.

## Coordinator-pinned binding decisions (apply, do not re-litigate)

1. **URL prefix:** the service owns the FULL path (nginx forwards unstripped — lumen-system precedent `/api/v1/system/**`): `PatientController` maps `/api/v1/patient/patients` (+ `/resolve`, `/{id}`, `/{id}/resolve-or-merge`), `PatientNumberRuleController` maps `/api/v1/patient/patient-number-rules`. `spring.application.name=lumen-patient`, port 8082 (already in b1 yml).
2. **HTTP-200 envelope:** the copied `GlobalResponseAdvice`/`GlobalExceptionHandler` machinery does it — controllers return plain objects/R; errors are `BusinessException` through `Assert` (A0715 conflict payload names the duplicate; A0400 malformed; house B-codes per b3).
3. **Perms mechanism:** mirror lumen-system's controller permission annotations EXACTLY (read `OrgRoomController`/`RoleController` + `AuthPermEnforcementTests` for the house pattern — authority string + rejection envelope). Rule CRUD: list=`patient:rule:list`, create=`patient:rule:create`, update=`patient:rule:update`, delete=`patient:rule:delete`. Rejection without the perm = the A0301 envelope per the errors spec's Permission Denied Response.
4. **Archive endpoints are authentication-only in v1** (auth-rbac b4 recorded precedent: authority gating deferred until menu nodes exist): tokenless → the "203" envelope; any authenticated user passes. The tests assert BOTH. Do not invent archive perm strings; do not seed archive menu nodes.
5. **Rule CRUD service:** NEW interface `PatientNumberRuleService` + `impl/` (distinct from b2's issuance `PatientNumberService`). Semantics: create/update with `active=true` when another active rule exists for the same type → A0714 conflict (detail names type + existing rule); delete on the ACTIVE rule → REJECTED A0400 detail 启用的规则不可删除，请先停用 (b1 decision 5 — inactive rules soft-delete via house logic delete); `serial_cur` is runtime state — NEVER settable through CRUD DTOs; `stamp` optional (null + isStamp=1 → default `yyMMdd`); validation: `serialLength` 1–10, prefix/suffix ≤8 chars of `[A-Za-z0-9]` — violations → A0400 (the web surfaces them on the field in task 5).
6. **Menu seed = `V2__patient_menu.sql` in the PATIENT ledger** (V1 taken): every statement CONTENT-guarded (ids are data, perms/titles are the contract — V32 shape), explicit `system.` qualification (patient's default-schema is patient), L2 「患者配置」 type M NULL perms sort 3 icon **`team`** (family-resident glyph — `UserAccountIcon` already mapped in the console's MENU_ICON_BY_NAME, zero frontend change) under the L1 设置 row (content-addressed: up_id 0, type M, title 设置); C 号码规则 (path `number-rule`, component `patient/number-rule/index`, perms `patient:rule:list`, sort 1) under the group; F 新建/编辑/删除 (perms `patient:rule:create/update/delete`) under the C page; grant carry-over step: any non-admin role holding the page or a button gets the group ancestor (V32 step-2 shape — defensive for future grants); admin auto-grant (role 1, NOT-EXISTS) for group+page+buttons; setval tails on `system.menu_info` + `system.role_menu` sequences; comment lines carry NO semicolons (re-execution statement-splitting footgun); NULL-safe guards use IS DISTINCT FROM where a column may be NULL (recorded footgun).
7. **Production grant note:** append a commented GRANT block to `docs/deployment/pg-init.sql` for `lumen_patient_role` on `system.menu_info`/`system.role_menu` (+ sequences) — the seed runs from the patient ledger cross-schema in production; documentation-only, mirrors the file's existing commented role blocks.
8. **Authenticated HTTP tests in the patient context:** lumen-system is NOT running — mint tokens with the context's framework `TokenService` (same `token.secret` + shared Redis writes the login state the JWT filter validates). Mirror the house run-identity pattern: read `AuthIntegrationTestBase.ensureRunAdminIdentity`/`sweepRunResidue` and adapt — JDBC-clone the seeded admin into a run-scoped `itadmin_<ts>` account in `system.user_info` (broad test creds can cross schemas), mint the run token for that identity, sweep clone + patient fixtures hard-DELETE in `@AfterAll`. Patient API tests use the run token, never the real admin.
9. **Endpoint tests** (house pattern — read `AuthDeviceApiTests`/`AuthPermEnforcementTests` for the client mechanism): per-route happy path (create archive → returned id+pat_id; resolve chain branches; rule CRUD round-trip), A0715 conflict payload, A0301 without rule perms, 203 tokenless on an archive endpoint, 203 + A0301 on rule endpoints. WebMvc layer = the real security chain, not stubbed auth.
10. **Full suite (4.4):** `./mvnw test -q` from `lumen-sev/` — expect green everywhere; investigate ANY failure outside lumen-patient before marking 4.4 done (fix or report — no blanket skipping). The known shared-server flake family (admin-login contention) may flake — re-run once before diagnosing.
11. **Pinyin search contract (b3-review handoff):** b3 landed Hutool `PinyinUtil` output VERBATIM (space-joined, e.g. `zhang san`) — b3 report flags the search-contract call. Ruling: the helper's output is NORMALIZED to space-stripped lowercase (`zhangsan`) at generation (one-line change riding b4; adjust the b3 pinyin assertions to the normalized form — coordinator-sanctioned), so the page search is a plain `pinyin LIKE 'prefix%'` on the normalized column + name contains. No double-side normalization machinery.

## Test plan (RED → GREEN per test class)

`PatientApiTests` (4.1): every route's happy path + the conflict payload (4.1 verify line).
`PatientPermTests` (4.2): rule CRUD without the perm → A0301 (all four verbs), with perm → 200; archive endpoints tokenless → 203 envelope, authenticated-any-user → 200.
`PatientMenuSeedTests` (4.3): fresh-seed shape (group/page/buttons rows + admin grants present); synthetic non-admin role granted the page binding + migration re-run → ancestor chain completed; re-execution no-op (row counts stable) + sequence convergence (setval past MAX(id)). Mirror `AuthExamSettingSeedTests`/`AuthWorkstationSeedTests` re-execution mechanics (statement-splitting re-run of the migration SQL).

## References

1. b1–b3 reports + landed patient code
2. Controller + perms pattern: `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/manage/controller/OrgRoomController.java` (or RoleController) + `AuthPermEnforcementTests`
3. API test mechanics: `AuthDeviceApiTests` + `AuthIntegrationTestBase` (run identity + sweep)
4. Menu seed precedents: `V32__menu_settings_groups.sql` (primary), `V22__workstation_w_nodes.sql`, seed tests `AuthExamSettingSeedTests`/`AuthWorkstationSeedTests`
5. Errors spec: `openspec/specs/errors/spec.md` (A0301 Permission Denied Response, HTTP-200 envelope)

## Watch items

- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`; reactor-only builds.
- Stage ONLY your files by explicit path — the tree carries unrelated parallel-change residue. Never `git add -A`.
- NO framework/common/lumen-system code edits; the ONLY openspec-adjacent edit is `docs/deployment/pg-init.sql` (decision 7). No tasks.md/loop-state changes (coordinator owns those).
- Do NOT touch the frontend (lumen-web) — task 5 is a gated separate phase.
- The menu seed writes CROSS-SCHEMA into `system.*` — that is the ruled design (D5); the seed tests run with broad test creds. Never qualify `patient.` tables — the ledger's default-schema handles them.

## Verify + report

- L1/L3: per-class `./mvnw -pl lumen-patient -am test -q` during development; then task 4.4 full `./mvnw test -q` — log to file; report exit code + counts + log path.
- Report: `openspec/sdd/reports/patient-domain-b4-report.md` (<80 lines): implemented, RED/GREEN evidence, files changed, the perms-mechanism finding (which house annotation), deviations, concerns. Status line.
- Commit: `✨ feat(sev): patient-domain b4 — /api/v1/patient 控制器+权限族+患者配置菜单种子（V22/V32 纪律·跨 schema）+ pg-init 授权注记 + 全量回归`

# Tasks

## 1. Backend module scaffold (lumen-patient)

- [x] 1.1 Create `lumen-sev/lumen-patient` module: pom (aggregator-imports-BOM, deps on framework/common), declare it in the `lumen-sev` aggregator, app class `PatientApplication` at `org.birdbro.lumen.patient` with `scanBasePackages="org.birdbro"` + wildcard mapper scan, `application.yml` with `spring.application.name=lumen-patient`, port 8082, `patient` schema Flyway config — verify `./mvnw compile -q` from `lumen-sev/` exits 0 with the new module in the reactor
- [x] 1.2 Add the 8082 row to the port table in `openspec/coordination/init-project-skeleton.md` — verify the table lists patient
- [x] 1.3 Write Flyway `V1__patient_domain.sql` in the patient ledger: `patient_master` (id PK, pat_id, demographics incl. server pinyin, archive_status, merged_into, oid, audit cols), `patient_identifier`, `patient_number_rule` (rule_type/prefix/suffix/serial_length/serial_cur/is_stamp/stamp + active flag), `patient_event_outbox` (append-only, status PENDING), `patient_merge_log`, the partial unique index `(id_type, id_no) WHERE id_no IS NOT NULL AND merged_into IS NULL`, and the default `pat_id` rule seed (yymmdd stamp + 4-digit serial) — verify by running the data-layer test that boots the ledger against the test-server PG and asserting the five tables, the index, and the seed row exist

## 2. Entities, mappers, and issuance (TDD)

- [x] 2.1 Entities + mappers for the five tables (`@Builder` paired with `@NoArgsConstructor`/`@AllArgsConstructor` per house convention; no physical FKs) with mapper integration tests against the test-server PG (insert/select/soft state transitions) — verify mapper tests pass in the reactor run
- [x] 2.2 Number issuance service (TDD): rule resolution (active per rule_type), `FOR UPDATE` row lock + `serial_cur` increment, stamp-day reset branch, number formatting (prefix+stamp+serial+suffix) — verify with the concurrency test (multi-thread issuance yields distinct serials, unique pat_id) and the day-reset unit test
- [x] 2.3 Issuance-then-failure gap behavior: archive creation that fails after issuance burns the number — verify a test asserts no reuse of a burned serial

## 3. Archive, resolution, merge, outbox services (TDD)

- [x] 3.1 Archive service (TDD): create (issues pat_id + server pinyin + optional PROVISIONAL), update demographics (writes PATIENT_CHANGED outbox row), fetch by id, default list excluding merged rows — verify service tests cover each path
- [x] 3.2 Identity uniqueness (TDD): duplicate id_no on live archives rejected as conflict; card_no non-unique; merged-row id_no does not block creation — verify tests assert the conflict error payload and the two soft cases
- [x] 3.3 Resolve chain (TDD): id_no exact → card_no → soft candidates (name+phone, name+gender+birth) → create-new (provisional/formal flag); soft-match response returns a candidate list, not an error — verify a test per chain branch
- [x] 3.4 Resolve-or-merge (TDD): minimal merge sets merged_into + survivor RESOLVED + merge_log row + PATIENT_MERGED outbox row; idempotent re-merge returns the same survivor without duplicate rows — verify tests assert idempotence and the outbox payload naming both ids
- [x] 3.5 Outbox append-only guarantee (TDD): no code path deletes or updates outbox rows; rows stay PENDING with no broker configured — verify a test asserts PENDING accumulation and flows succeed without MQ

## 4. API surface, perms, and menu seeds

- [x] 4.1 Controllers: `POST /patients/resolve`, `POST /patients`, `PUT /patients/{id}`, `GET /patients/{id}`, `GET /patients` (multi-number + name/pinyin search), `POST /patients/{id}/resolve-or-merge`, `GET/POST/PUT/DELETE /patient-number-rules` under `/api/v1/patient/**` with the A/B/C error-code family and HTTP-200 envelope per the api/errors specs — verify endpoint tests (WebMvc layer) for each route and the conflict/perm rejection payloads
- [x] 4.2 Endpoint perms enforcement for the rule CRUD family (`patient:rule:list/create/update/delete`) and archive endpoints per the auth-rbac pattern — verify tests assert 403-equivalent rejection without the perm
- [x] 4.3 Menu-seed migration in the patient ledger (cross-schema writes into `system.menu_info`/`role_menu`, V22/V32 CONTENT-guard discipline): L2 「患者配置」 group (type M, NULL perms, sort 3, family-resident icon glyph pinned here), 号码规则 page (path `number-rule`, component `patient/number-rule/index`, perms `patient:rule:list`), F buttons 新建/编辑/删除 (`patient:rule:{create,update,delete}`), admin auto-grant, ancestor-chain completion, sequence convergence — verify seed tests: fresh-seed shape, non-admin grant chain survival, re-execution no-op
- [x] 4.4 Run the full backend suite `./mvnw test -q` from `lumen-sev/` (JDK 11) — verify exit 0 and report counts; fix any cross-module fallout before proceeding

## 5. Frontend: number-rule page (after backend API is user-verified)

- [x] 5.1 Console page `src/pages/patient/number-rule/index.tsx` against the REAL backend (`pnpm dev` proxying `VITE_API_ENTRY_URL`, no mock layer): paged rule table (规则类型/前缀/后缀/流水长度/日期戳/当前流水/状态) + perm-gated toolbar; Drawer form (close top-right end placement, maskClosable false) with 前缀/后缀/流水长度/是否带日期戳, 规则类型 fixed 影像号; field-level validation; delete confirm — run `frontend-design` skill first for the page (new page), get user confirmation on the prototype, then implement
- [x] 5.2 Vitest page tests (list rendering, perm-gated toolbar, create/edit drawer submit, validation error surfacing, delete confirm) — verify `pnpm test` passes with the new suite and `pnpm build` exits 0

## Workflow follow-up

- Run `/opsx:verify patient-domain` (L1 build / L2 spec alignment / L3 tests) and record verify.md.
- Archive after all registered changes for the feature are PASS.

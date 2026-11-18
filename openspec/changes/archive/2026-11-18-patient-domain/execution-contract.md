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

- **Change name**: patient-domain
- **Problem to solve**:
All three department workstations (radiology / US / endoscopy) need one shared patient archive, and the US workstation (next in line) cannot start without it: its registration flow's first step is patient resolution/archiving. The reference system's evidence (EIS deep-dive 2026-11-16, `lumen-reference/NOTES-us-workstation.md`) shows what happens without a patient domain: reuse trusts a client-supplied uid with no dedup fallback, card_no carries no uniqueness, the same patient data is physically stored in three tables, and correcting a patient means writing all three - the exact failures the 2026-11-04 patient-domain decisions (#1-#5) were made to prevent. Those decisions are complete but unbuilt; this change lands them.

<!-- AI: summarize the above into 1-2 sentences -->
Land the ruled patient master index domain (2026-11-04 decisions #1-#5) as the `lumen-patient` service: one-person-one-archive master records, conditional-unique identity, the resolve chain, minimal merge, image-number issuance, and the changed/merged event outbox - plus one console page for number-rule management.

- **In scope**:
- `lumen-sev/lumen-patient` module (port 8082, `patient` schema, own Flyway ledger V1: 5 tables + partial unique index + default pat_id rule seed); aggregator declaration + port-table row
- Issuance service (FOR UPDATE row lock, stamp-day reset, burned-number gaps), resolve chain, minimal merge (+merge log), outbox writes (PENDING, append-only, no MQ)
- Archive/rule REST surface under `/api/v1/patient/**` with `patient:*` perms; menu/perms seeds cross-schema into `system.menu_info`/`role_menu` (V22/V32 discipline)
- Console page 号码规则 under 设置 L1「患者配置」 (real backend, no mock layer) + vitest suite

- **Out of scope**:
- MQ producer/consumer (outbox stays PENDING until the consumer change lands)
- Accession (exam number) issuance - belongs to lumen-core
- HIS patientId auto-mapping logic (identifier table carries it; integration change consumes)
- Full manual archive merge of two formal archives (v2)
- Any patient UI beyond the number-rule page (resolution/merge/archive-search UIs live in future workstation changes)
- docker compose / nginx deployment changes

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/patient-domain/spec.md`
12:### Requirement: Patient Master Archive
21:#### Scenario: Creation issues image number and pinyin
28:#### Scenario: Merged archive stays traceable
34:### Requirement: Conditional Unique Identity
43:#### Scenario: Duplicate id_no is rejected
50:#### Scenario: Card numbers are not unique
56:#### Scenario: Merged rows do not block re-issuance
61:### Requirement: Identity Resolution Chain
70:#### Scenario: Exact id_no hit resolves immediately
75:#### Scenario: Soft match returns candidates
81:#### Scenario: No match creates a new archive
87:### Requirement: Provisional and Merge State Machine
97:#### Scenario: Emergency placeholder receives an image number
102:#### Scenario: Minimal merge writes pointer and log
108:#### Scenario: Merge is idempotent
114:#### Scenario: Default search hides merged rows
119:### Requirement: Image Number Issuance
127:#### Scenario: Concurrent issuance does not duplicate
132:#### Scenario: Stamp rule resets by day
137:### Requirement: Patient Change Event Outbox
145:#### Scenario: Correction writes a change event
150:#### Scenario: Merge writes a merged event
155:#### Scenario: No broker is required
160:### Requirement: Number Rule Management API
169:#### Scenario: Rule CRUD enforces perms
174:#### Scenario: Creation resolves the active rule
179:### Requirement: Patient Service Surface
186:#### Scenario: Endpoints are reachable under the unified entry
192:#### Scenario: Service identity is declared
- `specs/system-data/spec.md`
5:### Requirement: Patient Number Rule Menu Pages
16:#### Scenario: Fresh deployment seeds the patient config group
22:#### Scenario: Non-admin grants keep their chain
28:#### Scenario: Re-execution is a no-op
33:### Requirement: Patient Number Rule Button Perms
39:#### Scenario: Admin role receives the button grants
- `specs/web-system-console/spec.md`
5:### Requirement: Number Rule Page Shell
12:#### Scenario: Page loads against the real backend
18:#### Scenario: Toolbar follows button perms
23:### Requirement: Number Rule Form and Delete
31:#### Scenario: Create a stamped rule
38:#### Scenario: Invalid serial length surfaces on the field
43:#### Scenario: Delete requires confirmation


## Design Constraints

- **Architecture/Decisions**:
### D1: Issuance = DB row lock, no Redis
Number issuance takes `SELECT ... FOR UPDATE` on the rule row, increments
`serial_cur`, and writes the number inside the caller's transaction - with the counter
increment committing in its OWN inner transaction (`REQUIRES_NEW`, wording amendment
2026-11-17, b2 review): a failed creation burns the serial, numbers never reused.
Alternatives:
Redis INCR (turns an optional dependency mandatory; counter rebuild after loss) and PG
native sequences (fastest, but cannot day-reset, and stamp rules need per-day reset).
### D2: Event carriage = outbox table, contract-first
`patient_event_outbox` (event_type, patient_id, payload jsonb, status PENDING,
append-only). The consumers are future: `lumen-core` snapshot refresh (PATIENT_CHANGED)
and exam-order repoint (PATIENT_MERGED - the repoint executes in the department
- **Technical constraints**:
- JDK 11 / Boot 2.5.15 / Spring Cloud Alibaba OPS-verified train (no version bumps); PG per-service schema + own Flyway ledger; MyBatis-Plus; Redis optional (issuance MUST NOT require it)
- No Java gateway - unified nginx entry `/api/v1/patient/**`; A/B/C error-code family + HTTP-200 envelope per api/errors specs
- Naming: `org.birdbro.lumen.patient`, business services interface+`impl/`, `@author birdbro`, kebab-case files; entities `@Builder`+`@NoArgsConstructor`+`@AllArgsConstructor`; no physical FKs
- Menu seeds: V22/V28/V32 CONTENT-guard serial-insert discipline, cross-schema `system.menu_info`/`role_menu` writes from the patient ledger, admin auto-grant, ancestor-chain completion, re-execution no-op
- Frontend: React 18 + TS + AntD 5 (pnpm), build against the REAL backend (`VITE_API_ENTRY_URL` proxy), no mock layer; Drawer close top-right end placement + maskClosable false (user ruling 2026-11-13)

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 Create `lumen-sev/lumen-patient` module: pom (aggregator-imports-BOM, deps on framework/common), declare it in the `lumen-sev` aggregator, app class `PatientApplication` at `org.birdbro.lumen.patient` with `scanBasePackages="org.birdbro"` + wildcard mapper scan, `application.yml` with `spring.application.name=lumen-patient`, port 8082, `patient` schema Flyway config — verify `./mvnw compile -q` from `lumen-sev/` exits 0 with the new module in the reactor
6:- [ ] 1.2 Add the 8082 row to the port table in `openspec/coordination/init-project-skeleton.md` — verify the table lists patient
7:- [ ] 1.3 Write Flyway `V1__patient_domain.sql` in the patient ledger: `patient_master` (id PK, pat_id, demographics incl. server pinyin, archive_status, merged_into, oid, audit cols), `patient_identifier`, `patient_number_rule` (rule_type/prefix/suffix/serial_length/serial_cur/is_stamp/stamp + active flag), `patient_event_outbox` (append-only, status PENDING), `patient_merge_log`, the partial unique index `(id_type, id_no) WHERE id_no IS NOT NULL AND merged_into IS NULL`, and the default `pat_id` rule seed (yymmdd stamp + 4-digit serial) — verify by running the data-layer test that boots the ledger against the test-server PG and asserting the five tables, the index, and the seed row exist
11:- [ ] 2.1 Entities + mappers for the five tables (`@Builder` paired with `@NoArgsConstructor`/`@AllArgsConstructor` per house convention; no physical FKs) with mapper integration tests against the test-server PG (insert/select/soft state transitions) — verify mapper tests pass in the reactor run
12:- [ ] 2.2 Number issuance service (TDD): rule resolution (active per rule_type), `FOR UPDATE` row lock + `serial_cur` increment, stamp-day reset branch, number formatting (prefix+stamp+serial+suffix) — verify with the concurrency test (multi-thread issuance yields distinct serials, unique pat_id) and the day-reset unit test
13:- [ ] 2.3 Issuance-then-failure gap behavior: archive creation that fails after issuance burns the number — verify a test asserts no reuse of a burned serial
17:- [ ] 3.1 Archive service (TDD): create (issues pat_id + server pinyin + optional PROVISIONAL), update demographics (writes PATIENT_CHANGED outbox row), fetch by id, default list excluding merged rows — verify service tests cover each path
18:- [ ] 3.2 Identity uniqueness (TDD): duplicate id_no on live archives rejected as conflict; card_no non-unique; merged-row id_no does not block creation — verify tests assert the conflict error payload and the two soft cases
19:- [ ] 3.3 Resolve chain (TDD): id_no exact → card_no → soft candidates (name+phone, name+gender+birth) → create-new (provisional/formal flag); soft-match response returns a candidate list, not an error — verify a test per chain branch
20:- [ ] 3.4 Resolve-or-merge (TDD): minimal merge sets merged_into + survivor RESOLVED + merge_log row + PATIENT_MERGED outbox row; idempotent re-merge returns the same survivor without duplicate rows — verify tests assert idempotence and the outbox payload naming both ids
21:- [ ] 3.5 Outbox append-only guarantee (TDD): no code path deletes or updates outbox rows; rows stay PENDING with no broker configured — verify a test asserts PENDING accumulation and flows succeed without MQ
25:- [ ] 4.1 Controllers: `POST /patients/resolve`, `POST /patients`, `PUT /patients/{id}`, `GET /patients/{id}`, `GET /patients` (multi-number + name/pinyin search), `POST /patients/{id}/resolve-or-merge`, `GET/POST/PUT/DELETE /patient-number-rules` under `/api/v1/patient/**` with the A/B/C error-code family and HTTP-200 envelope per the api/errors specs — verify endpoint tests (WebMvc layer) for each route and the conflict/perm rejection payloads
26:- [ ] 4.2 Endpoint perms enforcement for the rule CRUD family (`patient:rule:list/create/update/delete`) and archive endpoints per the auth-rbac pattern — verify tests assert 403-equivalent rejection without the perm
27:- [ ] 4.3 Menu-seed migration in the patient ledger (cross-schema writes into `system.menu_info`/`role_menu`, V22/V32 CONTENT-guard discipline): L2 「患者配置」 group (type M, NULL perms, sort 3, family-resident icon glyph pinned here), 号码规则 page (path `number-rule`, component `patient/number-rule/index`, perms `patient:rule:list`), F buttons 新建/编辑/删除 (`patient:rule:{create,update,delete}`), admin auto-grant, ancestor-chain completion, sequence convergence — verify seed tests: fresh-seed shape, non-admin grant chain survival, re-execution no-op
28:- [ ] 4.4 Run the full backend suite `./mvnw test -q` from `lumen-sev/` (JDK 11) — verify exit 0 and report counts; fix any cross-module fallout before proceeding
32:- [ ] 5.1 Console page `src/pages/patient/number-rule/index.tsx` against the REAL backend (`pnpm dev` proxying `VITE_API_ENTRY_URL`, no mock layer): paged rule table (规则类型/前缀/后缀/流水长度/日期戳/当前流水/状态) + perm-gated toolbar; Drawer form (close top-right end placement, maskClosable false) with 前缀/后缀/流水长度/是否带日期戳, 规则类型 fixed 影像号; field-level validation; delete confirm — run `frontend-design` skill first for the page (new page), get user confirmation on the prototype, then implement
33:- [ ] 5.2 Vitest page tests (list rendering, perm-gated toolbar, create/edit drawer submit, validation error surfacing, delete confirm) — verify `pnpm test` passes with the new suite and `pnpm build` exits 0

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: concurrent issuance distinct-serial test; stamp-day reset test; duplicate id_no conflict test; resolve-chain branch tests (exact id_no / card_no / soft candidates / create-new); merge idempotence test; outbox append-only + PENDING-without-broker test; menu seed re-execution no-op test; perms rejection tests; number-rule page vitest suites
- **Required edge cases**: unnamed emergency PROVISIONAL placeholder still receives pat_id; merged-row id_no does not block a new archive; first issuance after stamp rollover restarts serial at 1; burned serial never reused; soft-match returns a candidate list (HTTP-200), never an error; rule delete on the active rule (decide: reject or deactivate - assert whichever)
- **Regression-sensitive areas**: partial unique index on fresh + already-migrated DBs; menu seed idempotency against the V28 serial-insert discipline; aggregator build stays green with the new module (no orphan pom); cross-schema seed writes must not disturb the system ledger's existing checksums

## Execution Mode

- **Mode**: `SDD`
- **Selection rationale**: 5 task groups / 18 tasks spanning two stacks (new backend module + console page) with risk indicators - concurrency-sensitive issuance, partial unique index migration, cross-schema menu seeds. SDD coordinator/implementer/reviewer flow per the b4 precedent.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: DP-3 user approval of this contract before apply; frontend prototype user confirmation inside task 5.1 (new-page rule); seed migration review before the full-suite run (task 4.4)
- **Blocking categories**: cross-schema menu seed writes; partial unique index behavior; outbox append-only guarantee

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

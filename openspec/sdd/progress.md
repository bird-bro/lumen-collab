# SDD Progress Ledger

## Change: init-backend-skeleton
## Execution Mode: SDD
## Started: 2026-10-29
## Base: e7d182e (branch: dev)

### Batch 1: Aggregator + lumen-bom (tasks 1.1-1.4)
- Status: Complete + Approved (commit 00cca62; review fixes commit follows)
- Commits: e7d182e..00cca62
- Review: Approved - deviations 1/2 accepted with reproduction evidence; Important-1 (dcm4che repo propagation) fixed in aggregator; Important-2 (reactor-only builds) documented
- Notes: implementer measured 1454 shared depMgmt keys, 0 version mismatch; okhttp 3.14.4-vs-3.14.9 drift avoided by adopting OPS's import order
- Notes: toolchain = lumen-sev/mvnw (wrapper 3.3.4 → Maven 3.9.9) + JDK 11 (/Library/Java/JavaVirtualMachines/jdk-11.jdk); settings.xml sanitized (aliyun mirror only, no credentials)

### Batch 2: OPS framework/common verbatim copy (tasks 2.1-2.3)
- Status: Complete + Approved (commit ab2baa7; review fixes commit follows)
- Commits: 6bd2cf6..ab2baa7
- Review: Approved - byte-faithfulness gate re-verified independently (36=36, 54=54, only recorded adaptations); Important-1 (D5 (a) scope) reconciled; Minor items deferred to B5 (settings URL modernization, dispose.properties mojake noted)
- Notes: deleted classes unreferenced in copied modules; OPS-wide consumers only in uncopied business modules

### Batch 3: lumen-auth shell (tasks 3.1-3.2)
- Status: Complete + Approved after fix (commits 6634be5 + fix eb34dd2)
- Commits: 94ca262..eb34dd2
- Review: Needs fixes -> fixed - Critical: Nacos config neutralization didn't reach bootstrap phase (12 live :8848 attempts); fixed via bootstrap.yml test-profile document, evidence 8848 grep 21->0, red reason unchanged (xss.excludes), surefire 2.95s->0.98s
- Notes: port 8081, scanBasePackages="org.birdbro"; D3 amendment recorded in coordination doc; Batch 4 red-test intelligence: token.* + xss.* properties will be the red

### Batch 4: Zero-infrastructure verification (tasks 4.1-4.3)
- Status: Complete + Approved (commit 2eecd07; review-fix commit follows)
- Commits: eb34dd2..2eecd07
- Review: Approved - gate 4 passed: context loads, zero Nacos/Redis/DB contact, L1+L3 exit 0; all 3 concerns accepted (stub quarantined / @MapperScan recorded as entry-class contract / dummy-URL datasource with explicit initial-size: 0)
- Notes: task 4.1 sequencing note recorded (observed red is the strongest available); framework/common byte-untouched

### Batch 5: Documentation and spec hygiene (tasks 5.1-5.5)
- Status: Complete (commit 8a42961) - final review in progress
- Commits: 2eecd07..8a42961
- Review: Pending (final cross-batch review)
- Notes: 4 drift items found and fixed (registry row, adaptation counts in sev CLAUDE + tasks 2.2, sev AGENTS.md resync, naming.md wording)

---

### Verify remediation (tweak round, tasks 6.1-6.4)
- Status: Complete
- Scope: 13 PARTIAL + 2 deferred-✗ scenarios resolved at the wording level; command portability (MAJOR-1), Redis default-on semantics + opt-out (MAJOR-2 rebuttal + real gap), MetaObjectConfig fill scope (data), SUCCESS("0000") note, coordination ApiEnvelope claim, offline-package risk registered; one more "three adaptations" straggler caught in design Risks.
- Gates: L1 exit 0, L3 exit 0, strict valid; root-invoked dir-build re-proven exit 0

## Summary
- Total tasks: 21 (17 impl + 4 remediation)
- Completed: 21
- Blocked: 0
- Final review: PASS (dimensions filled in contract)

---

# Frontend Change: init-web-skeleton
## Execution Mode: SDD
## Started: 2026-11-02
## Base: 33e39b5 (branch: dev)

### Batch 1-2: Scaffold + pinned toolchain + module layout (tasks 1.1-2.2)
- Status: Complete + Approved (commits 661aca6 + minor-fix 9ab1d0b)
- Commits: 33e39b5..9ab1d0b
- Review: Approved - pin table 10/10 exact (resolved==pinned), lock free of React19/AntD6/Vite8/TS7, alias positive+negative controls reproduced; 4 minors (2 fixed: *.local gitignore, tsconfig.node.json gated into build; 2 noted: pages/.gitkeep form superseded by B5, wildcard-alias equivalence - single `@/*` in both configs proven functionally identical to the five named aliases, reviewer ran positive+negative controls)
- Notes: @types trio exact-pinned; no "type":"module" so __dirname alias is valid

### Batch 3-4: API seam + unified-entry proxy (tasks 3.1-4.3)
- Status: Complete + Approved (commit 3cfacbe)
- Commits: 9ab1d0b..3cfacbe
- Review: Approved - endpoint-free seam re-proven; proxy both rungs physically reproduced (Rung B: request arrived at controlled :9999 listener); TS2322 control judged declaration-level (vacuous in variant A) - queued for first envelope-owning change
- Notes: 5173 occupied by unrelated vite - review ran 5174/strictPort

### Batch 5: Shell, theming, department routing (tasks 5.1-5.5)
- Status: Complete + Approved (commit d00617a)
- Commits: 3cfacbe..d00617a
- Review: Approved - teal HSL(185,81,29%) independently recomputed; laziness proven IN THE EMITTED BUNDLE; placeholders request-free; implementer's "pre-existing" antd-chunk claim DISPROVEN (base build 142.78kB clean, warning new in B5, benign) -> correction addendum in report
- Notes: chunkFileNames traceability + mutation-control-queued recorded in coordination doc (B6)

### Batch 6: Debt and spec hygiene (tasks 6.1-6.4)
- Status: Complete (commit 65b163a)
- Commits: d00617a..65b163a
- Review: folded into final review (Approved)
- Notes: debt entries verified live; registry flipped; B5 addendum appended

---

## Summary (frontend)
- Total tasks: 22
- Completed: 22
- Blocked: 0
- Final review: PASS (Ready for /opsx:verify)

---

# Change: auth-rbac-foundation
## Execution Mode: SDD
## Started: 2026-11-02
## Base: 00c04e1 (branch: dev)

### Preflight rulings (user, 2026-11-02)
- Unauthenticated rejection = envelope code `"203"` (copied AuthenticationEntryPointImpl
  semantic) - auth-rbac delta + tasks 3.1 + proposal re-worded; artifacts_hash refreshed
  (602c782b), strict validate exit 0
- Test-server credentials: user fills `lumen-sev/.env-test.local` themselves (gitignored;
  AUTH_TEST_* names fixed in that file)
- Test-server PG <= 14 -> Flyway 7.14.0 (Boot-managed), no version pin needed
- New-code wiring decisions under design D6's "apply 时核对" mandate:
  WebSecurityCustomizer permits `/api/v1/auth/login`; UserHeaderBridgeFilter derives
  USER-HEADER from the security context (MetaObjectConfig fills create_by); UserDetailsServiceImpl
  ships in lumen-auth; seed admin `admin`/`admin123` + must-change-password flag

### Batch 1: Test-server integration base (tasks 1.1-1.3)
- Status: In progress
- Status: Complete + Approved (commits bb2815e + green-proof fix 9da15e9; minor cleanups in follow-up)
- Review: Approved at HEAD - bb2815e carried three latent context-load blockers (Boot flyway
  check-location, Flyway 7 schemas/default-schema, RANDOM_PORT port assert) fixed by 9da15e9;
  Important-1 (report green evidence) resolved by coordinator addendum; 4 minors (2 fixed,
  docs committed at boundary, 2 test-only nits accepted)
- Notes: green run on real server PG 14.11; Flyway resolves 7.7.3 (Boot 2.5.15 managed, NOT
  7.14.0) - PG14 is WARN-only (decompile-verified), watch-item carried to Batch 5 coordination
  doc; test server credentials supplied by user in-chat and written to gitignored .env-test.local

### Batch 2: Flyway migrations + data layer (tasks 2.1-2.3)
- Status: In progress
- Status: Complete + Approved (commit c94d004; review 0 Critical/Important, 7 minors)
- Review: Approved - DDL/seed compared 1:1 vs brief, setval block verified load-bearing,
  suite independently re-run 14/14 green MVN_EXIT=0
- Notes: global gitignore *.sql (machine-level) was silently hiding migrations -> directory-
  scoped db/migration/.gitignore negation; docs/deployment/pg-init.sql was a prior casualty -
  coordinator committed it (d00fd96) with repo-level negation. Minor watch-items handed to
  Batch 3: password hash must not leak (DTO mapping), deleted=0 on permission joins;
  future explicit-id seed migrations repeat the setval tail; Flyway 7.7.3 x PG14.11 still
  WARN-only (0 ERROR lines in migrated run)

### Batch 3: Login loop (tasks 3.1-3.3)
- Status: In progress
- Status: Complete + Approved (commit fe03c96; review 10/10 PASS, 0 Critical/Important, 7 minors)
- Review: Approved - suite independently re-run 20/20; TokenService UUID-vs-JWT trap caught
  by pair-3 red/green BEFORE commit (createToken return value is the JWT; loginUser.token
  holds the Redis-key UUID); 203-not-404 red shape explained (chain precedes dispatch)
- Notes: minor handoffs to Batch 4 - A0111 service check required + DB partial-unique index
  kept (minor-6); shared BCryptPasswordEncoder bean optional polish (minor-2); to later
  work: me() enable re-check/token revoke (minor-3), servlet-level CORS if cross-origin
  SPA calls login (minor-4), A0400 validation test (minor-5)

### Batch 4: Admin CRUD (tasks 4.1-4.4)
- Status: In progress
- Status: Complete + Approved (commit b72d4de; review 10/10 PASS, 0 Critical/Important, 5 minors)
- Review: Approved - 34/34 independently re-confirmed; role-delete cascade verified as the
  only sound choice (Batch-3 perms SQL has no role join - inert bindings would keep granting);
  envelope discovery recorded (void->"204" branch; R.ofFail drops its data arg; mutations
  return data:true)
- Notes: RECORD AT ARCHIVE/VERIFY (minor-3): "authenticated administrator" enforced as
  authentication-only - authority gating deferred to DataScope/radiology change per task
  scope. Later-batch candidates: level re-derivation on unit re-parent, @NotBlank on update
  DTOs, unit-delete child guard, cycle detection, user list pagination, A0400 validation test

### Batch 5: Documentation & spec hygiene (tasks 5.1-5.3)
- Status: In progress
- Status: Complete (no reviewer dispatched - docs/config only; coordinator verified all five
  annotations + config comment against the diff; mutation control evidence TS2322 exit 2 ->
  clean exit 0; strict validate exit 0)
- Notes: coordination doc carries five new records (bigint flag CLOSED stay-Integer,
  Flyway/PG14.11 watch-item with design-gated pin path, Endpoint Documentation archive-time
  alignment, mutation control RE-RUN PASSED, authority-gating deferral); verify.config.yaml
  backend test comment states the new lumen-auth semantics (command unchanged)

## Summary (auth-rbac-foundation)
- Total tasks: 16
- Completed: 16
- Blocked: 0
- Final review: IN PROGRESS
- Final review: PASS_WITH_ISSUES (final reviewer, 2026-11-02) - three dimensions:
  Completeness PASS_WITH_ISSUES (pg-init role-isolation undemonstrable -> coordination record
  landed), Correctness PASS_WITH_ISSUES (api ID-Serialization drift -> MODIFIED delta added),
  Coherence PASS (zero scope leaks, framework/common diff EMPTY, all deferrals durable);
  pre-pivot residue scrubbed (proposal Testcontainers bullet, design Risk bullet,
  lumen-sev/CLAUDE.md Database + IDs lines); contract Verification Dimensions filled,
  Overall PASS_WITH_ISSUES - no code blockers, remaining items are deployment-time
  demonstrations queued for archive/verify
- Status: Complete; 16/16 tasks; awaiting /opsx:verify then archive decision

---

# Change: rename-auth-to-system
## Execution Mode: SDD
## Started: 2026-11-03
## Base: bc1eae8 (branch: dev)

### Preflight (2026-11-03)
- DP-3 approved; contract hash 680cce0b; guard proposing->applying PASS;
  workflow_mode: full
- Blocking item resolved: parallel-session residue (15 files: entity builder combo,
  hutool/BeanUtil adoption, explicit flyway schemas - all test-green, rulings recorded
  in CLAUDE.md) snapshotted as its own commit 9839652 BEFORE the module rename, per
  user approval (clean-tree git mv)

- Minors folded into Batch 2: (1) AUTH_SCHEMA constant/label/javadoc in bootstrap,
  (2) pre-existing class/filename mismatch AuthUnitCrudTests/AuthDepartmentTests,
  (3) frozen V3 comment stale - checksum discipline forbids edit, noted for
  spec-hygiene (no action)

### Batch 2: Endpoints + wiring + test closure (tasks 3.1-3.4, 4.1-4.5)
- Status: Complete + Approved (commit 33167c3, 24 files +1556/-26; one API-error-free run;
  146 tool uses)
- Review: Approved - 0 Critical / 0 Important / 5 Minor; suite independently re-run 50/0/0
  (real public.log_info/log_sign rows polled); sign-out test proves adaptations (7)+(8)
  end-to-end (200 envelope + type=12 row); @Log 19/19 write endpoints; handoffs 1-4 verified
- RequestContextConfig root cause deepened by review: copied SwaggerConfig @EnableWebMvc
  disables WebMvcAutoConfiguration, silently removing Boot 2.4+'s auto-configured
  RequestContextFilter - the new file restores exactly that bean (added to adaptation (8))
- Minor queue: (1) non-browser UA NPEs async pool at AsyncFactory.java:77 - sign/@Log rows
  silently dropped for scripted clients (copied-machinery defect, future recorded
  adaptation); (2) /logs pageSize uncapped; (3) login try-width double-record edge
  (theoretical); (4) DetailQuery value-vs-label kept as copied
- Parallel-session residue untouched (18 worktree paths intact; relicense f451962 noted)

### Batch 3: Test closure + swagger smoke + docs (tasks 4.1-4.2, 5.1)
- Status: Complete (Batch Inline - coordinator executed)
- Smoke: POST /api/v1/system/login 200+JWT; /v3/api-docs 23 paths all system; old path
  returns the "203" envelope (security chain precedes dispatch - contract's "404" wording
  noted imprecise, intent met); port cleaned
- Guard applying->verifying PASS; phase: verifying

---

# Change: dict-city-reference
## Execution Mode: SDD
## Started: 2026-11-04
## Base: 847790c (branch: dev)

### Preflight (2026-11-04)
- DP-3 approved; contract hash 326cebce; guard proposing->applying PASS;
  workflow_mode: SDD
- Data asset committed with the change: data/rows.sql (3751-row lineage export from
  followup.public.dict_city, 2026-11-03 snapshot; required a dir-local !*.sql negation
  mirroring the db/migration precedent - global gitignore excludes *.sql)

### Batch 1: All tasks (1.1-3.1)
- Status: Complete + Approved (commit 032221c, 17 files; review 0C/0I/3m, coordinator-
  persisted copy); verify.md PASS 3/3; ARCHIVED (2026-11-04-dict-city-reference)
- Review: Approved - seed integrity SEED_IDENTICAL (V4 tuples byte-identical to
  data/rows.sql after terminator normalization, independently diffed); read-only proven
  (2 GETs only, no write path); deviations verified sound (Long id/upId for TreeUtils
  same-key-type; DDL stays integer); independent re-run 65/0/0
- Minor queue carried: lineage-data oddities verbatim (id 820103 pinyin mojibake,
  id 712925 zip/city_code swap - V5+ revision candidates); BaseMapper inherited write
  methods (no HTTP path - spec holds)

### Batch 2: Atomic cascade (tasks 2.1-2.2, 3.1-3.3)
- Status: Implementer DONE (commit 898382c, 111 files +357/-357 all git-mv; reviewer
  dispatched)
- Module/package/entry class renamed (SystemApplication, org.birdbro.lumen.system);
  /api/v1/auth swept to /api/v1/system (9 controllers + permit-all + test URLs); 16
  dict files relocated into the five-layer layout (log write path untouched, 4 javadoc
  lines re-pinned + install protocol run); docs re-pinned (naming.md, CLAUDE.md,
  AGENTS.md mirror, verify.config.yaml, coordination port table, zh proposal); gitignore
  re-pinned + check-ignore verified
- Batch 1 handover 3/3 done (SYSTEM_SCHEMA/[system-test-bootstrap], class-name alignment,
  frozen migrations untouched)
- Gates: L1 clean compile green, L3 full suite MVN_EXIT=0 50/50; residual sweep only 4
  allowed hits (2 frozen migration comments + 2 provenance notes)
- Incident (self-corrected): first commit ff671e1 captured pre-sweep staged content -
  amended to 898382c; reviewer tasked with reflog + completeness verification

---

# Change: system-data
## Execution Mode: SDD
## Started: 2026-11-03
## Base: e00d447 (branch: dev)

### Preflight rulings (user, 2026-11-03)
- Tables land in `public` schema of the shared lumen DB (public = design-level shared
  exception to role confinement); entities UNQUALIFIED via `currentSchema=auth,public`
- No new module: dict copies ADDITIVELY into `framework/dict` (symmetry with
  `framework/log`); admin endpoints live in lumen-auth
- Sign events: login success/failure wiring IN SCOPE (logout already wired by the copied
  LogoutSuccessHandlerImpl - discovered during propose Q&A); dict_city DEFERRED to the
  lumen-patient change

### Batch 1: Migration + pg-init + framework (tasks 1.1-1.2, 2.1-2.2)
- Status: Complete + Approved (commit 88806a7; review-fix cd703cf)
- Commits: e00d447..cd703cf
- Review: Approved - 0 Critical / 1 Important / 4 Minor; suite independently re-run 41/41;
  DDL<->entity 1:1 verified (log_info 26 cols, log_sign 18); zero remaining ice. refs;
  framework untouched beyond 2 entities + 2 XMLs
- Review fix cd703cf: DictDetailServiceImpl enable filter aligned to lumen 1/0 (was OPS
  EnableConstants.TRUE=11; V3 frozen so reverse alignment not viable); red-green test
  dictDetailServiceCollectsSeededLiveDetails; full suite 42/42
- Batch 2 heads-ups: LogSignMapper.xml:40 pre-existing #{req} bug (goes LIVE once sign
  events wire - fix in batch 2, adaptation (7) scope); CatalCreate.enable is Byte
  (lumen Integer adaptation); list-by-caCode needs a NEW mapper/service method (getPage
  filters by caId); DetailQuery value-vs-label filter unpinned by spec (kept as copied)
- Environment trap recorded: `-pl lumen-auth` resolves framework from ~/.m2 - after ANY
  framework change run `./mvnw -pl lumen-bom,framework,common install -DskipTests` first
  (or run the full reactor), or test-compile fails on the stale jar
- RULING: V3 is applied to the shared test DB (checksum-frozen) - NEVER edited again,
  only V4+ appended (matches the flyway-history ruling in project memory)

### Batch 2: Endpoints + wiring + test closure (tasks 3.1-3.4, 4.1-4.5)
- Status: Complete + Approved (commit 33167c3, 24 files +1556/-26; one API-error-free run;
  146 tool uses)
- Review: Approved - 0 Critical / 0 Important / 5 Minor; suite independently re-run 50/0/0
  (real public.log_info/log_sign rows polled); sign-out test proves adaptations (7)+(8)
  end-to-end (200 envelope + type=12 row); @Log 19/19 write endpoints; handoffs 1-4 verified
- RequestContextConfig root cause deepened by review: copied SwaggerConfig @EnableWebMvc
  disables WebMvcAutoConfiguration, silently removing Boot 2.4+'s auto-configured
  RequestContextFilter - the new file restores exactly that bean (added to adaptation (8))
- Minor queue: (1) non-browser UA NPEs async pool at AsyncFactory.java:77 - sign/@Log rows
  silently dropped for scripted clients (copied-machinery defect, future recorded
  adaptation); (2) /logs pageSize uncapped; (3) login try-width double-record edge
  (theoretical); (4) DetailQuery value-vs-label kept as copied
- Parallel-session residue untouched (18 worktree paths intact; relicense f451962 noted)

---

## Change: web-foundation-auth-console
## Execution Mode: SDD
## Started: 2026-11-04
## Base: 9839652 (branch: dev)

> loop-state.yaml 单槽冲突说明:并行会话 rename-auth-to-system 正处于 verifying(10/10),
> 状态槽归它;本变更进度以本台账为准,待 rename 归档后再接管 loop-state。
> 规划完成时同步了并行更名(2026-11-03/04):模块 lumen-auth→lumen-system,端点前缀
> /api/v1/auth/**→/api/v1/system/**(proposal/specs/design/tasks/contract/zh 文档已全部对齐,
> artifacts_hash b0a081d7)。roles/list 缺口已声明(api delta),后端小变更跟进。

### Batch 5: UI Prototype (task 5.1) - COMPLETE, user-confirmed
- Status: Complete + Confirmed (user 2026-11-04, after 12 revision rounds; commit follows)
- Review: prototype delivered via frontend-design skill (subagent w/ Skill tool), then
  12 user-feedback revision rounds each anchored to OPS source facts - (1) three-region
  shell (68px rail/60px header/160px white collapsible sidebar), (2) no page-title blocks
  (compact lists), (3) sidebar renders active-L1 children: L2 dirs=group titles w/ icons,
  L3=flat text-only items, L1 never repeats; icon-only op columns; query-left tool-rows,
  (4) header user area text-left/avatar-right no caret + popover plain logout, (5)
  duotone -> (9) L3 iconless + rail 26px white + OPS fold button (24px icon bottom-right,
  40px collapsed strip w/ vertical text), (10) teal sidebar experiment REVERTED to white,
  (11) rail near-black tech blue #0B2540 (hover #12355C), (12) action primary reverted
  teal->bright blue #3178fc - final palette: single blue family (near-black shell +
  bright blue actions), red alarm-only
- Spec sync during revisions: web-design-system delta - Layout Metrics rewritten
  (three-region + menu display rules + compact lists + 10px content padding + header
  composition scenario + 26px white rail); OPS Palette Tokens -> single blue family
  (trajectory all-OPS-blue -> teal-action -> bright-blue recorded, hash 2876e72a)
- Pending follow-ups recorded: mock menu fixtures restructure to 3-level (batches 5.2-5.4
  brief), backend V2 seed menu restructure + roles/list (backend change), profile org field
- Notes: prototype HTML committed as planning artifacts; 61/61 tests green throughout

### Batch 7: Integration Verification (tasks 7.1-7.3)
- Status: Complete + Approved (report: web-foundation-b7-report.md; commit follows)
- 7.1: frozen-lockfile install + 155/155 + build green (rm -rf denied by safety gate -
  equivalent frozen-lockfile verification used; full fresh-checkout re-check optional at verify)
- 7.2: browser-level Playwright walkthrough (system Chrome channel) **10/10 PASS** -
  auth flow, three-zone metrics (68/60/160 measured), every console page scenario,
  logout + cleared storage; two script-side fixes only (dialog checkbox wait, 取 消
  auto-space), zero product-code changes
- 7.3: design-system scenarios green (unit assertions + browser metrics + fonts.css
  static offline check)
- **CHANGE IMPLEMENTATION COMPLETE: 19/19 tasks.** Next: /opsx:verify L2 -> archive
  confirmation. Backlog handed off: roles/list backend change; V2 seed menu restructure;
  profile org field; user edit/delete endpoints (if wanted); mock fake-token switch;
  woff2 subsetting; dark theme

### Batch 6b: Console Pages II (tasks 6.4-6.7 roles/menus/dicts/logs)
- Status: Complete + Approved (commit follows; implemented in-session by the coordinator
  after the 6a subagent model-resolution failure - TDD kept: 28 RED (missing modules) ->
  GREEN)
- Review: self-reviewed then independently re-verified - 155/155 (3 consecutive green
  runs; one transient flake observed once and gone), build green, hex guard green,
  API smoke on 5179 clean (A0714 x2 + A0400 verbatim, full-replace echo [1,13],
  menus 3-level perms, dicts sort order, logs filters)
- Real fixes landed during the batch: ConsoleTable now forwards onRow (dicts row-select
  was dead); roles/dicts mount double-fetch killed (useRef skip-first); logs query/reset
  explicit reload (page-state no-op); **AntD Tree replaced by nested AntD Checkbox grant
  list** in the assignment dialog - Tree's key stringification + cascade conduction
  broke the full-replace contract (minimal repro recorded, decision in component comment);
  AntD two-char button auto-space assertions
- Notes: role type column shows seed value 10 (prototype checklist item, user-confirmed
  overall); roles/list still mock-served (gap endpoint - page consumes typed client, no
  change needed when backend lands)

### Batch 6a: Console Pages I (tasks 6.1-6.3 orgs/deps/users)
- Status: Complete + Approved (commit follows; two-executor relay: implementer A died of
  API quota mid-smoke, coordinator completed audit/smoke/report in-session after a
  subagent model-resolution failure - work NOT rewritten, 127/127 green at handoff)
- Review: Approved - coordinator audited coverage matrix (all brief items have tests:
  orgs 9 / deps 10 / users 11 incl. multi-root, dup-code/name keep-dialog-open,
  password reset echo, enable out-of-range restore, perms gating); API-level smoke on
  5179 clean (org CRUD, A0712/A0111 verbatim, newPassword echo); one A0400 during smoke
  was the smoke script's own field-name typo (password vs newPassword), frontend client
  matches backend DTO - not a bug; hex guard green, build green, zero new deps
- Notes: users page has NO edit/delete (backend endpoints don't exist - capability gap
  recorded for a future backend change alongside roles/list); console-shell.tsx +
  org-tree.tsx shared components documented for 6b reuse; browser-level walkthrough
  deferred to Batch 7.2

### Batch 5b: Auth Flow UI (tasks 5.2-5.4 + mock fixture restructure)
- Status: Complete + Approved (commit follows this line's next shared commit)
- Review: Approved - coordinator independently re-ran test (91/91, 11 files) + build
  (exit 0); report includes full prototype-vs-implementation visual table; mock smoke
  = HTTP-level curl + browser-level Playwright 33/33 (login -> three-zone shell ->
  groups/flat rows/selection -> fold/expand -> leaf nav -> logout -> cleared-token reload)
- Accepted deviations (all reasoned in report): avatar first char from profile.name
  (generic) vs static demo char; sidebar text #666 (token) vs OPS #6f6f6f (hex discipline
  wins); selection bg = derived colorPrimaryBg vs prototype literal #EBF2FF; login stage
  follows rev-12 tokens vs prototype's embedded pre-rev-12 values; @ant-design/icons
  5.6.1 in dependencies (production import + pnpm strict - brief said dev, implementer's
  correction is right); FoldOutlined/ExpandOutlined don't exist in 5.6.1 ->
  MenuFold/MenuUnfoldOutlined (caught by tests)
- Honest limitation: browser-level fake-token 203 unconstructible (mock /me falls back to
  seeded admin for any Bearer - b3 mock-only fallback); 203 chain evidenced at unit +
  HTTP level; optional mock switch recorded as follow-up
- Real bugs caught by tests mid-batch: wrong Result import, missing CSS var injections
  (4 of 17), icons undefined at runtime
- Menu fixture restructure: groups 13/14/15 with path null (groups never navigate),
  pages keep ids/paths/perms, seedRoleMenus [1..15]; page title kept as 日志查询 per
  confirmed prototype (brief said 操作日志 - prototype wins)

### Batch 4: Transport Client & Auth Session (tasks 4.1-4.3)
- Status: Complete + Approved (commit with review fixes)
- Review: Approved - coordinator independently re-ran test (61/61, 7 files) + build
  (exit 0 strict); client 203 semantics verified structurally (never reads
  response.status; dedicated HTTP-200-203 test) + no store imports (DI one-way);
  path->spec table audited (19 functions, all documented); concerns: test-stub bug
  fixed by implementer without touching impl (26/27 -> 27/27); ListLogsQuery as type
  alias (implicit index signature) - accepted
- Review fixes by coordinator: web-app-shell delta gains MODIFIED API Client Seam
  (entity IDs typed as JSON numbers per ID Serialization - resolves the
  entity-id.ts(string) vs api spec(number) contradiction implementer flagged);
  entity-id.ts rewritten to `EntityId = number` (stale snowflake-string doc updated);
  src/api/index.ts seam comment refreshed (seam now populated); hash 98f2d2e7
- Deferred observations: timeout/abort non-goal (additive later); login returns
  menus/perms superset vs main-spec wording (recorded, harmless); tasks 4.2 said
  login/clear, brief specified setSession/clear - brief wins (store has no HTTP)

### Batch 3: Mock Server & Seed-Parity Fixtures (task 3.1)
- Status: Complete + Approved (commit with spec-fix, see b3 feat commit)
- Review: Approved - coordinator independently re-ran test (14/14) + build (exit 0) and
  curl smoke on :5178 (login good/bad, 203 gate, /me, orgs tree, roles IPage, full-replace
  shrink 4->1 top menus + perms [] then restored, port freed). Three concerns accepted:
  (1) brief's literal url-gate conflicted with itself (auth/dicts/logs live in main specs) -
  union-gate over all openspec sources is the correct resolution, 24/24 URLs documented;
  (2) users/list is a plain List per implemented surface - api delta + web-system-console
  wording fixed by coordinator in this batch's commit (paged -> plain list; hash d2722d21);
  (3) menu placeholder paths follow frontend routes (/ultrasound//endoscopy) over V2 seed
  (/us//endo), org_dep codes stay seed-exact - noted as potential backend follow-up
- Notes: plugin 2.7.1 needs NAMED export mockDevServerPlugin (README default-import broken);
  *.mock.ts include glob only - _fixtures.ts safe as helper; per-file esbuild bundling means
  cross-file session state lives in a globalThis bucket (dev-memory only); mock-only fields
  flagged (token shape, menu ids 7-12, roles menuIds echo, arbitrary-Bearer fallback);
  login field is username (mirrors backend LoginRequest); OrbStack real backend on :80 - 
  proxy passthrough proven both variants (real 203 / ECONNREFUSED 500)

### Batch 2: Design System Foundation (tasks 2.1-2.3)
- Status: Complete + Approved (commit 766c81e, 12 files incl. 3 TTFs)
- Review: Approved - coordinator independently re-ran test (14/14) + build (exit 0,
  3 hashed MiSans in dist); hex-scan re-run with corrected globs (first run's 2 "hits"
  were the reviewer's own glob error - both files sit in the sanctioned theme/test
  exclusions); MSYH scan clean; scope exact (4 M + 5 new in lumen-web)
- Notes: TDD RED->GREEN evidence captured for tokens (9 failures on teal baseline) and
  layout (header null / sider 200px); hex guard proven to bite via canary mutation;
  TS18048 fixed by tightening type (NonNullable<ThemeConfig['token']>) - strict intact;
  App.tsx wiring corrected to full themeConfig so component tokens reach the tree;
  fonts 24MB total (8.07/8.05/7.96 MB), cmp-verified byte-identical to OPS sources

### Batch 1: Dependencies & Tooling (task 1.1)
- Status: Complete + Approved (docs commit 3244ccd; feat commit b02e5ca)
- Review: Approved - coordinator independently re-ran test (2/2) + build (exit 0);
  diff scope exactly matches brief; D3 selection: vite-plugin-mock-dev-server 2.7.1
  (peer vite >=5.0.0 covers 7.3.7), no msw fallback; all deps exact-pinned, baseline
  values untouched (pnpm key reorder only); notes: esbuild build-script warning is
  pre-existing pnpm-10 behavior (build green proves working); vitest major 5 unpinned
  in brief - peer evidence captured
- Notes: progress.md shared with parallel rename session - ledger lines for this
  change land in shared commits only when the tree is quiet

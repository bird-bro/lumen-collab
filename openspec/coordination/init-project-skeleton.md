# Coordination: init-project-skeleton

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "init-project-skeleton"`.

## Why

The monorepo currently contains navigation and role files only - `lumen-sev/` and
`lumen-web/` hold `CLAUDE.md`/`AGENTS.md` and nothing else, and `docker/` does not exist.
Every downstream feature (patient, ris, integration, the web UI) needs a build topology to
land in. This feature establishes the compilable foundation for both stacks so later
changes add behavior instead of arguing about module layout, version governance, or
build commands.

Depth is deliberately minimal: **compilable empty shell**. Module directories, build
files, application entry points, and configuration seams exist and the builds go green.
No business logic, no endpoints, no database schema, no reference gateway-to-service call
chain. Those arrive with the feature changes that need them.

## Shared Design

These are the contracts both stacks must agree on. They are already normative in
`openspec/specs/` - restated here only to fix the skeleton's touchpoints.

| Contract | Source of truth | Skeleton obligation |
|:--|:--|:--|
| Response envelope `{ timestamp, code: string, msg, data }` (OPS `R` contract), success `"200"`/`"204"`, base URL `/api/v1` | `openspec/specs/api/spec.md` (aligned to OPS by `init-backend-skeleton`) | Frontend dev server proxies `/api/v1`; frontend `src/api/` types this envelope; backend does not yet emit envelopes |
| Unified API entry: all `/api/v1/<service>/**` through **nginx** (cloud form may introduce Spring Cloud Gateway) | `openspec/specs/api/spec.md` | Frontend never targets a service port directly; nginx is the only inbound route |
| Snowflake IDs serialized as JSON strings | `openspec/specs/api/spec.md` | Not exercised yet (no entities); BOM must carry the Jackson tooling that will do it |
| Error code scheme: OPS `ExceptionEnum` letter codes (`A` client / `B` system / `C` third-party) as strings, error field `msg` | `openspec/specs/errors/spec.md` (aligned to OPS) | Not exercised yet (no handlers) |
| Entity audit contract: OPS `EntityBase` - `deleted` + `create_by`/`create_time`/`update_by`/`update_time` (GMT+8 serialization) | `openspec/specs/data/spec.md` (aligned to OPS) | No datasource wired in this feature; delivery mechanism = the verbatim-copied `framework`/`common` (`EntityBase` + `MetaObjectConfig`) |

**Port allocation** (shared, so the frontend proxy and deployment config agree):

| Entry / Service | Port | Note |
|:--|:--|:--|
| **nginx** (unified API entry + SPA static) | 80 / 443 | the only inbound route; there is **no Java gateway** |
| `lumen-system` | 8081 | |
| `lumen-web` dev server | 5173 | proxies `/api/v1` to the nginx entry |

Ports for patient / ris / integration are **not** allocated here - the changes that create
those services assign them and update this table.

## Decisions

- **Compilable empty shell, not a reference slice** - the user chose minimal depth; a
  gateway→auth call chain with a live endpoint would pull in the response envelope,
  exception handling, and Nacos runtime availability, none of which this feature needs to
  prove. Build-green is the acceptance bar. - 2026-10-29
- **Backend scope is the v1 foundation subset only (revised)** - `lumen-bom`, the OPS
  `framework` + `common` modules copied verbatim, and the `lumen-auth` shell.
  `lumen-exam-core`, `lumen-api/*`, `lumen-patient`, `lumen-radiology`, `lumen-integration`, and
  the v2 department services are deferred to the changes that give them behavior. Matches
  the handoff doc's recommended execution order step 1. - 2026-10-29
- **No Java gateway; nginx is the unified entry (removes `lumen-gateway` from the project)** -
  user decision 2026-10-29. Every former gateway function has an owner in the hospital form:
  routing `/api/v1/<service>/**` -> nginx path routing; edge auth -> each service's
  `JwtAuthenticationTokenFilter` (OPS pattern, token state in shared Redis); rate limiting ->
  Sentinel in-process; TLS/static/HTTP2 -> nginx. Service discovery is static (fixed
  containers), so the gateway's dynamic-discovery value is zero. **Spring Cloud Gateway
  returns as a future change only when the cloud form materializes** (dynamic instances,
  edge traffic governance - the eis-cloud precedent used Zuul, SCG is its replacement). The
  api spec's Gateway Path Convention gets a MODIFIED delta: "through the platform entry
  (nginx; SCG optional in cloud form)". - 2026-10-29
- **OPS `framework` + `common` adopted verbatim as shared base modules** - user decision
  2026-10-29: name, artifactId, and package (`org.birdbro.framework` / `org.birdbro.common`)
  copied unchanged; they are the shared base for all Servlet services. This supersedes the
  earlier five-way `lumen-common-*` plan. Five recorded adaptations (localized diffs):
  (1) `RedisConfig` becomes conditional and `FastJson2JsonRedisSerializer` is confined to it
  (Redis is optional in the hospital form; B2 review corrected the scope); (2) security wiring: `lumen-auth` issues tokens, other services verify
  via the copied filter against shared Redis; (3) operation-log tables are owned per service
  (each service's Flyway carries the same op-log schema; the copied mappers write the
  service's own DB); (4) **`DingTalkConstants` + `WxCryptUtil` are deleted during the copy**
  (user instruction 2026-10-29 - OPS-domain baggage; zero references **within the copied
  modules**, verified by grep - full-OPS consumers exist only in `birdbro-operation`/
  `birdbro-manage`, which are not copied and have no lumen equivalent); (5) **governance alignment of the copied POM** - the single third-party
  version literal in OPS's child POMs (`org.bouncycastle:bcprov-jdk16:1.46`, found during
  Batch 1) moves into `lumen-bom` management, resolved version unchanged. Two binding disciplines: **`lumen-gateway`/cloud gateway never depends
  on framework/common (WebFlux vs Servlet); only Servlet services depend on them**. Remaining
  idle classes (`WorkOrderStatusEnum`, `pojo/user/*`) are copied but untouched - pruned only
  by the changes that touch them. - 2026-10-29
- **framework/common evolvability + adaptation (6)** - user ruling 2026-11-03: the shared
  platform layer MAY be adjusted/optimized on the OPS bloodline (supersedes the
  verbatim-only reading of 2026-10-29; OPS originals in their own repo stay untouched and
  remain the re-diff baseline). Evolution style: ADDITIVE-FIRST - new framework/common
  files over modified OPS lines, so future upstream syncs cannot conflict. First fruits
  (auth-rbac-foundation): **(6) no-gateway audit-identity bridging + per-service
  anonymous paths** - `UserHeaderBridgeFilter` relocated from lumen-auth to
  `framework/security/filter/` (the copied `MetaObjectConfig` consumes the
  gateway-injected `USER-HEADER`; lumen has no Java gateway, so the JWT→USER-HEADER
  translation is platform machinery auto-registered in every Servlet service), plus
  `framework/security/SecurityAnonymousProperties` + `AnonymousPathCustomizer`
  (per-service `security.permit-all` config; the copied `SecurityConfig` permitAll list
  stays verbatim) and the shared `BCryptPasswordEncoder` bean (`SecurityBeanConfig`)
  replacing lumen-auth's service-local config class. - 2026-11-03
- **(7) `ice.`-free log table names** (system-data change): the copied
  `LogInfoEntity`/`LogSignEntity` and the two log mapper XMLs (`insertInfo`/`insertSign`
  are the real write path) drop the OPS `ice.` schema qualifier - unqualified names
  resolve via `search_path = <service-schema>, public` (the 随访 precedent); the platform
  shared dict/log tables live in `public` per user decision 2026-11-03. - 2026-11-03
- **(8) request-context filter for the copied logout handler**
  (`framework/config/RequestContextConfig.java`, new file): the copied
  `LogoutSuccessHandlerImpl` records sign events through `AsyncFactory`, which needs
  `RequestContextHolder` - empty at the `LogoutFilter` phase of the security chain, so
  logout crashed 100% before this fix. Root cause (review-verified): the copied
  `SwaggerConfig`'s `@EnableWebMvc` disables `WebMvcAutoConfiguration`, silently removing
  Boot 2.4+'s own auto-configured `RequestContextFilter` - the new file restores exactly
  that bean with mirrored conditions. Registers an `OrderedRequestContextFilter` ahead
  of the security chain; zero copied-line changes. - 2026-11-03
- **(9) OpenAPI metadata via properties** (`framework/config/SwaggerConfig.java`, 3-line
  modification): `apiInfo()` builds from `@Value("${swagger.title/description/version/
  contact-name}")` with the OPS literals as DEFAULTS (zero-config behavior unchanged);
  each service overrides in its own application.yml (lumen-system: LumenCollab).
  Additive-first was not possible here - `ApiInfo` is built inline in the copied file,
  so configurability requires touching it. - 2026-11-04
- **Package naming: dual root by code origin** - user decision 2026-10-29. groupId
  `org.birdbro` everywhere; copied modules keep `org.birdbro.{framework,common}`; new
  modules use `org.birdbro.lumen.*` (`org.birdbro.lumen.{auth,patient,radiology,us,endo,
  integration,examcore}`, `org.birdbro.lumen.api.{patient,integration}`). In-service
  layering `controller/service/mapper/entity/dto`; application class in the service root
  package; services scan `org.birdbro` (OPS `LaunchApplication` habit). Author attribution
  (birdbro) preserved in headers/pom/LICENSE/images. Rules written to
  `.claude/rules/naming.md`. - 2026-10-29
- **Department module naming** - `lumen-radiology` (radiology: RIS is the industry system term at
  the PACS/LIS/HIS level and the product positioning itself), `lumen-us` (DICOM modality US),
  `lumen-endo` (DICOM modality ENDO); future departments follow `lumen-<standard abbrev>`
  (e.g. `lumen-ecg`). The trio is "each domain's most recognizable short name", not strict
  modality-code uniformity. - 2026-10-29
- **Deployment forms decided (full analysis: `docs/analysis/hospital-deployment-forms.zh.md`)** -
  user decisions 2026-10-29: (a) **hospital form = multi-service processes** (one module =
  one jar = one container; this is what makes per-department release isolation real -
  upgrading radiology never touches ultrasound), static service discovery
  (`SimpleDiscoveryClient`), **Nacos kept as configuration center only** (three guardrails:
  local bootstrap fallback, client snapshot fallback, restricted `@RefreshScope`), nginx
  entry, Docker compose delivery with offline `docker save/load`; (b) modular monolith
  (single launch assembly) demoted to optional tiny-site form; (c) full microservices
  (SCG + Nacos discovery + RocketMQ) is the **cloud product's** runtime form. Two binding disciplines hold inside each product: **inter-module calls only
  through `lumen-api` Feign contracts; events only through the `EventPublisher` abstraction**
  (local product = Spring Event / HTTP callback with replay, cloud product = RocketMQ).
  `exam-core` version pinning per service preserves release isolation for the shared kernel.
  - 2026-10-29
- **Two products, not two assemblies (supersedes "one codebase, two assemblies")** - user
  correction 2026-10-29: in the cloud business **only radiology can be diagnosed remotely;
  ultrasound and endoscopy are on-site only**. So the cloud version is a distinct product
  (radiology remote diagnosis + referral + cloud film), not the local suite redeployed.
  Local product = full department suite, on-site diagnosis. Cloud product = radiology-only
  remote diagnosis - its own frontend and backend assembly; **us/endo never deploy there**.
  The two products share the foundation as versioned libraries (`lumen-bom`, `framework`,
  `common`, `lumen-exam-core`, `lumen-api`) - this is NOT the fork anti-pattern from NOTES
  lesson #1 (that was two copies of the same business code drifting); sharing the kernel is
  exactly that lesson's prescription, and the products' different business scope removes the
  drift surface. Repo topology for the cloud product (second product dir in this monorepo vs
  separate repo) is decided in the cloud product's own proposal - not here.
  **Lineage evidence (user pointer 2026-10-29, measured)**: the old ecosystem already had
  exactly this product split - `RCP` (`rcp_sev`, also LoopForge-instrumented) is a pure
  cloud radiology remote-diagnosis platform: modules framework, common, birdbro-launch,
  birdbro-manage, **birdbro-radiology**, birdbro-setting, birdbro-bi, birdbro-ai - **no
  us/endo**, plus `rcp_web` as its own frontend. And RCP carries **its own copy** of
  framework/common that has measurably drifted from OPS's: framework 20/35 files differ,
  common **47/64 files differ (~73%)**, with product-specific constants leaking into the
  shared lib (RCP-only `ExamConstants`; OPS-only `DingTalkConstants`/`OrgConstants` - which
  also independently validates adaptation (d): the DingTalk/WeChat baggage is OPS-only).
  Sharing versioned libraries instead of per-product copies is the measured fix for the
  drift this team has already lived through (NOTES lesson #1). B1 note recorded here (was
  only in the implementer report): OPS's parent manages neither `httpclient` nor
  `commons-lang3` nor `validation-api` (they resolve via `spring-boot-dependencies`), so
  they are correctly absent from lumen-bom's 1:1 copy; `bcprov-jdk16` enters via recorded
  adaptation (e). - 2026-10-29
- **Entry-class contract (B4 review, red-proven)** - every service entry class MUST carry
  both `@SpringBootApplication(scanBasePackages = "org.birdbro")` AND
  `@MapperScan("org.birdbro.**.mapper")`, mirroring OPS `LaunchApplication` byte-for-byte.
  The MapperScan is not optional: framework's operation-log `@Service` impls extend
  MyBatis-Plus `ServiceImpl` and autowire `LogInfoMapper`/`LogSignMapper`, which no
  component scan can satisfy (mappers are not Spring components) - the shell cannot boot
  without it (B4 red rung 3). All six future services replicate this contract.
  Related B4 proofs recorded: the test-only `UserDetailsService` stub is quarantined
  (`@TestConfiguration` + explicit `@Import`, never in main sources - it MUST NOT migrate);
  the druid-exclude fallback was proven insufficient and reverted for a dummy-URL datasource
  with explicit `initial-size: 0`; task 4.1's literal "fail before task 3.2's profile" is
  unachievable in batch order - the observed red (profile active, failing on
  placeholder/wiring/datasource) is the strongest available red and honors TDD intent.
  - 2026-10-29
- **D3 amendment (B3 review, proven by control experiment)** - with
  `spring-cloud-starter-bootstrap` on the classpath, the bootstrap context binds
  `NacosConfigProperties` from `bootstrap.yml` ONLY; disable flags in
  `application-test.yml` load into the main environment after bootstrap and never reach
  the config locator (`@ConditionalOnNacosConfigEnabled`). Measured without a running
  Nacos: 12 live connection attempts to :8848, tolerated as empty by SCA 2021.1 -
  "tolerated", not "neutralized" (behavior 15's remote-config clause unmet; also breaks
  the moment any Nacos listens on 8848, e.g. the standard compose workflow). Fix: a
  `test`-profile document inside `bootstrap.yml` (`spring.config.activate.on-profile:
  test`) carries the disable flags - the bootstrap context inherits the run's active
  profiles (verified in spring-cloud-context 3.0.3). Registration-side flags in
  `application-test.yml` were proven to work and stay. D3's original fallback ladder
  (`auto-registration`, `autoconfigure.exclude`) does NOT fix this - bootstrap-context
  beans are unreachable from the main context. - 2026-10-29
- **Batch 1 build-mechanics decisions (review-accepted, Batch 1 gate)** - (1) `lumen-bom`
  is a standalone reactor module (no `<parent>`): aggregator-imports-BOM + BOM-parent =
  aggregator is a Maven depMgmt import cycle (reproduced on Maven 3.9.9). Consequence:
  modules resolve the BOM only inside the full reactor (or after `./mvnw -pl lumen-bom
  install`) - build from the aggregator; documented in `lumen-sev/CLAUDE.md`. (2) BOM
  import order = OPS's own (cloud, alibaba, framework, boot) so the effective
  dependencyManagement is **byte-identical to OPS** - verified by full-key diff: 1484 shared keys, 0 drift (final-review measurement)
  (the "obvious" framework-first order would flip okhttp to 3.14.9 vs OPS's 3.14.4).
  (3) dcm4che repository is declared in the **aggregator** (inherited by children), not
  only in lumen-bom - a BOM's `<repositories>` do not propagate to consumers (Batch 1
  review Important-1). Build harness: OPS's `mvnw` wrapper (Maven 3.9.9) + **sanitized**
  settings.xml (aliyun mirror only; OPS's original carried plaintext Aliyun RDC credentials
  in git - lumen must not repeat that; rotation recommended for OPS). - 2026-10-29
- **`docker/` is out of scope for this feature** - the user deferred it. The skeleton must
  therefore start without external infrastructure: every test runs with Nacos, PostgreSQL,
  Redis, and RocketMQ absent. This is a hard constraint on both stacks' test design. - 2026-10-29
- **Two per-stack changes under one coordination doc** - backend and frontend build
  toolchains share nothing, so a single change would mix two unrelated verification
  commands. Split per CLAUDE.md's cross-stack rule; this doc is the registry. - 2026-10-29
- **Frontend mock tool remains unselected** - `lumen-web/CLAUDE.md` mandates mock-first
  development, but choosing MSW vs `vite-plugin-mock-dev-server` vs mockjs is a separate
  decision with its own tradeoffs. This feature creates the `src/api/` seam the mock layer
  will plug into and logs the gap in `init-web-skeleton/debt.md`. - 2026-10-29
- **Changes are applied sequentially, not in parallel** - `openspec/loop-state.yaml`
  tracks one active change and one `artifacts_hash`, so the two changes cannot both be the
  "fresh" contract at once. Apply backend first, then frontend. - 2026-10-29
- **Backend version train = the OPS-verified set (supersedes earlier stack decisions)** -
  the user directed (2026-10-29) that the backend MUST use the versions proven compatible in
  the reference project OPS (`/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/pom.xml`):
  Java 11, Spring Boot 2.5.15, Spring Framework BOM 5.3.33, Spring Cloud 2020.0.3, Spring
  Cloud Alibaba 2021.1, and OPS's shared jars matched exactly (Lombok 1.18.30, Hutool
  5.8.20, MyBatis-Plus `mybatis-plus-boot-starter` 3.5.3.1, Druid 1.2.18, fastjson2 2.0.38,
  easyexcel 3.3.2, mapstruct 1.5.5.Final, etc.). This **supersedes** the handoff doc §2's
  Java 17 + Spring Boot 3.x + HikariCP lines (root `CLAUDE.md` Tech Stack and
  `lumen-sev/CLAUDE.md` are updated in the same change - task 5.4). Known tradeoffs accepted:
  Boot 2.5.15 OSS EOL, `javax` namespace; two OPS versions carry CVEs at those exact
  coordinates (PostgreSQL 42.2.18, commons-fileupload 1.3.1) - patch-level bumps are the
  candidate first deviation pending user sign-off. Libraries OPS does not manage (dcm4che,
  HAPI HL7v2, RocketMQ) carry no OPS verification and stay off every compile path until
  their own changes verify them. - 2026-10-29
- **Frontend is unaffected by the OPS alignment** - OPS's frontend is Vue (`ops_web`),
  unrelated to `lumen-web` (React 18 + AntD 5). No `init-web-skeleton` artifact changes. - 2026-10-29
- **API/data/error contracts aligned to OPS verbatim (supersedes the original envelope and
  audit-field specs)** - the user directed (2026-10-29) that `R.java` and `EntityBase` be
  adopted as-is rather than adapted, so `init-backend-skeleton` carries MODIFIED deltas for
  `api` (envelope `{ timestamp, code: string, msg, data }`, success `"200"`/`"204"`),
  `errors` (OPS `ExceptionEnum` letter scheme `A`/`B`/`C`, error field `msg`), and `data`
  (`EntityBase`: `deleted` + `create_by`/`create_time`/`update_by`/`update_time`, GMT+8
  serialization). Consequences accepted: the envelope loses the old numeric-code ↔ HTTP-status
  mapping; timestamps serialize in GMT+8 rather than UTC; audit operator columns are OPS's
  `Integer` (int32) - **flagged**: if lumen user PKs are snowflake `bigint`, `create_by`/
  `update_by` must widen to bigint in the `lumen-auth` change. **Flag CLOSED (2026-11-02,
  auth-rbac-foundation)**: ruled **stay Integer** - PKs are Integer auto-increment per this
  change's data delta (`openspec/changes/auth-rbac-foundation/specs/data/spec.md`: user
  decision 2026-11-02, the bigint-snowflake clause is withdrawn); no widening ever needed
  since `user_info.id` is Integer. `init-web-skeleton`'s planned
  `ApiEnvelope` type was updated to match (its delta specs/design - `lumen-web/src/` does not
  exist yet; the frontend change is still `proposing`). - 2026-10-29
- **Flyway/PG compatibility accepted as-is (auth-rbac-foundation watch-item)** - the team
  test server runs PostgreSQL 14.11 while Boot 2.5.15 manages `flyway-core` 7.7.3 (NOT
  7.14.0/7.15, the first versions with PG 14 support). PG 14 triggers only a WARN ("not
  tested, latest supported version 13"; decompile-verified: warn, no throw), all migrations
  are plain DDL, and the suite is green (34/34). Accepted as-is with **zero version
  deviation** from the OPS train; IF a future migration misbehaves on PG 14, the
  design-gated fix is a lumen-only `flyway-core` pin to 7.15.0 (first 7.x with PG 14
  support) via proposal - never a silent dependency bump. - 2026-11-02

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | `init-backend-skeleton` | **archived** (2026-11-02) | PASS_WITH_ISSUES · 29/37 · credential in archive |
| frontend | `init-web-skeleton` | **archived** (2026-11-02) | PASS · 13/13 · credential in archive |

**Batch 5 traceability**: `lumen-web/vite.config.ts` gained a `build.rollupOptions.output.chunkFileNames` function in Batch 5 (commit `d00617a`) - an evidence-enabler deriving `assets/pages/<department>/` output paths so the lazy department chunks are greppable in the build log (B5 review accepted as not scope creep).

**Endpoint Documentation alignment - pending archive (auth-rbac-foundation)**: the MAIN api
spec's `Endpoint Documentation` requirement still exemplifies `code: 0` (numeric, superseded
by the OPS string-code contract); it gets aligned when this change's api delta archives -
`openspec/changes/auth-rbac-foundation/specs/api/spec.md` (its scenarios carry `"200"`).
`openspec/specs/` stays untouched until then. - 2026-11-02

**Envelope mutation control - RE-RUN 2026-11-02 (auth-rbac-foundation Batch 5)**: the
type-level negative control on `ApiEnvelope` (TS2322 red probe), previously QUEUED for the
first endpoint-owning change (B3+B4 review carry-over), is re-run at that change's close -
auth-rbac-foundation's backend endpoints now emit the envelope. Probe: temporary
`lumen-web/src/envelope-mutation-probe.ts` assigned a wrong-shaped value
(numeric `code: 200`, the pre-OPS envelope shape) to an `ApiEnvelope`-typed variable;
`tsc --noEmit` (the type-check half of `pnpm build`) REJECTED it -
`src/envelope-mutation-probe.ts(10,3): error TS2322: Type 'number' is not assignable to type 'string'.` (exit 2) - then the probe was deleted and the clean re-run exited 0.
**Control PASSES**: the string-code contract is enforced at the type level.

## Gate

Feature done = ALL registered changes verify PASS -> archive each.

Verification commands come from `openspec/verify.config.yaml`:

| Stack | L1 build | L3 test |
|:--|:--|:--|
| backend (`lumen-sev`) | `./mvnw compile -q` (dir: lumen-sev) | `./mvnw test -q` |
| frontend (`lumen-web`) | `pnpm build` | `null` -> SKIP (no runner yet; see debt log) |

L2 (WHEN/THEN scenarios) is checked against each change's `specs/`.

- **Post-archive amendment (2026-10-30, user instruction)**: author headers in the copied
  `framework`/`common` normalized to exactly `@author birdbro` (ruoyi ×35, luzw ×4, zhengtw ×2,
  bird-variants ×18; `@date` history preserved; regression test green). Consequence for the
  verbatim gate: future `diff -r` vs OPS now differs by author headers + the five recorded
  adaptations - OPS originals untouched in their own repo remain the re-diff baseline. Known
  remaining RuoYi trace: `Constants.JSON_WHITELIST_STR` contains `"com.ruoyi"` (functional
  string, dead in lumen - no such package) - cleanup candidate for the first change touching
  `Constants.java`.
**Deferred naming input (final review Minor-6)**: `lumen-sev/CLAUDE.md`'s Database line
still names the radiology DB `lumen_ris` while the module is `lumen-radiology` - no DB exists
yet; the radiology change settles the DB name and updates that line.
**Authority-gating deferral (auth-rbac-foundation, 2026-11-02)**: the User Management
requirement says "authenticated administrator" - the implementation enforces authentication
only (any valid token; no task in that change mandates authority checks). Permission gating
is queued to the DataScope/radiology change; recorded here so archive/verify is not silent
about it.
**Archive order**: archive `init-backend-skeleton` and `init-web-skeleton` only after both
verify PASS, then mark this doc's registry rows `archived`. Post-closure addendum
(2026-11-02): `init-infra-compose` (hospital infra: PG six-DBs + Nacos config-center +
Redis as containers) was archived the same day, then **superseded by user decision
(2026-10-30 clock note: 2026-11-02)** - PostgreSQL/Nacos/Redis are provided by the hospital
machine-room servers or cloud managed instances; lumen NEVER deploys them. Consequences:
the containerized compose is removed; the six-database script lives on as a DBA handoff
artifact (`docs/deployment/pg-init.sql`); the main spec `infra-compose` was replaced by
`environment-contract` (external provisioning + env-var consumption); `docker/` remains
reserved for lumen application containers + nginx entry. The archived change's credential
and diff remain as the historical record of what was built before the decision.
**Archive order**: archive `init-backend-skeleton` and `init-web-skeleton` only after both
verify PASS, then mark this doc's registry rows `archived`.
**pg-init role-isolation scenario - deferred demonstration (auth-rbac-foundation, 2026-11-02)**:
`docs/deployment/pg-init.sql` creates `lumen` + the six schemas idempotently (verified by
script review; the test bootstrap exercises the same CREATE SCHEMA semantics for `auth`),
but the per-service role/GRANT block ships COMMENTED OUT awaiting DBA-set passwords - so the
data delta's "Role isolation denies cross-schema access" scenario stays undemonstrable from
committed artifacts until a DBA fills the passwords on a real environment. Record now;
demonstrate at deployment-time verification.

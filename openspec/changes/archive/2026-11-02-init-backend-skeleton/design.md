# Design

## Context

`lumen-sev/` holds only `CLAUDE.md`/`AGENTS.md`; there is no `pom.xml` and nothing to
compile. See `proposal.md` § Why for motivation and `specs/` for the normative requirements.
This design records only the technical choices that make a zero-infrastructure build
reproducible: the pinned release train, how Nacos is neutralized for tests, and how the
BOM handles a dependency that is not on Maven Central.

The binding constraint is the proposal's Success Criteria: `mvn compile` and `mvn test`
must both pass with no Nacos, PostgreSQL, Redis, or RocketMQ running, because `docker/` is
out of scope for this feature.

**Source of truth for the version train changed (2026-10-29).** The user directed that the
backend MUST use the versions already proven compatible in the reference project OPS - the
user's own production-proven backend at
`/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/pom.xml` (groupId `org.birdbro`). The
earlier idea of adopting the newest Boot 3.x train is dropped; OPS's jar set is copied 1:1.

## Goals / Non-Goals

**Goals:**
- A green `./mvnw compile -q` and `test -q` from a clean checkout with no
  infrastructure.
- One authoritative version source (`lumen-bom`); zero version literals in child POMs.
- `lumen-bom`'s managed set identical to OPS's wherever OPS manages the same library.
- Service shells whose Spring context loads in a test without contacting Nacos.

**Non-Goals:**
- Any runtime behavior - no endpoints, no envelope, no exception handling, no JWT.
- Any datasource that actually connects; the copied `framework`/`common` carry OPS's
  entity/audit machinery, but nothing wires a database in this change.
- Wiring RocketMQ, Sentinel rules, or Flyway migrations.
- Upgrading off the OPS train (Boot 2.5.15 EOL is accepted for now; a future proposal).
- Making the services register with a live Nacos in CI (deferred until `docker/` exists).

## Decisions

### D1 - The OPS-verified train, copied 1:1 (2026-10-29)
OPS's parent POM was read in full; all version properties live there (module POMs add none).
Everything below is copied exactly, not re-researched.

| Component | OPS version (adopted) | Note |
|:--|:--|:--|
| Java | 11 | OPS `java.version`; supersedes the handoff doc's Java 17 |
| Spring Boot | 2.5.15 | supersedes the handoff doc's "3.x" |
| Spring Framework BOM | 5.3.33 | explicitly managed by OPS |
| Spring Cloud | 2020.0.3 | pairs with SCA 2021.1 and Boot 2.5.x |
| Spring Cloud Alibaba | 2021.1 | Nacos discovery + config; Sentinel managed, unused |
| spring-cloud-starter-bootstrap | 3.0.3 | OPS loads Nacos config via `bootstrap.yml` |
| MyBatis-Plus | 3.5.3.1 | `mybatis-plus-boot-starter` (Boot 2 line) - **not** the boot3 starter |
| Lombok | 1.18.30 | annotation processor, wired via `maven-compiler-plugin` |
| Hutool | 5.8.20 | `hutool-all` |
| Druid | 1.2.18 | `druid-spring-boot-starter`; supersedes the handoff doc's HikariCP choice at the BOM level (which pool a service actually uses is decided when datasources arrive) |
| PostgreSQL driver | 42.2.18 | OPS pin; see Risks for the CVE note |
| fastjson2 | 2.0.38 | |
| easyexcel | 3.3.2 | |
| mapstruct | 1.5.5.Final | annotation processor, wired with Lombok |
| swagger | 3.0.0 | `springfox-boot-starter`; managed but unused in this change (springfox only works up to Boot 2.5 - this train is compatible) |
| Shiro | 1.10.1 | managed only; lumen's auth design (JWT per handoff) is decided by the `lumen-auth` change, not here |
| jjwt | 0.9.1 | managed only, for that same future decision |
| Also copied | commons-io 2.13.0, zxing 3.3.3, TinyPinyin 2.0.3.RELEASE, feign-core 12.1, commons-compress 1.21, commons-fileupload 1.3.1, mybatis-typehandlers-jsr310 1.0.2, UserAgentUtils 1.21 | the rest of OPS's managed set |
| Build plugins | surefire 3.0.0-M5, compiler 3.8.1 (+ annotationProcessorPaths for config-processor/Lombok/mapstruct), flatten 1.5.0 with `${revision}` | OPS's build mechanism, adopted so the skeleton builds the same way OPS does |

`lumen-bom` imports `spring-framework-bom`, `spring-boot-dependencies`, `spring-cloud-dependencies`,
and `spring-cloud-alibaba-dependencies` as BOM imports, then declares OPS's managed versions on
top - mirroring OPS's own structure (spring-cloud + alibaba + framework + boot BOMs, then
explicit entries).

**Lumen additions OPS does not manage** (no OPS verification inherited; flagged in Risks):
- dcm4che 5.35.2 (`org.dcm4che:dcm4che-core` / `dcm4che-net`) - see D2
- HAPI HL7v2 2.6.0 (`ca.uhn.hapi:hapi-base`, `hapi-structures-v25`; **not** HAPI FHIR)
- rocketmq-spring-boot-starter 2.2.3 (RocketMQ client 4.9.4 - the Boot 2.x-compatible line)
- Flyway and Redis are NOT pinned - `spring-boot-dependencies` manages both, same as OPS
  (which uses `spring-boot-starter-data-redis` with no override)

- **Alternative considered:** keep the newest Boot 3.5.x train and only copy OPS's utility
  jar versions (Lombok/Hutool/etc.). Rejected by the user - Lombok/Hutool versions alone do
  not make a train verified, and Boot 2 vs 3 starters are different artifacts
  (`mybatis-plus-boot-starter` vs `mybatis-plus-spring-boot3-starter`), so a partial copy
  would be neither OPS's verified set nor a coherent new one. Whichever train is chosen, it
  is chosen whole.

### D2 - dcm4che requires an extra repository
dcm4che artifacts are **not published to Maven Central**. Verified: `dcm4che-core` returns
404 on `repo1.maven.org` but resolves from `https://www.dcm4che.org/maven2` (latest 5.35.2).
`lumen-bom` therefore declares that `<repository>` and version-manages `dcm4che-core` /
`dcm4che-net` at 5.35.2, but **no module puts them on its compile path in this change**
(`specs/dependency-governance` § Integration Libraries Managed Not Linked).

- **Risk this creates:** if `www.dcm4che.org` is unreachable, a build that actually resolves
  the artifact would fail. Mitigated because nothing consumes it yet - declaring a
  `<dependencyManagement>` entry and a `<repository>` does not download the artifact during
  `compile`/`test` of the foundation modules.

### D3 - Neutralize Nacos for zero-infrastructure tests
OPS wires Nacos config through `spring-cloud-starter-bootstrap` 3.0.3 (`bootstrap.yml`),
and this skeleton adopts that same mechanism. `lumen-auth` includes Nacos
discovery + config on the classpath (that is the point of the wiring), but a test must load
the context with no Nacos server. The test profile sets:
- `spring.cloud.nacos.discovery.enabled=false`
- `spring.cloud.nacos.config.enabled=false`
- `spring.cloud.discovery.enabled=false`

With bootstrap on the classpath and the config flag false, the bootstrap phase skips the
remote import entirely; no Nacos connection is attempted. The context-load test activates
this profile and asserts the context starts. Production `bootstrap.yml` / `application.yml`
keeps Nacos enabled with `spring.application.name` and `server.port` (8081 for the only
shell in this change).

- **Alternative considered:** exclude the Nacos starters from the test classpath via Maven.
  Rejected - it hides real autoconfiguration and diverges test wiring from production; the
  property-based approach exercises the same beans with the network call disabled.
- **Note:** on this train the Boot 2.4+ `spring.config.import` mechanism is unnecessary
  (bootstrap handles it), unlike the Boot 3 approach sketched in earlier drafts of this
  design.

### D4 - Module layout: verbatim OPS base + new lumen modules, flat under lumen-sev/
```
lumen-sev/
├── pom.xml                      # aggregator (packaging: pom) + ${revision} + flatten plugin
├── lumen-bom/                   # version source (packaging: pom)
├── framework/                   # ★ copied verbatim from OPS (org.birdbro.framework)
├── common/                      # ★ copied verbatim from OPS (org.birdbro.common)
└── lumen-auth/                  # service shell (org.birdbro.lumen.auth)
```
No `lumen-services/` layer (abolished per handoff §5), **no Java gateway module** - the
unified API entry is nginx (user decision 2026-10-29; every gateway function is owned by
nginx / per-service `JwtAuthenticationTokenFilter` / Sentinel; SCG is a future cloud-form
change). Dual package root marks code origin: copied modules keep
`org.birdbro.{framework,common}`; new modules use `org.birdbro.lumen.*`
(`.claude/rules/naming.md`). The earlier five-way `lumen-common-*` split is **superseded**
by the verbatim copy. Shared jars are built by `maven-jar-plugin` only - **no**
`spring-boot-maven-plugin` repackage - enforcing `specs/service-topology` § Shared Library
Boundary (non-executable). Module coordinates use OPS's `${revision}` +
`flatten-maven-plugin` mechanism (revision `0.1.0`).

### D5 - OPS base modules verbatim, with exactly five adaptations
`framework` and `common` are copied unchanged (name, artifactId, package). The three
recorded adaptations (anything else is a governance violation per
`specs/service-topology` § Verbatim Adoption):
1. **Redis conditional** - `RedisConfig` gets `@ConditionalOnClass`/
   `@ConditionalOnProperty` guards so a service context loads with no Redis server (Redis
   is optional in the hospital form). `FastJson2JsonRedisSerializer` needs no guard of its
   own: its only instantiation is inside `RedisConfig` (Batch 2 review), so guarding the
   configuration class fully prevents its loading. (D5 correction, B2 review.)
   Semantics: the guard is **default-on** (`matchIfMissing = true`) - a service using Redis
   needs no extra property; a service opts OUT with `lumen.redis.enabled: false`.
2. **Security wiring boundary** - the copied `TokenService` /
   `JwtAuthenticationTokenFilter` stay as-is; `lumen-auth` will be the token issuer, other
   services verify against shared Redis. Wiring completes in the auth feature change, not
   here.
3. **Operation-log table ownership** - the copied `log/*` mappers assume one shared DB; in
   the multi-service form each service's Flyway carries the same op-log schema and the
   mappers write that service's own DB. Recorded now, implemented per service.
4. **Business-baggage removal** (user instruction 2026-10-29) - `common/Constants/DingTalkConstants.java`
   and `common/utils/WxCryptUtil.java` are **deleted** during the copy: they are OPS-domain
   artifacts with **zero references inside the copied modules** (verified by grep; the
   full-OPS grep shows consumers only in `birdbro-operation`/`birdbro-manage` - OPS business
   modules outside the copy scope, with no lumen equivalent to break), and the eis/RCP
   lineage has no DingTalk/WeChat dependency (RCP's own common copy lacks
   `DingTalkConstants` entirely). Remaining idle classes (`WorkOrderStatusEnum`,
   `pojo/user/*`) are copied but untouched - pruned only by the changes that touch them.
5. **Author-header normalization** (post-archive, user instruction 2026-10-30): all `@author`
   variants in the copied modules (`ruoyi` ×35, `luzw` ×4, `zhengtw` ×2, `bird`/`birdBro`/`@author:` ×18)
   normalized to exactly `@author birdbro`; original `@date` lines preserved as history. Two
   code-level RuoYi traces handled separately: the `//RuoYiConfig...` comment in `IpUtils`
   replaced with a neutral note; the `com.ruoyi` entry in `Constants.JSON_WHITELIST_STR` left
   as-is (functional string, dead in lumen - no `com.ruoyi` package exists) - cleanup candidate
   for the first change touching `Constants.java`.
6. **Governance alignment of the copied POM** (Batch 1 finding) - OPS's `common/pom.xml`
   carries the only third-party version literal in its child POMs
   (`org.bouncycastle:bcprov-jdk16:1.46`). The copy keeps the dependency but drops the
   literal, and `lumen-bom` manages `bcprov-jdk16:1.46` instead - satisfying
   `specs/dependency-governance` § Single Version Source without changing the resolved
   version.

Each service's entry class scans `org.birdbro` (OPS `LaunchApplication`
habit) so the copied beans register without any spring.factories machinery.

## Risks / Trade-offs

- **The OPS train is old: Java 11 and Spring Boot 2.5.15, whose OSS support has ended.** A
  new open-source project starting on it accepts no further OSS security patches, the
  `javax` namespace, and a Boot-2-only dependency universe. This was weighed and accepted by
  the user (2026-10-29) in exchange for the one thing the newest train cannot offer: a jar
  set whose mutual compatibility is already production-proven. Mitigations: the BOM isolates
  every version so a future upgrade proposal changes one file; nothing in this change
  hard-codes a Boot-2-ism outside the BOM.
- **Two OPS-managed versions have known CVEs at those exact coordinates:** PostgreSQL
  driver 42.2.18 (CVE-2022-21724, fixed in 42.2.25) and commons-fileupload 1.3.1
  (CVE-2016-1000033, fixed in 1.3.3). For a platform holding patient data this matters, but
  the user's directive is exact OPS parity, so both are copied as-is and flagged; a
  patch-level bump (42.2.25 / 1.3.3) stays within the same minor line and is the candidate
  first deviation once the user signs off. Neither jar is on any compile path in this change.
- **Nacos may fail fast at test time despite the flags** (e.g. a registry autoconfiguration
  not covered by `discovery.enabled=false`). → The context-load test is the guard; if it
  fails, add `spring.cloud.service-registry.auto-registration.enabled=false` and, as a last
  resort, exclude the specific Nacos autoconfiguration classes via
  `spring.autoconfigure.exclude`.
- **Jars outside OPS's set (dcm4che, HAPI, RocketMQ) have no OPS verification** → managed in
  the BOM but off every compile path; their verification belongs to the changes that use
  them.
- **Verbatim-copy creep: "small fixes" accumulate silently in `framework`/`common` during
  porting.** → `diff -r` against the OPS originals is a task gate (2.1/2.2); only the five
  recorded adaptations may differ, anything else is a blocking review category per the
  execution contract.
- **`package` is offline-fragile** (L2 audit finding): `maven-jar-plugin` resolves transitives
  (`file-management`, `maven-archiver`, `commons-io`, `commons-lang3`) by origin-checking the
  shared cache, which fails with `-o` unless they were fetched from the current repos. The
  declared L1/L3 gates (`compile`/`test`) are unaffected; CI must either allow network or pin
  `maven-jar-plugin` explicitly. Not fixed in this change (packaging is no task requirement).

## Migration Plan

Greenfield - no existing build to migrate. Rollback is deleting the created `pom.xml` files
and module directories; nothing else references them yet. `openspec/loop-state.yaml` returns
to `phase: proposing` if the change is abandoned before apply.

## Open Questions

None that affect specs, approach, or task breakdown. The exact placeholder content of each
library jar is an implementation detail decided during apply.

# Tasks

> Scaffolding, dependency, and verbatim-copy groups carry no tests of their own; the first
> assertion lands in group 4 with the context-load test, per the design's
> zero-infrastructure guarantee. OPS source of truth for the verbatim copy:
> `/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/{framework,common}`.

## 1. Aggregator and version governance

- [x] 1.1 Create `lumen-sev/pom.xml` (packaging `pom`, groupId `org.birdbro`) declaring
  `<modules>` for `lumen-bom`, `framework`, `common`, `lumen-auth`; set `java.version=11`
  with UTF-8 encoding, `${revision}`=0.1.0 + `flatten-maven-plugin:1.5.0`
  (`resolveCiFriendliesOnly`), and pluginManagement for `maven-surefire-plugin:3.0.0-M5` and
  `maven-compiler-plugin:3.8.1` with annotationProcessorPaths for
  `spring-boot-configuration-processor:2.5.15`, `lombok:1.18.30`, and
  `mapstruct-processor:1.5.5.Final` - mirroring OPS's build mechanism (design D1). Verify:
  `mvn -f lumen-sev/pom.xml validate -q` exits 0.
- [x] 1.2 Create `lumen-bom/pom.xml` (packaging `pom`) carrying OPS's parent
  `dependencyManagement` 1:1 - import `spring-framework-bom:5.3.33`,
  `spring-boot-dependencies:2.5.15`, `spring-cloud-dependencies:2020.0.3`, and
  `spring-cloud-alibaba-dependencies:2021.1` as BOM imports, then declare every managed
  version OPS manages (including `mybatis-plus-boot-starter:3.5.3.1`, `lombok:1.18.30`,
  `hutool-all:5.8.20`, `druid-spring-boot-starter:1.2.18`, `postgresql:42.2.18`,
  `fastjson2:2.0.38`, `easyexcel:3.3.2`, `shiro-core:1.10.1`, `springfox-boot-starter:3.0.0`,
  `jjwt:0.9.1`, `spring-cloud-starter-bootstrap:3.0.3`, `mapstruct:1.5.5.Final`, and the
  remaining OPS entries: commons-io, zxing, TinyPinyin, feign-core, commons-compress,
  commons-fileupload, mybatis-typehandlers-jsr310, UserAgentUtils, httpclient,
  commons-lang3, validation-api, bcprov) - plus the lumen-only additions
  `ca.uhn.hapi:hapi-base:2.6.0`, `ca.uhn.hapi:hapi-structures-v25:2.6.0`,
  `org.dcm4che:dcm4che-core`/`dcm4che-net:5.35.2`, and
  `rocketmq-spring-boot-starter:2.2.3`, and the internal artifacts `framework`/`common`
  managed at `${revision}` (design D1). Verify: `mvn -f lumen-sev/pom.xml -pl lumen-bom
  validate -q` exits 0, and a line-by-line diff against `ops_sev/pom.xml` shows identical
  versions for every library OPS also manages.
- [x] 1.3 Declare the dcm4che repository `https://www.dcm4che.org/maven2` in `lumen-bom`
  (design D2), with no module consuming a dcm4che artifact. Verify:
  `mvn -f lumen-sev/pom.xml dependency:tree -q` shows no `org.dcm4che` node on any module's
  compile path.
- [x] 1.4 Have every child POM inherit from the aggregator and import `lumen-bom`, with no
  third-party `<version>` literal anywhere below `lumen-bom`. Verify:
  `grep -rn "<version>" lumen-sev --include=pom.xml` shows version tags only in
  `lumen-bom/pom.xml` and in parent/module coordinates (never on a third-party dependency).

## 2. OPS base modules verbatim copy (framework + common)

- [x] 2.1 Copy `framework` and `common` from `ops_sev/` into `lumen-sev/` unchanged:
  artifactIds stay `framework`/`common`, packages stay `org.birdbro.framework` /
  `org.birdbro.common`; repoint each copied POM's parent from the OPS parent to the
  `lumen-sev` aggregator (versions resolve through `lumen-bom`). Verify:
  `mvn -f lumen-sev/pom.xml -pl framework,common -am compile -q` exits 0, and
  `diff -r` against the OPS originals shows changes only in the parent block of the two POMs.
- [x] 2.2 Apply exactly the five recorded adaptations, nothing more (design D5):
  (a) make `RedisConfig` / `FastJson2JsonRedisSerializer` conditional on Redis being
  present (`@ConditionalOnClass`/`@ConditionalOnProperty`), so a context loads without a
  Redis server; (b) leave the security classes as-is and record that token issuance is
  `lumen-auth`'s job while other services verify via the copied
  `JwtAuthenticationTokenFilter` against shared Redis (wiring completes in the auth
  feature change); (c) record in the coordination doc that operation-log tables are owned
  per service - each future service's Flyway carries the same op-log schema, the copied
  mappers write that service's own DB; (d) delete
  `common/src/main/java/org/birdbro/common/Constants/DingTalkConstants.java` and
  `common/src/main/java/org/birdbro/common/utils/WxCryptUtil.java` (user instruction -
  OPS-domain baggage, zero references within the copied modules verified); (e) in the copied `common/pom.xml` drop
  the `bcprov-jdk16:1.46` version literal (design D5 adaptation 5) after adding
  `org.bouncycastle:bcprov-jdk16:1.46` to `lumen-bom` management. Verify: `diff -r` against
  OPS shows only these files changed, and each change traces to (a)/(b)/(c)/(d)/(e).
- [x] 2.3 Confirm the base-module boundaries hold. Verify:
  `grep -rn "@SpringBootApplication" lumen-sev/framework lumen-sev/common` returns nothing;
  `grep -rn "spring-boot-starter-webflux\|spring-cloud-starter-gateway"
  lumen-sev/framework lumen-sev/common` returns nothing (no reactive deps may enter); and
  no module other than a Servlet service depends on `framework`/`common` (the gateway
  discipline - there is no gateway module to violate it, and it must stay that way).

## 3. Service shells

- [x] 3.1 Create `lumen-auth` with the entry point
  `org.birdbro.lumen.auth.AuthApplication` (`@SpringBootApplication` with
  `scanBasePackages = "org.birdbro"` AND `@MapperScan("org.birdbro.**.mapper")` - both
  mirroring OPS `LaunchApplication` exactly; the MapperScan is REQUIRED: framework's log
  `@Service` impls autowire mappers, proven by B4 red rung 3; all six future services
  replicate this entry-class contract), `spring-boot-starter-web`, and `bootstrap.yml` +
  `application.yml` setting `spring.application.name: lumen-auth` and `server.port: 8081`.
  No controllers, no security wiring yet. Verify:
  `mvn -f lumen-sev/pom.xml -pl lumen-auth -am compile -q` exits 0.
- [x] 3.2 Wire Nacos discovery + config + `spring-cloud-starter-bootstrap:3.0.3` into
  `lumen-auth` the way OPS does (design D3): remote config via `bootstrap.yml`, address from
  an env placeholder, no committed credentials; add the test profile
  `src/test/resources/application-test.yml` with
  `spring.cloud.nacos.discovery.enabled=false` and `spring.cloud.nacos.config.enabled=false`.
  Verify: `grep -rn "password" lumen-sev --include=*.yml` returns no secret literal, and the
  test profile file exists with both flags false.

## 4. Zero-infrastructure verification

- [x] 4.1 Write a context-load test for `lumen-auth` (`@SpringBootTest` with the `test`
  profile) asserting the context starts, `spring.application.name` resolves to
  `lumen-auth`, and `server.port` resolves to `8081`. Write the test first, watch it fail
  before task 3.2's profile is in place, then make it pass. Verify:
  `mvn -f lumen-sev/pom.xml -pl lumen-auth test -q` exits 0 with Nacos, PostgreSQL, Redis,
  and RocketMQ all stopped - this also proves adaptation (a): the Redis-conditional
  configuration stays inactive with no Redis present.
- [x] 4.2 Run the whole-stack gate from `openspec/verify.config.yaml` on a machine with no
  infrastructure. Verify: `mvn -f lumen-sev/pom.xml compile -q` and
  `mvn -f lumen-sev/pom.xml test -q` both exit 0 (L1 and L3 PASS).
- [x] 4.3 Assert the deferred modules were not created. Verify: none of `lumen-exam-core`,
  `lumen-api`, `lumen-patient`, `lumen-radiology`, `lumen-integration`, `lumen-us`,
  `lumen-endo` - and no gateway module of any name - exists under `lumen-sev/`, and no
  `lumen-common-*` five-way split exists (superseded by verbatim `framework`/`common`).

## 5. Documentation and spec hygiene

- [x] 5.1 Update `openspec/coordination/init-project-skeleton.md`: record the final pinned
  versions if they differ from design D1, the five adaptation diffs if they grew beyond
  design D5, and set this change's registry row to the current status. Verify: the doc's
  Decisions and registry rows match what was built.
- [x] 5.2 Confirm the port table reflects the built reality (nginx 80/443 entry,
  `lumen-auth` 8081) so the frontend proxy target is unambiguous. Verify:
  `init-web-skeleton` reads the nginx port from that table without guessing.
- [x] 5.3 Confirm `.claude/rules/naming.md` matches the built package layout (dual root
  `org.birdbro.{framework,common}` / `org.birdbro.lumen.*`, layering packages, author
  attribution). Verify: the file's rules match the directories created in groups 2-3.
- [x] 5.4 Sync the agent-facing docs: root `CLAUDE.md` + `AGENTS.md` Tech Stack row and
  Project Map, and `lumen-sev/CLAUDE.md` Module Structure, become "Java 11 · Spring Boot
  2.5.15 · Spring Cloud 2020.0.3 · Spring Cloud Alibaba 2021.1 (OPS-verified train) ·
  unified entry nginx (no Java gateway) · base modules `framework`/`common` copied verbatim
  from OPS". Verify: `grep -rn "Java 17\|Boot 3\|lumen-gateway\|lumen-common-" CLAUDE.md
  AGENTS.md lumen-sev/CLAUDE.md` returns nothing, and `diff CLAUDE.md AGENTS.md` shows no
  difference.
- [x] 5.5 Run `openspec validate init-backend-skeleton --strict`. Verify: exit 0 with no
  errors and no warnings.

## Workflow follow-up

- Generate and approve the execution contract (`build-contract.sh`), pass
  `guard.sh proposing applying`, then run `/opsx:apply`.
- After apply, run `/opsx:verify` against the WHEN/THEN scenarios in `specs/`.
- Archive this change only once **both** registered changes verify PASS, per the coordination
  doc's Gate section.

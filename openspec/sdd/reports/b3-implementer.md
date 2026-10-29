# Batch 3 Implementer Report - lumen-auth service shell (tasks 3.1-3.2)

Change: `init-backend-skeleton` · Commit: `6634be5` (dev) · Status: DONE

## What was implemented

Replaced the transient `lumen-auth` stub pom with the real shell. Five files, nothing more:

| File | Content |
|:--|:--|
| `lumen-sev/lumen-auth/pom.xml` | Real shell pom (stub fully replaced, no leftover markers) |
| `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/AuthApplication.java` | Entry point, package `org.birdbro.lumen.auth` |
| `lumen-sev/lumen-auth/src/main/resources/application.yml` | `spring.application.name: lumen-auth`, `server.port: 8081` |
| `lumen-sev/lumen-auth/src/main/resources/bootstrap.yml` | Nacos config + discovery, OPS wiring style |
| `lumen-sev/lumen-auth/src/test/resources/application-test.yml` | Task 3.2 test profile |

### pom.xml
Parent `org.birdbro:lumen-sev:${revision}`; artifactId `lumen-auth`; packaging `jar`.
Dependencies (zero version literals - all resolve through `lumen-bom`, verified by grep:
the pom's only `<version>` tag is the parent's `${revision}`):
`org.birdbro:framework`, `org.birdbro:common`, `org.springframework.boot:spring-boot-starter-web`,
`com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery`,
`com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config`,
`org.springframework.cloud:spring-cloud-starter-bootstrap`, and
`spring-boot-starter-test` (test scope).
NO springfox, NO datasource/redis/mybatis starters, NO build plugins (no
spring-boot-maven-plugin repackage - not requested by the task; D4 forbids repackage for
the shared jars and the executable-jar decision belongs to a later batch).

### AuthApplication.java
`@SpringBootApplication(scanBasePackages = "org.birdbro")` + standard `main` ->
`SpringApplication.run(...)`. Nothing else, per task: no `@EnableDiscoveryClient`
(auto-registration on this train), no controllers, no extra beans. Mirrors the OPS
`LaunchApplication` habit (OPS uses `@ComponentScan("org.birdbro")`; the task specifies the
equivalent `scanBasePackages` form). Deliberately NOT copied from OPS: the
`DataSourceAutoConfiguration` exclude and the various `@Enable*` annotations - the red
context-load test in Batch 4 drives any such need.

### bootstrap.yml - how it mirrors OPS's birdbro-launch/src/main/resources/bootstrap.yaml
OPS structure: `spring.cloud.nacos.config.{file-extension: yaml, server-addr, namespace}` +
`spring.cloud.nacos.discovery.{server-addr, namespace}` + `spring.application.name` +
`server.port`. lumen-auth keeps that exact block structure with three neutralizations
(specs/service-topology "Sensitive Configuration Exclusion"):
1. `server-addr: ${NACOS_ADDR:127.0.0.1:8848}` in both blocks (OPS has a literal IP:port).
2. `namespace: ${NACOS_NAMESPACE:}` (OPS has a literal namespace UUID). Empty default
   resolves to the Nacos default (public) namespace, matching an unset property.
3. No `spring.profiles.active` line (OPS pins `active: test` there - would wrongly force the
   test profile at runtime; Batch 4's test activates `test` via `@ActiveProfiles`).
No credentials, no literal secrets anywhere; `NACOS_ADDR`/`NACOS_NAMESPACE` are the deploy
injection points. `file-extension: yaml` kept so remote data-ids are `<name>.yaml` as in OPS.

### application-test.yml (task 3.2)
`spring.cloud.nacos.discovery.enabled: false`, `spring.cloud.nacos.config.enabled: false`,
`spring.cloud.discovery.enabled: false` - exactly design D3's three flags. NO
`token.*`/`xss.*` dummies added, per the task: if the context-load test reveals missing
placeholders, the red test drives adding them to this file.

## Verification (all from `lumen-sev/`, JAVA_HOME=jdk-11, `-Dmaven.repo.local=/Users/birdbro/work/01-JF/00-Coding/00-Maven/Jar`)

| Command | Exit |
|:--|:--|
| `./mvnw -q compile ...` (full reactor) | 0 |
| `./mvnw -q validate ...` | 0 |
| `./mvnw -q -pl lumen-auth -am compile ...` (tasks.md 3.1 gate) | 0 |
| `grep -rn "password" lumen-sev --include=*.yml` | no match (exit 1) |
| `grep -n "8081" lumen-auth/src/main/resources/application.yml` | matches (comment + `port: 8081`) |
| stub-marker grep over `lumen-auth/` | no match |
| version-tag grep over `lumen-auth/pom.xml` | only parent `${revision}` |

## Batch 4 heads-up: framework properties resolved at context load

`@SpringBootApplication(scanBasePackages = "org.birdbro")` registers the copied framework/
common beans; these placeholders resolve EAGERLY when the context loads (full scan of both
modules - this is the complete list):

1. `token.header`, `token.secret`, `token.expireTime` - `framework/security/TokenService.java`
   is a plain `@Component` (no conditional guard). MISSING today -> the context-load test
   will likely fail on these first; add to `application-test.yml` then.
2. `xss.excludes`, `xss.urlPatterns` - `framework/config/FilterConfig.java` is a
   `@Configuration` and its `@Value` fields sit on the class itself, NOT behind the
   `@ConditionalOnProperty(xss.enabled)` of the filter-bean method - so they are required
   even with XSS filtering off. (OPS values for reference: `/system/notice`,
   `/system/*,/monitor/*,/tool/*`.)
3. `dispose.*` - `framework/global/GlobalProperties.java`
   (`@ConfigurationProperties(prefix = "dispose")`, registered via `GlobalConfig`) has
   defaults (empty lists) -> harmless without configuration.

Infrastructure paths present at context load (expect lazy, no servers needed):
- Redis: `common/core/redis/RedisCache` (`@Component`) needs a `RedisTemplate`;
  `RedisConfig` (adaptation (a)) activates since data-redis is on the classpath
  (`lumen.redis.enabled` defaults true) and Lettuce connects lazily. If the red test trips
  over Redis anyway, `lumen.redis.enabled: false` in `application-test.yml` is the recorded
  adaptation (a) switch.
- DataSource: framework brings `druid-spring-boot-starter` + `postgresql` +
  `mybatis-plus-boot-starter`; `DruidDataSourceAutoConfigure` creates a pooled DataSource
  with no URL and does not connect eagerly. OPS's `LaunchApplication` excludes
  `DataSourceAutoConfiguration` - deliberately not applied here; the red test drives it if
  needed.
- Security: spring-boot-starter-security arrives via framework/common; auto-config generates
  an in-memory user (random password, console-only) - does not block context load.
- Nacos: if the three D3 flags somehow do not neutralize the bootstrap phase, D3's documented
  fallback ladder applies (`spring.cloud.service-registry.auto-registration.enabled=false`,
  then `spring.autoconfigure.exclude`).

## Not done (out of scope, per task boundary)

- No context-load test (Batch 4, task 4.1), no controllers/security wiring, no
  spring-boot-maven-plugin repackage, no openspec/ planning-file edits (task 5.x owns those).

## Addendum - B3 review Critical-1 fix (commit `eb34dd2`)

Reviewer finding: `application-test.yml`'s `spring.cloud.nacos.config.enabled: false` never
reaches the bootstrap phase - with spring-cloud-starter-bootstrap present, the bootstrap
context binds `NacosConfigProperties` from `bootstrap.yml` ONLY; application-test.yml loads
into the main environment afterwards. The config import ran and "succeeded" as empty only
because SCA 2021.1 tolerates the connect failure.

Fix (option A, declarative): appended a test-profile-activated document to
`bootstrap.yml` (`spring.config.activate.on-profile: test` + the same three flags:
`spring.cloud.nacos.config.enabled: false`, `spring.cloud.nacos.discovery.enabled: false`,
`spring.cloud.discovery.enabled: false`). The bootstrap context inherits the run's active
profiles (spring-cloud-context 3.0.3), so the flags now bind during the bootstrap phase.
`application-test.yml` untouched (harmless duplication; stays the home for future
test-only properties). No other files touched.

Evidence: throwaway `@SpringBootTest @ActiveProfiles("test")` probe
(`BootstrapNeutralizationProbeTest`, DELETED after capture - Batch 4 owns the real test),
identical command both runs: `./mvnw -pl lumen-auth -am test` (JDK 11, pinned repo,
nothing listening on 8848):

| Metric | Pre-fix | Post-fix |
|:--|:--|:--|
| `grep -c "8848"` in surefire log | 21 | **0** |
| `grep -c "ServerHttpAgent"` | 15 | **0** |
| Red reason | `Could not resolve placeholder 'xss.excludes'` (filterConfig bean) | **unchanged** - same placeholder, NOT a Nacos error |
| Surefire `Time elapsed` | 2.953 s | **0.975 s** |

Sample pre-fix traffic (all `Connection refused` - SCA logged ERROR and continued, masking
the gap): `c.a.n.c.config.http.ServerHttpAgent : [NACOS ConnectException httpGet]
currentServerAddr:http://127.0.0.1:8848` - matching the reviewer's root cause exactly.
Post-fix: zero Nacos log lines of any kind.

Re-verified after the fix: `./mvnw -q validate` and `./mvnw -q -pl lumen-auth -am compile`
exit 0; `grep -rn "password" lumen-sev --include=*.yml` still returns nothing. The red on
the placeholder is the expected Batch 4 driver: `token.*` / `xss.*` values land in
`application-test.yml` when the task 4.1 test goes green (see heads-up above).


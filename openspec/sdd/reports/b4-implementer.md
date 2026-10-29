# B4 Implementer Report - Batch 4: zero-infrastructure verification (tasks 4.1-4.3)

Change: `init-backend-skeleton` - Batch 4 (strict TDD red -> green)
Commit: `2eecd070c2ebf0da2adfb7b84e79e3cc997bc4e0` (branch `dev`)
Status: **DONE_WITH_CONCERNS** - two green-step decisions go beyond the anticipated
fallback ladder and need gate-4 reviewer blessing (see "Deviations from the anticipated
ladder").

Environment used for every run:
```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home   # JDK 11.0.29
cd lumen-sev && ./mvnw ... -Dmaven.repo.local=/Users/birdbro/work/01-JF/00-Coding/00-Maven/Jar
```
Note on `-am`: the brief's command `./mvnw -pl lumen-auth test` cannot resolve siblings -
only `lumen-bom:0.1.0` is installed in the local repo (the `org/birdbro/framework` and
`org/birdbro/common` 1.0.0 artifacts there are the OPS originals). `-pl lumen-auth -am`
keeps the reactor self-contained, per the reactor-only rule in `lumen-sev/CLAUDE.md`.

## Port checks (before RED and again before the 4.2 gates)

`lsof -iTCP:8848 -sTCP:LISTEN` / `5432` / `6379` / `9876` - **no output, exit 1 each time**:
nothing listening on Nacos, PostgreSQL, Redis, or RocketMQ ports during any run below.

## Task 4.1 - TDD evidence

### Test written FIRST (before any fix)

`lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthApplicationTests.java`:
`@SpringBootTest` + `@ActiveProfiles("test")`, autowires `Environment`; asserts context
load + `spring.application.name` == `lumen-auth` + `server.port` == `8081`. No other
assertions, no security mocks, no endpoints.

### RED

Command:
```
./mvnw -pl lumen-auth -am clean test -Dmaven.repo.local=/Users/birdbro/work/01-JF/00-Coding/00-Maven/Jar
```
(stale Batch-3 probe classes removed via `clean` so surefire runs exactly one test)

Verbatim failing output (console tail):
```
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 1.181 s <<< FAILURE! - in org.birdbro.lumen.auth.AuthApplicationTests
[ERROR] org.birdbro.lumen.auth.AuthApplicationTests.contextLoads  Time elapsed: 0.001 s  <<< ERROR!
java.lang.IllegalStateException: Failed to load ApplicationContext
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'filterConfig': Injection of autowired dependencies failed; nested exception is java.lang.IllegalArgumentException: Could not resolve placeholder 'xss.excludes' in value "${xss.excludes}"
Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder 'xss.excludes' in value "${xss.excludes}"

[ERROR] Errors: 
[ERROR]   AuthApplicationTests.contextLoads » IllegalState Failed to load ApplicationCon...
[INFO] BUILD FAILURE
```
RED classification confirmed: a **property/framework failure** (unresolved placeholder in
the copied `FilterConfig`), NOT a Nacos/Redis connection failure. The RED log already had
`grep -c "8848"` = 0, no Redis error, no Druid error - Batch 3's bootstrap neutralization
held from the first red (no regression).

### The red ladder (each rung observed verbatim, then minimally fixed)

The single placeholder fix was not enough; the context-load test then surfaced three
further rungs, in order:

1. **Placeholders** (RED above) -> test-only properties in `application-test.yml` (below).
2. **Security wiring gap**:
```
Parameter 0 of constructor in org.birdbro.framework.config.SecurityConfig required a bean of type 'org.springframework.security.core.userdetails.UserDetailsService' that could not be found.
```
   OPS services each supply their own `UserDetailsServiceImpl` (e.g. birdbro-manage);
   lumen's is deliberately deferred to the auth feature change (design D5, adaptation 2).
   Minimal test-scope fix: `AuthTestStubConfig` (in `lumen-auth/src/test/java`, imported
   by the test via `@Import`) provides one no-op `UserDetailsService` bean that throws if
   ever invoked. No framework/common class touched; no production wiring completed early.
3. **Missing mapper registration**:
```
Error creating bean with name 'logInfoServiceImpl': Unsatisfied dependency expressed through field 'baseMapper'; nested exception is ... NoSuchBeanDefinitionException: No qualifying bean of type 'org.birdbro.framework.log.mapper.LogInfoMapper' available
```
   OPS's `LaunchApplication` carries `@MapperScan(basePackages = "org.birdbro.**.mapper")`;
   `AuthApplication` (task 3.1) did not. This is a real shell-wiring gap (the shell could
   never boot), so the fix went into the entry class itself (main sources), mirroring the
   cited OPS habit - see Deviations.
4. **DataSource demanded at startup**:
```
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataSource' defined in class path resource [com/alibaba/druid/spring/boot/autoconfigure/DruidDataSourceAutoConfigure.class]: Invocation of init method failed; nested exception is org.springframework.boot.autoconfigure.jdbc.DataSourceProperties$DataSourceBeanCreationException: Failed to determine a suitable driver class
```
   The task-documented fallback (`spring.autoconfigure.exclude:
   com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure`) was applied
   first and proven **insufficient**: Boot's own `DataSourceConfiguration$Hikari` then
   failed identically (`dataSource ... Factory method 'dataSource' threw exception ...
   Failed to determine a suitable driver class`), and excluding BOTH autoconfigurations is
   impossible because the `@MapperScan`'d framework mappers force a DataSource bean
   through `MybatisPlusAutoConfiguration`. Minimal fix that keeps the real chain and
   opens no connection: a dummy unreachable `spring.datasource.url` + driver in the test
   profile (druid initializes with `initialSize=0` -> no physical connection; any
   accidental `getConnection` fails loudly on the invalid host). The druid exclude was
   REVERTED in favor of this (production-parity: the druid wrapper stays in the context).
   Empirically proven: green-run log has zero `test-invalid` / `UnknownHost` /
   `Connection refused` lines - no connection was ever attempted.

### Exact test-only properties added (each traced to a red rung)

In `lumen-sev/lumen-auth/src/test/resources/application-test.yml`:

| Property | Value | Consuming bean (traced) | Red rung |
|:--|:--|:--|:--|
| `token.header` | `test-header` | `framework/security/TokenService.java` `@Value` | rung 1 (same family as `xss.excludes`; source-grep verified - the only `@Value`s in framework/common) |
| `token.secret` | `test-only-dummy-not-a-secret` | `framework/security/TokenService.java` `@Value` | rung 1 (explicit dummy; not a real credential - behavior 16 untouched) |
| `token.expireTime` | `3600` | `framework/security/TokenService.java` `@Value` (int) | rung 1 |
| `xss.excludes` | `/css/*,/js/*,/images/*` | `framework/config/FilterConfig.java` `@Value` | rung 1 - the named RED failure |
| `xss.urlPatterns` | `/*` | `framework/config/FilterConfig.java` `@Value` | rung 1 |
| `spring.datasource.url` | `jdbc:postgresql://test-invalid:5432/test` | druid `DruidDataSourceWrapper` init (via `DataSourceProperties.determineUrl()`), needed by `MybatisPlusAutoConfiguration.sqlSessionFactory` <- `@MapperScan`'d framework log mappers | rungs 2-4 |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | same chain (postgresql 42.2.18 on classpath via framework) | rung 4 |

No `spring.autoconfigure.exclude` remains in the file (applied, proven insufficient,
reverted - see rung 4). No `xss.enabled` added (its filter `@Bean` is
`@ConditionalOnProperty` and stays off; only the two `@Value` fields were demanded). No
username/password (druid init accepts none; avoids any `password` literal in YAML).

### GREEN

Command:
```
./mvnw -pl lumen-auth -am test -Dmaven.repo.local=/Users/birdbro/work/01-JF/00-Coding/00-Maven/Jar
```
Verbatim passing output:
```
2026-10-29 15:18:23.012  INFO 22587 --- [           main] o.b.lumen.auth.AuthApplicationTests      : Started AuthApplicationTests in 1.906 seconds (JVM running for 2.221)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.192 s - in org.birdbro.lumen.auth.AuthApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Reactor Summary for lumen-sev 0.1.0:
[INFO] lumen-auth ......................................... SUCCESS [  2.604 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  3.173 s
```

### Neutrality evidence (on the GREEN run log)

| Check | Result |
|:--|:--|
| `grep -c "8848" green.log` | **0** (required: 0) |
| Nacos agent/connection attempts (`ServerHttpAgent`, `NacosConnectionFailure`) | 0 |
| Redis connection errors (`RedisConnectionFailure`, lettuce, "Unable to connect to Redis") | **0** - adaptation (a) proven: Redis absent is harmless |
| DB connect attempts (`test-invalid`, `UnknownHost`, `Connection refused`) | 0 - no datasource connection opened |

## Task 4.2 - whole-stack gates (no infrastructure running)

| Gate | Command | Result |
|:--|:--|:--|
| compile (L1) | `./mvnw -q compile -Dmaven.repo.local=...` | **exit 0** |
| test (L3) | `./mvnw -q test -Dmaven.repo.local=...` | **exit 0** (only lumen-auth has tests; surefire report: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.042 s`) |
| neutrality on full-test log | `grep -c "8848" full-test.log` | **0** |

Ports re-verified immediately before the gates: nothing listening on 8848 / 5432 / 6379 /
9876 (`lsof -sTCP:LISTEN` empty for all four).

## Task 4.3 - deferred modules absent

`ls lumen-sev/`:
```
AGENTS.md
CLAUDE.md
common
framework
lumen-auth
lumen-bom
mvnw
mvnw.cmd
pom.xml
```
`find lumen-sev -maxdepth 1 -type d \( -name lumen-exam-core -o -name lumen-api -o -name
lumen-patient -o -name lumen-radiology -o -name lumen-integration -o -name lumen-us -o
-name lumen-endo -o -iname '*gateway*' -o -name 'lumen-common-*' \)` -> **0 hits**.
Aggregator `<modules>`: exactly `lumen-bom`, `framework`, `common`, `lumen-auth`.

## Deviations from the anticipated ladder (need gate-4 blessing)

1. **`AuthTestStubConfig` (new, test-only)** - the brief anticipated only property fixes
   plus a possible druid exclude; the red run surfaced the `SecurityConfig`
   constructor-injection gap that no property can fix. The stub is confined to
   `lumen-auth/src/test/java`, is imported explicitly by the test, throws if invoked, and
   leaves the verbatim guarantee intact (`framework`/`common` untouched this batch).
2. **`@MapperScan(basePackages = "org.birdbro.**.mapper")` added to `AuthApplication`
   (main sources)** - red rung 3 proved the task-3.1 entry class incomplete (the shell
   could never boot without it; OPS's `LaunchApplication` always carries it). Recorded in
   the class Javadoc; tasks.md 3.1's wording ("scanBasePackages ... OPS LaunchApplication
   habit") arguably already implied it, but the addition is a Batch 4 green-step
   completion of 3.1 and should be reflected in Batch 5 doc updates.
3. **The task-documented druid-`autoconfigure.exclude` fallback was applied and
   REVERTED** - proven insufficient (Hikari rung) and replaced by the dummy-URL approach
   that keeps the production-shaped druid wrapper without any connection. Both variants
   are recorded above so the reviewer can audit the choice.

## Commit

`2eecd070c2ebf0da2adfb7b84e79e3cc997bc4e0` - `✅ test(sev): Batch 4 - zero-infrastructure
context-load verification (init-backend-skeleton 4.1-4.3)`; 4 files:
`AuthApplication.java` (+@MapperScan), `AuthApplicationTests.java` (new),
`AuthTestStubConfig.java` (new), `application-test.yml` (test-only properties). No
`openspec/` files committed; `framework`/`common` byte-untouched.

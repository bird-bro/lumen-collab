# Batch 2 Implementer Report - init-backend-skeleton (tasks 2.1-2.3)

- **Status**: DONE
- **Commit**: `ab2baa7bde8d94a0206802eb6cb70d88b0779872` (dev) - 93 files, +7434/-14
- **Source of truth**: `/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/{framework,common}`
- **Copied into**: `/Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev/{framework,common}` (pom.xml + src/ only; no `target/`, `logs/`, IDE files, `.DS_Store`)

## 1. Verbatim evidence

### 1.1 `diff -rq` OPS framework/src vs lumen-sev/framework/src (exit 1 = differences listed)

```
Files /Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/framework/src/main/java/org/birdbro/framework/config/RedisConfig.java and lumen-sev/framework/src/main/java/org/birdbro/framework/config/RedisConfig.java differ
```

Only `RedisConfig.java` differs - adaptation (a). Full content of that diff:

```diff
--- OPS framework/src/main/java/org/birdbro/framework/config/RedisConfig.java
+++ lumen-sev/framework/src/main/java/org/birdbro/framework/config/RedisConfig.java
@@ -3,6 +3,8 @@
 import com.fasterxml.jackson.annotation.JsonTypeInfo;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
+import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
+import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
 import org.springframework.cache.CacheManager;
 import org.springframework.cache.annotation.CachingConfigurerSupport;
 import org.springframework.cache.annotation.EnableCaching;
@@ -28,8 +30,14 @@
 /**
  * redis配置 - 合并缓存管理配置
  *
+ * <p>lumen adaptation (a), init-backend-skeleton design D5: this configuration is
+ * conditional so a service context also loads with no Redis server / no Redis jar
+ * (Redis is optional in the hospital form).</p>
+ *
  * @author ruoyi
  */
+@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisOperations")
+@ConditionalOnProperty(name = "lumen.redis.enabled", havingValue = "true", matchIfMissing = true)
 @Configuration
 @EnableCaching
 public class RedisConfig extends CachingConfigurerSupport {
```

### 1.2 `diff -rq` OPS common/src vs lumen-sev/common/src (exit 1 = differences listed)

```
Only in /Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/common/src/main/java/org/birdbro/common/Constants: DingTalkConstants.java
Only in /Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/common/src/main/java/org/birdbro/common/utils: WxCryptUtil.java
```

Only the two deleted files - adaptation (d). All 90 other copied source files are byte-identical.

### 1.3 framework/pom.xml vs OPS original (full diff)

```diff
8c8
<         <artifactId>birdbro</artifactId>
---
>         <artifactId>lumen-sev</artifactId>
```

Only the parent block (task 2.1). Every dependency and the `<name>` line untouched.

### 1.4 common/pom.xml vs OPS original (full diff)

```diff
8c8
<         <artifactId>birdbro</artifactId>
---
>         <artifactId>lumen-sev</artifactId>
81d80
<             <version>1.46</version>
```

Parent block (2.1) + the bcprov literal removal (e). `org.bouncycastle:bcprov-jdk16` dependency itself kept, no other dependency touched.

## 2. Adaptations applied (exactly D5's five, nothing more)

| # | Adaptation | File(s) | Change |
|:--|:--|:--|:--|
| (a) | Redis conditional | `lumen-sev/framework/src/main/java/org/birdbro/framework/config/RedisConfig.java` | Added imports `org.springframework.boot.autoconfigure.condition.{ConditionalOnClass,ConditionalOnProperty}` and the two class-level annotations exactly as prescribed. File pre-read: no pre-existing conditionals; bean structure matched expectation (`redisTemplate`/`cacheManager`/`limitScript`, `CachingConfigurerSupport`, `@EnableCaching`) - no equivalent guard needed. `FastJson2JsonRedisSerializer` untouched (verified referenced only by RedisConfig - it is a plain class, only `new FastJson2JsonRedisSerializer(...)` inside RedisConfig's `redisTemplate` bean). Note: because `RedisConfig` is discovered by component scanning, the conditions are evaluated on ASM metadata before the class loads, so the string-form `@ConditionalOnClass(name=...)` guard is safe even if the redis jar were absent. |
| (b) | Security wiring boundary | none | No code change. `TokenService` / `JwtAuthenticationTokenFilter` / `SecurityConfig` copied as-is, no comments added; wiring is recorded in design D5 + coordination doc. |
| (c) | Operation-log ownership | none | No code change, no comments. Recorded in design D5 + coordination doc. |
| (d) | Business-baggage deletion | `lumen-sev/common/src/main/java/org/birdbro/common/Constants/DingTalkConstants.java`, `lumen-sev/common/src/main/java/org/birdbro/common/utils/WxCryptUtil.java` | Deleted. Pre-deletion grep: zero references inside the copied tree (only the files' own declarations matched). |
| (e) | Governance alignment | `lumen-sev/common/pom.xml`, `lumen-sev/lumen-bom/pom.xml` | Removed `<version>1.46</version>` from `bcprov-jdk16`; added `org.bouncycastle:bcprov-jdk16:1.46` to lumen-bom dependencyManagement under the "Lumen-only additions" comment (with an explanatory comment: OPS declares it only as a child-POM literal, not in its parent's depMgmt). Resolved version unchanged at 1.46. |

## 3. Boundary verification (task 2.3)

| Check | Command | Result |
|:--|:--|:--|
| No entry point in base modules | `grep -rn "@SpringBootApplication" lumen-sev/framework lumen-sev/common` | no output (grep exit 1 = zero matches) - PASS |
| No reactive deps in base modules | `grep -rn "spring-boot-starter-webflux\|spring-cloud-starter-gateway" lumen-sev/framework lumen-sev/common` | no output (grep exit 1 = zero matches) - PASS |
| Dependency consumers of framework/common | inspected all poms | `framework` -> `common` (expected, allowed); `common` -> none; `lumen-auth` stub -> none (Batch 3 wires it); `lumen-bom` manages `framework`/`common` at `${revision}` (BOM management, not consumption) - PASS |

## 4. Build verification

| Command (JAVA_HOME=JDK 11, `-Dmaven.repo.local=/Users/birdbro/work/01-JF/00-Coding/00-Maven/Jar`) | Exit |
|:--|:--|
| `./mvnw -q compile` (all 4 modules) | 0 |
| `./mvnw -q validate` | 0 |
| `./mvnw compile` (reactor evidence) | BUILD SUCCESS - `lumen-bom`, `lumen-sev`, `common`, `framework`, `lumen-auth` all SUCCESS; reactor correctly ordered `common` before `framework` |

Regression: `grep -rn "<version>" lumen-sev --include=pom.xml` shows only parent/module `${revision}` coordinates and the aggregator's build-plugin properties - no third-party dependency literal outside `lumen-bom/pom.xml` (task 1.4 rule intact; the bcprov literal is gone).

## 5. Surprises found in the copied code

1. **Design D5's "zero references in the OPS source" is true for the copied modules, not the whole OPS repo.** A full `ops_sev` grep (excluding `target/`) found `DingTalkConstants` consumers in `birdbro-operation` (`DingTalkUtil`, `ExpenseSyncServiceImpl`) and a `WxCryptUtil` consumer in `birdbro-manage` (`WxServiceImpl`). Those are OPS business modules that are **not** part of the verbatim copy and have no lumen equivalent (DingTalk approval flows / WeChat login are exactly the OPS-domain baggage adaptation (d) removes), so the deletion cannot break anything in lumen-collab. Not a blocker - but task 5.1 may want to record the precise scope of the "zero references" claim in the coordination doc.
2. **OPS's `RedisConfig.java` has duplicate imports** (`java.time.Duration`, `java.util.HashMap`, `java.util.Map` each imported twice, lines 20-26). Legal Java, harmless - copied verbatim, not "fixed" (would be unrecorded drift).
3. **`RedisConfig` mixes two serializer styles**: the `redisTemplate` bean uses `FastJson2JsonRedisSerializer` while `cacheManager()` builds its own `Jackson2JsonRedisSerializer`. OPS behavior, copied as-is; no action for this change.
4. **`FastJson2JsonRedisSerializer.AUTO_TYPE_FILTER` keys off `Constants.JSON_WHITELIST_STR`** (from `common`), confirming the framework->common dependency is load-bearing at class-init time - one more reason the `framework`->`common` edge must stay.

# Batch 1 Implementer Report - init-backend-skeleton (tasks 1.1-1.4)

Status: **DONE_WITH_CONCERNS** (all verification gates green; two letter-of-the-brief
deviations documented below, both forced by Maven semantics with evidence, both preserving
the brief's stated intent. Flagged for the blocking post-Batch-1 review.)
Commit: `00cca6271a8bdba6e2967f369990bc8d7a251d66` (branch `dev`)

## What was implemented

### Task 1.1 - Aggregator `lumen-sev/pom.xml`
- `org.birdbro:lumen-sev:${revision}` (0.1.0), packaging `pom`; `<modules>`:
  `lumen-bom`, `framework`, `common`, `lumen-auth`.
- Properties: `revision`=0.1.0, `java.version`=11, `maven.compiler.source/target`=`${java.version}`,
  both UTF-8 encodings, `maven-surefire-plugin.version`=3.0.0-M5,
  `maven-compiler-plugin.version`=3.8.1, `flatten-maven-plugin.version`=1.5.0
  (+ `spring.boot.version`/`lombok.version`/`mapstruct.version` used by the build mechanism, mirroring OPS).
- pluginManagement: surefire 3.0.0-M5; compiler 3.8.1 with `annotationProcessorPaths`
  (`spring-boot-configuration-processor:2.5.15`, `lombok:1.18.30`, `mapstruct-processor:1.5.5.Final`).
- plugins: flatten-maven-plugin 1.5.0, `resolveCiFriendliesOnly` + `updatePomFile=true`,
  `flatten`/`flatten.clean` executions - OPS's exact plugin block copied.
- dependencyManagement: imports `org.birdbro:lumen-bom:${project.version}` (pom/import) so
  every child inherits the managed set transitively (the mechanism Batch 2's verbatim
  parent-block-only pom diffs rely on).

### Task 1.2 - `lumen-bom/pom.xml`
- packaging `pom`; dependencyManagement: 4 BOM imports then OPS's parent entries 1:1
  (nacos discovery/config starters, spring-cloud-starter-bootstrap 3.0.3,
  spring-boot-configuration-processor 2.5.15 optional, druid 1.2.18, postgresql 42.2.18,
  shiro-core 1.10.1, springfox-boot-starter 3.0.0, lombok 1.18.30, jjwt 0.9.1, commons-io
  2.13.0, zxing core 3.3.3, hutool-all 5.8.20, TinyPinyin 2.0.3.RELEASE, fastjson2 2.0.38,
  feign-core 12.1, commons-compress 1.21, commons-fileupload 1.3.1,
  mybatis-plus-boot-starter / mybatis-plus-generator 3.5.3.1, mybatis-typehandlers-jsr310
  1.0.2, UserAgentUtils 1.21, easyexcel 3.3.2) + `org.mapstruct:mapstruct:1.5.5.Final`
  (per task 1.2's explicit list; value = OPS's own mapstruct-processor version) + internal
  `framework`/`common` at `${revision}`.
- Lumen-only additions (flagged not-OPS-verified, off every compile path):
  `hapi-base`/`hapi-structures-v25` 2.6.0, `dcm4che-core`/`dcm4che-net` 5.35.2,
  `rocketmq-spring-boot-starter` 2.2.3.
- Transient stub poms created for `framework`, `common`, `lumen-auth`
  (parent = aggregator, packaging jar, no deps, no source; replaced in Batches 2-3).

### Task 1.3 - dcm4che repository
- `<repositories>` in `lumen-bom`: id `dcm4che-repo`, url `https://www.dcm4che.org/maven2`
  (releases on, snapshots off). No module consumes any `org.dcm4che` artifact.

### Task 1.4 - child inheritance / no version literals
- All three stubs inherit from the aggregator and resolve versions through the aggregator's
  `lumen-bom` import. No third-party `<version>` literal in any child POM (grep evidence below).

## Verification evidence (all with JDK 11 + warm cache
`-Dmaven.repo.local=/Users/birdbro/work/01-JF/00-Coding/00-Maven/Jar`, run from `lumen-sev/`)

| Gate | Command | Result |
|:--|:--|:--|
| 1.1 validate | `./mvnw -q validate` | exit 0 |
| 1.2 validate | `./mvnw -q -pl lumen-bom validate` | exit 0 |
| 1.3 tree | `./mvnw dependency:tree` | exit 0; `grep -c "org.dcm4che\|ca.uhn.hapi\|rocketmq"` on full output = **0** (5 reactor projects, no integration-library node) |
| 1.4 grep | `grep -rn "<version>" lumen-sev --include=pom.xml` | version tags only in `lumen-bom/pom.xml` (the BOM), parent/module coordinates, and the aggregator's build-mechanism plugin versions (required by task 1.1); **zero** on a third-party dependency |
| compile | `./mvnw -q compile` | exit 0 (flatten ran; `.flattened-pom.xml` generated with `${revision}` -> `0.1.0`, gitignored) |
| 1.2 parity | `./mvnw -N help:effective-pom` on `ops_sev/pom.xml` vs `lumen-bom/pom.xml`, key-level diff | 1454 shared keys, **0 version mismatches**; only diffs: 5 OPS `birdbro-*` internal module entries correctly absent, `framework`/`common` at lumen's `0.1.0` (ours), 6 flagged lumen-only extras |

## Interpretations / deviations (the two concerns)

1. **`lumen-bom` has NO `<parent>` (standalone reactor module) - brief said "parent = aggregator".**
   The brief's combination (aggregator's depMgmt imports `lumen-bom` + `lumen-bom`'s parent is
   the aggregator) is invalid in Maven: `./mvnw -q validate` fails with
   `The dependencies of type=pom and with scope=import form a cycle:
   org.birdbro:lumen-sev:0.1.0 -> org.birdbro:lumen-bom:0.1.0 -> org.birdbro:lumen-bom:0.1.0`.
   Fix keeps the intent: `lumen-bom` stays in the aggregator `<modules>` (built in-reactor,
   `-pl lumen-bom` works), declares its own `groupId`/`${revision}` (`<revision>0.1.0</revision>`
   self-contained) plus OPS's exact flatten plugin block (not inherited). Children still get
   every version transitively via the aggregator's import, so Batch 2's copied poms need
   parent-block-only changes. Alternative rejected: per-child BOM imports would violate the
   Batch 2 verbatim gate (`diff -r` = parent block only).
2. **BOM import order = OPS's own (cloud, alibaba, framework, boot) - brief said "framework,
   boot, cloud, alibaba".** The brief's order is not resolution-neutral: effective-pom diff
   showed `com.squareup.okhttp3:okhttp`/`logging-interceptor` resolving at **3.14.9**
   (Boot 2.5.15's pin, imported first) instead of OPS's **3.14.4** (from
   `spring-cloud-kubernetes-dependencies:2.0.3`, imported transitively by
   `spring-cloud-dependencies:2020.0.3`, which precedes Boot in OPS's order). That is unrecorded
   drift under behaviors 21/22. Reordering to OPS's exact order reproduces OPS's effective
   dependencyManagement byte-for-byte (diff now clean) - design D1 "mirroring OPS's own
   structure". The comment in `lumen-bom/pom.xml` records why.

Also noted: tasks.md 1.2 lists `httpclient`, `commons-lang3`, `validation-api`, `bcprov` as
"remaining OPS entries" - the OPS parent pom manages none of these (httpclient/commons-lang3/
validation-api resolve via `spring-boot-dependencies`, which is imported; `bcprov-jdk16:1.46`
is a literal inside OPS's `common/pom.xml`, untouched by the verbatim copy in Batch 2). They
were therefore NOT added to `lumen-bom`, keeping the "OPS parent managed set 1:1" rule exact.

## Files changed (commit `00cca62`)

- `.gitignore` (+`.flattened-pom.xml` for flatten output)
- `lumen-sev/pom.xml` (aggregator)
- `lumen-sev/lumen-bom/pom.xml`
- `lumen-sev/framework/pom.xml`, `lumen-sev/common/pom.xml`, `lumen-sev/lumen-auth/pom.xml` (transient stubs)
- `lumen-sev/mvnw`, `lumen-sev/mvnw.cmd`, `lumen-sev/.mvn/{maven.config,settings.xml,wrapper/maven-wrapper.properties}` (pre-existing uncommitted harness, committed per brief)

Not committed (out of scope): openspec planning files, loop-state, reports, docs, CLAUDE/AGENTS docs (Batch 5).

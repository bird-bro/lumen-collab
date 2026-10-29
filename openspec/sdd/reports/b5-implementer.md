# Batch 5 Implementer Report - doc/spec hygiene (init-backend-skeleton 5.1-5.5)

> Verification-first batch: every doc claim checked against built reality
> (HEAD at start: `1283b58`). All five tasks verified; four drift items found and fixed.
> `openspec/loop-state.yaml` untouched (coordinator owns it - see Drift-noted below).

## Per-task verification

| # | Claim verified | Command | Result | Fixed? |
|:--|:--|:--|:--|:--|
| 5.1 | Coordination Decisions match built reality | Read doc + greps below | PASS (after registry fix) | registry row fixed; decision texts confirmed accurate |
| 5.1 | Registry: backend row status | Read `openspec/coordination/init-project-skeleton.md` | said `proposing` | FIXED -> `applied (verify pending)`; `init-web-skeleton` left `proposing` |
| 5.1 | Version train decision vs `lumen-bom/pom.xml` | grep property values | Java 11 (aggregator `java.version=11`), Boot 2.5.15, Framework BOM 5.3.33, Cloud 2020.0.3, SCA 2021.1, MP 3.5.3.1, Lombok 1.18.30, Hutool 5.8.20, Druid 1.2.18, PG 42.2.18, fastjson2 2.0.38, easyexcel 3.3.2, shiro 1.10.1, springfox 3.0.0, jjwt 0.9.1, bootstrap 3.0.3, mapstruct 1.5.5.Final, bcprov-jdk16 1.46, hapi 2.6.0, dcm4che 5.35.2, rocketmq 2.2.3 - all match the decision text and design D1 | no |
| 5.1 | Entry-class contract / D3 amendment / B1 build-mechanics / two products / five adaptations decisions | Read doc vs `AuthApplication.java`, design.md D1/D5, B1-B4 reports | doc matches built reality (contract text byte-matches tasks.md 3.1; D5 ledger = five) | no |
| 5.2 | Port table: nginx 80/443 (unified entry, no Java gateway), `lumen-auth` 8081 | Read coordination table + `lumen-sev/lumen-auth/src/main/resources/application.yml` | table says 80/443 + 8081; `application.yml` has `server.port: 8081`, `spring.application.name: lumen-auth` | no |
| 5.2 | Frontend proxy target unambiguous | `ls lumen-web/vite.config.ts` | does not exist (frontend change still `proposing`); backend side + table agree; `init-web-skeleton` reads the nginx port from this table when it lands | no |
| 5.3 | naming.md vs built layout | `ls lumen-sev` -> `lumen-bom framework common lumen-auth`; `find lumen-sev -name "*.java" -path "*org/birdbro*"` -> `org/birdbro/framework/...`, `org/birdbro/lumen/auth/AuthApplication.java` | dual root + layering + author attribution + library-boundary rule all match | 3 rule-text drifts fixed (below) |
| 5.4 | `grep -rn "Java 17\|Boot 3\|lumen-gateway\|lumen-common-" CLAUDE.md AGENTS.md lumen-sev/CLAUDE.md lumen-sev/AGENTS.md` | grep | exit 1 (no match) after fixes - `lumen-sev/AGENTS.md` had 4 stale lines pre-fix | FIXED |
| 5.4 | `diff CLAUDE.md AGENTS.md` | diff | no difference | no |
| 5.4 | `diff lumen-sev/CLAUDE.md lumen-sev/AGENTS.md` | diff | differed (AGENTS.md stale) | FIXED - synced AGENTS.md from fresh CLAUDE.md; now identical |
| 5.4 | `lumen-sev/CLAUDE.md` build commands use `./mvnw` + JDK 11 JAVA_HOME + reactor-only note | Read Build Commands section | present (`export JAVA_HOME=.../jdk-11.jdk/...`, `./mvnw compile/test -q`, Reactor-only rule paragraph) | no |
| 5.5 | `openspec validate init-backend-skeleton --strict` | command | exit 0 (see output below) | no |
| 5.5 | `python3 openspec/validate-artifacts.py openspec/changes/init-backend-skeleton` | command | `All artifacts valid`, exit 0 | no |

## Drift found and fixed

1. **Registry status stale** (`openspec/coordination/init-project-skeleton.md`): backend row
   `proposing` -> `applied (verify pending)`. Frontend row untouched (`proposing`).
2. **Adaptation-count drift** (`lumen-sev/CLAUDE.md`): "only the **three** recorded adaptations
   may differ" -> "**five**" (B2 review normalized the ledger to five; design D5 title says
   "exactly five adaptations"). Same stale count in `openspec/changes/init-backend-skeleton/tasks.md`
   task 2.2 ("four recorded adaptations" -> "five"; the line already enumerated (a)-(e)).
3. **Stale mirror** (`lumen-sev/AGENTS.md`): still Java 17 + Boot 3.x + `lumen-gateway` +
   `lumen-common-{web,mybatis}` (4 lines). Synced byte-for-byte from the fresh
   `lumen-sev/CLAUDE.md`.
4. **naming.md vs decisions** (`.claude/rules/naming.md`):
   - package list had `org.birdbro.lumen.{...,ris,...}` while the same file's rule (and the
     coordination "Department module naming" decision) forbids RIS in package names ->
     `radiology`, matching the coordination decision verbatim;
   - application-class example `org.birdbro.lumen.ris.RisApplication` -> built
     `org.birdbro.lumen.auth.AuthApplication`;
   - "one uniform rule - `lumen-<department English name>`" contradicted the coordination
     decision ("each domain's most recognizable short name", not strict uniformity) and held a
     Chinese token in an auto-loaded file -> rewritten per the coordination wording, English only.

## Task 5.5 final output

```
$ openspec validate init-backend-skeleton --strict
Change 'init-backend-skeleton' is valid
(exit 0 - no errors, no warnings)

$ python3 openspec/validate-artifacts.py openspec/changes/init-backend-skeleton
  All artifacts valid
(exit 0)
```

## Drift noted, NOT fixed (out of my scope)

- `openspec/loop-state.yaml`: `tasks_total: 17` but `tasks_done: 0`, `batches_completed: 0`
  while tasks.md shows 17/17 checked and batches 1-5 executed. Coordinator-owned; left as-is.

## Files changed in this batch

- `openspec/coordination/init-project-skeleton.md` (registry row)
- `lumen-sev/CLAUDE.md` (adaptation count three -> five)
- `lumen-sev/AGENTS.md` (resynced from CLAUDE.md)
- `.claude/rules/naming.md` (package list, example class, department-naming wording)
- `openspec/changes/init-backend-skeleton/tasks.md` (5.1-5.5 checked; 2.2 count corrected)
- `openspec/sdd/reports/b5-implementer.md` (this file)

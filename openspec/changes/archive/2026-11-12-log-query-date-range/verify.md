<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: log-query-date-range
  date: 2026-11-11
  overall: PASS
  stacks:
    lumen-sev:
      L1: PASS
      L2: "9/9"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "9/9"
      L3: PASS
---

# Verification: log-query-date-range

Overall: **PASS** — 18/18 scenarios IMPLEMENTED (9 system-data + 9 web-system-console)

## Per-stack

### lumen-sev
| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `./mvnw compile -q` (JDK 11); exit=0; logs/build-lumen-sev.log |
| L2 spec | 9/9 | all IMPLEMENTED (evidence below) |
| L3 tests | PASS | `./mvnw test -q` full reactor (team test DB); exit=0; logs/test-lumen-sev.log |

### lumen-web
| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `pnpm build`; exit=0; logs/build-lumen-web.log |
| L2 spec | 9/9 | all IMPLEMENTED (evidence below) |
| L3 tests | PASS | `pnpm test` Vitest 292/292 (24 files); exit=0; logs/test-lumen-web.log |

## Scenario alignment — system-data (§ Log Query Endpoint)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Date-range bounds filter action_time | IMPLEMENTED | LogController.java:90-99 (parse + double-closed conversion + call) → LogInfoServiceImpl.java:131-140 (`.ge`/`.lt` + `date_ym` IN), monthKeys :154; integration AuthLogQueryTests.java:156 |
| Single-bound range | IMPLEMENTED | LogController.java:97-98 null guards; service :139-140 conditional bounds; test single-bound assertions |
| Invalid range rejected | IMPLEMENTED | LogController.java:92-93 (inverted → `BusinessException("A0400")`), :103-110 (`parseDay` strict `yyyy-MM-dd`); test `assertRangeA0400` ×3 |
| Existing filters unchanged (additive guarantee) | IMPLEMENTED | LogController.java:99 passes `type/uid/account/dateYm` through; old overload intact ILogInfoService.java:55; pre-existing test still green in L3 |
| Query operation logs | IMPLEMENTED | LogController.java:99 → LogInfoServiceImpl.java:126-141, actionTime DESC |
| Account filter matches sign and operation rows | IMPLEMENTED | LogInfoServiceImpl `.eq(account)` — single `log_info` table, both families |
| Integer type filter | IMPLEMENTED | LogInfoServiceImpl `.eq(type)`; non-integer → `MethodArgumentTypeMismatchException` → GlobalExceptionHandler A0400 |
| Sign-log endpoint is gone | IMPLEMENTED | `grep -rn "logs/sign" lumen-system/src/main/java` → 0 hits |
| Session column excluded | IMPLEMENTED | LogInfoServiceImpl.java:133 select-exclusion in the new overload |

## Scenario alignment — web-system-console (§ Operation Log Query)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Filtered log query | IMPLEMENTED | logs/index.tsx:74-96 (reload with AND-combined filters); test logs-page.test.tsx:115 |
| Date-range drives beginDate/endDate | IMPLEMENTED | logs/index.tsx:85-89 (`yyyy-MM-dd`, both-or-none) + runQuery page reset; test :139 |
| Presets populate both bounds | IMPLEMENTED | logs/index.tsx:131-136 (今日/近7天/近30天/本月); tests :139 (今日), :186 (近7天 span) |
| Clearing the range removes both params | IMPLEMENTED | logs/index.tsx:190-192 (onChange → null); test :208 |
| Dict values render in the 类别 dropdown (guard) | IMPLEMENTED | logs/index.tsx:126-128 (`label` mapping — the 2026-11-11 fix); test :152-158 (option text = dict value) |
| Dict-driven category filter and labels | IMPLEMENTED | logs/index.tsx:143 column `resolveDictLabel` + Select options; degrade test :235 |
| Sign rows show em-dash | IMPLEMENTED | logs/index.tsx:144-148 (`v ?? '—'` / `v || '—'`); test :246 |
| Read-only surface | IMPLEMENTED | no mutation controls; test :284 |
| Menu-driven visibility follows the page permission | IMPLEMENTED | utils/perm.ts:16-24 (session `perms` gate) + logs/index.tsx:50 `usePerm()`; menu tree served permission-filtered server-side |

## Issues

(none — no PARTIAL, no NOT_IMPLEMENTED)

# Batch 3 Brief - sign-log-query / V14 menu seed + probe cleanup (tasks 3.1 + b2 follow-up)

## Task text (openspec/changes/sign-log-query/tasks.md + b2-review Important #1)

- 3.1 Write `lumen-sev/lumen-system/src/main/resources/db/migration/V14__sign_log_menu.sql`:
  menu row `(17, 16, 'C', '登录日志', 'system:signlog:list', 'signlog', 'system/log/sign',
  NULL, 3)` under 安全 (16) + role_menu grant to role 1 + setval resync, V5/V8 idempotence
  discipline.
- 3.2 (b2-review follow-up) Make `AuthSignLogQueryTests.cleanupMenu` a HARD delete
  (role_menu rows first - FK - then menu_info), and purge the already-polluted probe rows
  from the team test DB. Verify: suite green (74/74).

## Read first

1. `openspec/changes/sign-log-query/design.md` - D8 (seed shape)
2. `V8__menu_security_group.sql` - THE pattern: explicit id insert, guarded grant, setval
3. `V5__console_menus.sql` - menu_info column tuple + idempotence preamble rationale
4. `lumen-system/src/test/java/org/birdbro/lumen/system/AuthSignLogQueryTests.java` -
   `seedSignLogMenu`/`cleanupMenu` (the fixture you are amending; do NOT touch the
   assertions or the two-directional perm pins)

## CRITICAL context from the b2 review (why 3.2 exists)

The b2 fixture soft-deletes its per-run probe menus; each suite run consumed sequence ids,
and the shared test DB now holds soft-deleted probe rows at id 17 (`path LIKE
'b2signlogmenu_%'`). A V14 `ON CONFLICT (id) DO NOTHING` would SILENTLY NO-OP on that DB
(the real 登录日志 menu never gets created there) while passing on fresh DBs - a masking
discrepancy. Two-layer fix, BOTH required:

1. **V14 conflict clause = revive semantics**: `ON CONFLICT (id) DO UPDATE SET up_id = 16,
   title = '登录日志', perms = 'system:signlog:list', path = 'signlog',
   component = 'system/log/sign', icon = NULL, sort = 3, deleted = 0,
   update_time = CURRENT_TIMESTAMP` - converges on fresh AND probed DBs. Document in the
   header WHY this deviates from V8's DO NOTHING (probe-pollution lesson, b2 review).
   The role_menu grant keeps the V8 guarded INSERT SELECT (NOT EXISTS on the live row).
2. **Fixture hard-delete + one-time purge**: `cleanupMenu` deletes `system.role_menu`
   rows for the probe menu ids FIRST (FK `V1__auth_schema.sql:122`), then the
   `system.menu_info` rows. One-time test-DB purge via psql (params from gitignored
   `lumen-sev/.env-test.local`, read-only for the command, never print values):
   first SELECT the polluted set (`SELECT id, path, deleted FROM system.menu_info
   WHERE path LIKE 'b2signlogmenu_%'`), delete referencing `system.role_menu` rows,
   then hard-DELETE the menu rows; show before/after id sets in the report. Do NOT
   delete anything else; `MAX(id)` may legitimately stay > 17 afterwards (sequence
   advancement is harmless - V14 uses the explicit id 17).

## Verification

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw test -q     # boot applies V14 to the test server, then 74/74
```

psql checks (in addition to the purge evidence):
```sql
SELECT id, up_id, type, title, perms, path, component, sort, deleted
  FROM system.menu_info WHERE id = 17;                       -- the real row, deleted 0
SELECT version, success FROM system.flyway_schema_history
 WHERE version IN ('13','14') ORDER BY version;              -- two success rows
```
Re-run the suite a SECOND time - V14 must stay converged (revive semantics idempotent,
probe ids advance past 17, no collision with the real row).

## Report + commit

- Full report: `openspec/sdd/reports/sign-log-query-b3-report.md` (V14 file rationale,
  purge evidence, suite runs x2, self-review, concerns)
- Commit on dev, emoji-prefix style, mention sign-log-query b3. Stage ONLY: V14 file +
  AuthSignLogQueryTests.java (+ report/brief files under openspec/ if you touch them).
  The worktree carries other sessions' changes - stage file-by-file.
- Report back ≤15 lines: Status, one-line summary, report path, commit sha.

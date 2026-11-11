# Batch 3 Brief - audit-log-unify-api / query endpoint convergence (tasks 3.1–3.2)

## Task text (openspec/changes/audit-log-unify-api/tasks.md)

- 3.1 Red: unified query tests — account filter matches both families; Integer type
  filter; ordering actionTime DESC, id DESC tie-break; `session` excluded from rows;
  non-integer type → A0400; `GET /logs/sign` → 404
- 3.2 Add service overload `getPage(pageNum, pageSize, Integer type, uid, account,
  dateYm)` with select-exclusion of `session`; rewire `LogController.list` (Integer
  type + account param, MAX_PAGE_SIZE cap kept); remove `sign(...)` + the
  field-injected `ILogSignService` — green

## Read first

1. `openspec/changes/audit-log-unify-api/design.md` — D5 (session-column exclusion)
   and D8 (controller convergence) are the authoritative shape; `## Decisions`.
2. `openspec/changes/audit-log-unify-api/execution-contract.md` — Batch 3 scope +
   Test Obligations (behaviors 6, 7) + review gates (post-Batch-3 gate: endpoint
   surface /sign 404 + /logs widened).
3. `openspec/sdd/reports/audit-log-unify-api-b2-report.md` — Batch 2 landed (write
   paths retargeted; current reactor failures 3F+3E are YOUR batch's targets).
4. `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/ILogInfoService.java`
   + `impl/LogInfoServiceImpl.java` — current getPage(String type, ...) overload
   (lambdaQuery eq type/uid/dateYm + apply("deleted = 0") + orderByDesc actionTime/id).
5. `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/system/controller/LogController.java`
   — the controller you rewire (list + sign + field-injected ILogSignService; the
   field injection was itself a never-modify workaround — both leave cleanly).
6. `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/system/AuthLogQueryTests.java`
   — the current query tests (one of your RED targets; sends String type → currently
   500s on the Integer column).
7. `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/system/AuthSignLogQueryTests.java`
   — the sign-query tests (currently 500 via the dead-table XML; this batch removes
   the endpoint — these tests are Batch 4's re-target problem EXCEPT the /sign-404
   assertion which you add here; coordinate: your batch leaves the class compiling
   with the 404 test + a @Disabled or comment-out of the rest ONLY IF removal breaks
   compilation, otherwise leave them failing-red for Batch 4 — pick the option that
   keeps the reactor's failure set STRICTLY SMALLER, and say which you chose).

## Batch 3 requirements (design D5/D8, restated as build steps)

### Task 3.2 — service overload + controller rewire

- `ILogInfoService` gains an ADDITIVE overload:
  `IPage<LogInfoEntity> getPage(Integer pageNum, Integer pageSize, Integer type,
  Integer uid, String account, Integer dateYm)`.
- `LogInfoServiceImpl` implements it: same skeleton as the existing overload
  (`deleted = 0` apply, orderByDesc actionTime, orderByDesc id) PLUS:
  - `.select(LogInfoEntity.class, f -> !"session".equals(f.getColumn()))` — the
    D5 credential-exclusion (session column never selected).
  - `eq(account non-blank, LogInfoEntity::getAccount, account)` — StrUtil.isNotBlank
    (sign-page parity filter).
  - `eq(type != null, LogInfoEntity::getType, type)` — Integer now.
- The OLD `getPage(String type, ...)` overload: REMOVE it in this batch (its only
  consumer is LogController.list, rewired in the same change; the legacy
  `getLogInfoPage(LogRequest)` XML path stays for upstream parity). Removing keeps
  the perm surface honest — a String-type query against an Integer column must not
  linger. ILogInfoService is framework (copied): the ADDITIVE overload is ledger #3;
  the REMOVAL of the old overload is the same ledger item's subtraction half —
  record both in the report.
- `LogController`: `list(...)` — `type` param becomes Integer, `account` String
  param added (both @RequestParam required=false); MAX_PAGE_SIZE cap + 1 floor
  kept; javadoc updated to the audit-log story (merged endpoint, session-excluded).
  `sign(...)` + `@PreAuthorize("hasAuthority('system:signlog:list')")` + the
  field-injected `ILogSignService` REMOVED. Class-level gate stays
  `system:log:list`.

### Task 3.1 — the RED tests (write first)

In `AuthLogQueryTests` (re-target) or a new class following the b2
AuthAuditLogWriteTests style — pick ONE home and say so:

- account filter matches BOTH families (seed one op row + one sign row via the
  retargeted inserts, query by account → both return).
- Integer type filter (type=11 → only failures; type=999 → empty).
- Ordering: seed rows with distinct actionTime (+ one same-second pair) →
  actionTime DESC, id DESC tie-break.
- session excluded: any returned row map has NO `session` key/value (assert on the
  JSON payload through the endpoint layer or the entity field being null — prefer
  the endpoint/JSON layer so the exclusion is proven at the wire).
- non-integer type → A0400 (the copied exception envelope; check errors/spec.md —
  the spec delta says "rejected as a bad request parameter").
- `GET /api/v1/system/logs/sign` → 404 after removal.

Sweep discipline: hard-DELETE fixtures by the base-class prefix family; to_regclass
guards where a dropped table might be referenced.

## Reviewer-gate context you must honor

- Contract review gate AFTER this batch: endpoint surface check — /sign 404s,
  /logs widened (type Integer + account), session never in responses.
- The reactor's remaining 3F+3E should shrink to the Batch-4-owned set ONLY
  (log_sign direct queries in AuthSignLogTests ×2 + AuthUserAgentResilienceTests —
  3 failures; /sign + type-filter + sign-query failures must ALL be green after
  your batch).

## TDD discipline

- 3.1 RED first (the tests above fail against the current code: String overload
  500s, /sign 500s, no account filter, session not excluded). Record RED evidence.
- 3.2 → GREEN. Record.
- If the A0400 non-integer-type behavior requires the copied envelope's param-bind
  failure path (Spring type conversion → A0400 via the global handler), verify the
  existing errors-spec wiring before assuming — read
  openspec/specs/errors/spec.md for the A0400 contract.

## Build/test commands

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw test -q
```

- Shared test DB LIVE (.env-test.local; never print values). NO V25 changes in this
  batch (checksum stays 954461499 — do not touch migrations).

## Report + commit

- Report to `openspec/sdd/reports/audit-log-unify-api-b3-report.md` (what/TDD
  evidence per task/files/reactor failure-set delta/self-review/concerns).
- ONE commit on dev (emoji prefix style), mention audit-log-unify-api b3.
- Report back ≤15 lines: Status, one-line summary, report path, commit sha, concerns.

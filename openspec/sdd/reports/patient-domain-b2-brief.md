# patient-domain Batch 2 Brief — tasks 2.1–2.3 (entities/mappers + issuance + burned serial)

Change: `openspec/changes/patient-domain/`. Tasks source: `tasks.md` lines 11–13.
Prereq: Batch 1 landed (module + V1 ledger + test-infra trio). FIRST read `openspec/sdd/reports/patient-domain-b1-report.md`, then TRUST THE LANDED CODE (class names may differ slightly from the b1 brief — the report + code are authoritative). Tasks 3.x/4.x (archive services, API, menu seeds) belong to later batches — do NOT implement them.

- **2.1** Entities + mappers for the five tables (house Lombok combo; no physical FKs) + mapper integration tests against the test-server PG (insert/select/soft state transitions). Verify: mapper tests pass.
- **2.2** Number issuance service (TDD): rule resolution (active per rule_type), `FOR UPDATE` row lock + `serial_cur` increment, stamp-day reset branch, number formatting (prefix+stamp+serial+suffix). Verify: concurrency test (multi-thread issuance → distinct serials, unique pat_id) + day-reset test.
- **2.3** Issuance-then-failure gap: archive creation failing after issuance BURNS the number. Verify: a test asserts no reuse of a burned serial.

## Coordinator-pinned binding decisions (apply, do not re-litigate)

1. **Issuance transaction semantics (the batch's core decision):** `issue()` = `@Transactional(propagation = Propagation.REQUIRES_NEW)`. The counter increment COMMITS INDEPENDENTLY of the caller's transaction, so an archive creation that fails after issuance burns the serial — exactly what task 2.3's mandated test asserts ("no reuse of a burned serial"; spec: "numbers are never reused"). With issuance inside the caller's tx, a rollback would REVERT `serial_cur` and silently reissue the number — forbidden. The `pat_id` WRITE onto the archive row stays in the caller's transaction (design D1's atomic write intent). The coordinator amends design.md D1 wording at batch close (do NOT edit design.md yourself).
2. **Day reset:** `stamp` = Java date pattern (DATA — seed `yyMMdd`; any valid Java pattern must work with zero code); `stamp_value` = last-issued stamp token (STATE, NULL until first issuance). On issue with `is_stamp=1`: `token = DateUtil.format(new Date(), rule.getStamp())` (Hutool — the default toolkit; invalid pattern → A0400 BusinessException with detail); `token != stamp_value` → serial = 1, else `serial_cur + 1`; write both `serial_cur` and `stamp_value`. With `is_stamp=0`: `serial_cur + 1` only (null-ignoring update leaves `stamp_value` untouched).
3. **Number format:** `prefix + (is_stamp==1 ? token : "") + StrUtil.padPre(String.valueOf(serial), serialLength, '0') + suffix`.
4. **Lock:** `PatientNumberRuleMapper.selectActiveForUpdate(String ruleType)` = `@Select("SELECT * FROM patient_number_rule WHERE rule_type = #{ruleType} AND active = 1 AND deleted = 0 FOR UPDATE")` — explicit, greppable SQL. No active rule → `BusinessException(ERROR_B0001, "未配置启用的影像号规则: <ruleType>")` (system-state error, B-class — the request itself is fine; an admin fixes the config).
5. **Entity shape** (house precedent `OrgRoom`): classes WITHOUT an Entity suffix (`PatientMaster`, `PatientIdentifier`, `PatientNumberRule`, `PatientEventOutbox`, `PatientMergeLog`) in `org.birdbro.lumen.patient.entity`; `@TableName` + `@Data @Builder @AllArgsConstructor @NoArgsConstructor @EqualsAndHashCode(callSuper = true)` + `@TableId(value = "id", type = IdType.AUTO)` + `extends EntityBase`. Field types: `birthDate java.util.Date`, `gender/serialLength/serialCur/isStamp/active/mergedInto/oid Integer`, rest String. `@author birdbro` javadoc on every class.
6. **payload jsonb mapping:** String field + `@TableField(typeHandler = JacksonTypeHandler.class)` + `@TableName(autoResultMap = true)` on `PatientEventOutbox`; the mapper test round-trips a JSON payload (write → read → JSON-equal compare). If that path misbehaves on this train, plain String is the acceptable fallback — record which landed and the evidence.
7. **Additive enum constant (the batch's one sanctioned framework/common touch):** add `ERROR_A0715("A0715", "患者证件号已存在")` to the `A071x` block of `common`'s `ExceptionEnum` (family precedent A0711/A0712/A0713/A0714 — batch 3's id_no conflict uses it; A0714 stays the generic fallback). Purely additive; record it in your report; the coordination doc entry is the coordinator's.
8. **Service shape** (naming.md business-service ruling): interface `org.birdbro.lumen.patient.service.PatientNumberService` (carries the javadoc contract) + `service/impl/PatientNumberServiceImpl` (`@Service` on the impl). Method: `String issue(String ruleType)`.
9. **Fixture discipline:** tests use DEDICATED fixture rows only — NEVER touch the seeded `pat_id` rule row. Test rules use `b2_`-prefixed `rule_type` values; any `patient_master` rows use `pat_id` prefix `B2T-`. `@AfterAll` hard-DELETE sweep of every fixture family (user ruling 2026-11-07: shared-test-DB sweep is always hard DELETE). No soft-deleted fixture residue may survive.
10. **Concurrency test:** 8 threads × 5 issues against ONE `b2_` rule row; assert 40 numbers, all distinct, serials distinct. (REQUIRES_NEW makes direct `issue()` calls transactional — threads may call the service directly.)

## Test plan (RED → GREEN per test class)

- `PatientDataLayerTests` (2.1): per-entity insert→select round-trip; logic-delete transition (mapper delete → MP selects exclude the row; raw SQL sees `deleted=1`); outbox payload jsonb round-trip; `stamp_value` NULL round-trip.
- `PatientNumberIssuanceTests` (2.2 + 2.3): format without stamp; format with stamp; day-reset (past `stamp_value` → serial restarts at 1, `stamp_value` updated); same-day continuation (serial continues); active-rule resolution (two rules for the type, one active → active used); no-active-rule → B0001; concurrency (8×5 distinct); **burned-serial** (a test-owned `@Transactional` service method: `issue()` then throw → outer rollback → assert `serial_cur` advanced AND the next `issue()` returns a DIFFERENT number).
- RED evidence per class before GREEN (expected-failure reasons recorded honestly).

## References (read, do not re-derive)

1. `openspec/sdd/reports/patient-domain-b1-report.md` + the landed b1 code
2. Entity pattern: `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/manage/entity/OrgRoom.java`
3. Mapper pattern: `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/manage/mapper/OrgRoomMapper.java`
4. Service interface+impl pattern: `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/manage/service/RoleService.java` + `impl/RoleServiceImpl.java`
5. Error mechanism: `lumen-sev/common/src/main/java/org/birdbro/common/exception/Assert.java` / `BusinessException` / `enums/ExceptionEnum.java` (A071x block)
6. Data-layer test pattern: `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/manage/AuthDataLayerTests.java`

## Watch items

- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home` before every `./mvnw` call; reactor-only builds from `lumen-sev/`.
- Stage ONLY your files by explicit path — the tree carries unrelated parallel-change residue (exam-item* in lumen-sev/lumen-web + openspec/). Never `git add -A`.
- framework/common touch = ONLY the additive `ExceptionEnum` constant (decision 7). No other framework/common/lumen-system edits; no openspec edits; no tasks.md/loop-state changes (coordinator owns those).
- `lumen-sev/.env-test.local` present; missing-var failures are the designed explicit signal, never skip.

## Verify + report

- L1/L3 for this batch: `./mvnw -pl lumen-patient -am test -q` — redirect to a log file; report exit code + test counts + log path only.
- Report: `openspec/sdd/reports/patient-domain-b2-report.md` (<80 lines): implemented, RED/GREEN evidence (command + exit + counts), files changed, deviations (decision 6/7 outcomes), concerns. Then a status line (DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT + one-line summary + report path).
- Commit (stage explicit paths only): `✨ feat(sev): patient-domain b2 — 五表实体/映射 + 影像号发号服务（行锁·日重置·烧号语义）+ A0715 增量错误码 + 集成测试`

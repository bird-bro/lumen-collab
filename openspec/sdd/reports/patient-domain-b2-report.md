# patient-domain Batch 2 Report — tasks 2.1–2.3

Commit: `✨ feat(sev): patient-domain b2 — 五表实体/映射 + 影像号发号服务（行锁·日重置·烧号语义）+ A0715 增量错误码 + 集成测试` (staged by explicit path only; the report file itself stays untracked per the b1 precedent — coordinator collects the reports channel).

## What was implemented

- **2.1 Five entities** (`org.birdbro.lumen.patient.entity`, house `OrgRoom` shape: no Entity suffix, `@Data @Builder @AllArgsConstructor @NoArgsConstructor @EqualsAndHashCode(callSuper = true)`, `@TableId(value="id", type=IdType.AUTO)`, `extends EntityBase`, `@author birdbro`): `PatientMaster`, `PatientIdentifier`, `PatientNumberRule`, `PatientEventOutbox`, `PatientMergeLog`. Field types per brief decision 5 (`birthDate java.util.Date`; `gender/serialLength/serialCur/isStamp/active/mergedInto/oid` Integer; rest String).
- **2.1 Five mappers** (`org.birdbro.lumen.patient.mapper`, thin `BaseMapper` + `@Mapper`); `PatientNumberRuleMapper` carries the pinned verbatim `selectActiveForUpdate` (`SELECT * FROM patient_number_rule WHERE rule_type = #{ruleType} AND active = 1 AND deleted = 0 FOR UPDATE`, decision 4).
- **2.2 Issuance service** (decision 8 shape): interface `service/PatientNumberService` (javadoc contract) + `service/impl/PatientNumberServiceImpl` (`@Service` on impl). `issue(String ruleType)`: `@Transactional(propagation = REQUIRES_NEW)` (decision 1); active-rule resolution via the FOR UPDATE lock; no rule → `BusinessException(ERROR_B0001, "未配置启用的影像号规则: <ruleType>")`; stamp token via Hutool `DateUtil.format(new Date(), rule.getStamp())`, invalid pattern → `A0400` with detail (decision 2); `token != stamp_value → serial = 1 else serial_cur + 1`; stamp branch writes both `serial_cur` + `stamp_value`, `is_stamp=0` rides the null-ignoring update (decision 2); format = `prefix + token + StrUtil.padPre(serial, serialLength, '0') + suffix` (decision 3).
- **2.3 Burned-serial proof**: test-owned `IssuanceThenFailureHarness` (`@TestConfiguration` static-nested + `@Bean` POJO whose `@Transactional` method calls `issue()` then throws) — asserts the increment survived the outer rollback and the next number skips the burned serial.
- **Decision 7 (the one sanctioned framework/common touch)**: `ERROR_A0715("A0715", "患者证件号已存在")` added additively right after `ERROR_A0714` in `common`'s `ExceptionEnum` A071x block. Compile-verified; consumed by batch 3.

## TDD evidence

- **RED** (`export JAVA_HOME=…jdk-11… && ./mvnw -pl lumen-patient -am test -q` from `lumen-sev/`): exit 1 — compilation failure, packages `org.birdbro.lumen.patient.entity/mapper/service` 不存在 for both new test classes (expected-failure reason: classes not yet implemented). Log `lumen-sev/logs/b2-red1.log`.
- **GREEN** (`./mvnw -pl lumen-patient -am test -q -Dtest='PatientDataLayerTests,PatientNumberIssuanceTests'`): exit 0 — `PatientDataLayerTests 5/0/0`, `PatientNumberIssuanceTests 8/0/0` (surefire reports). Log `lumen-sev/logs/b2-green1.log`.
- **Batch verify (L1/L3)** (`./mvnw -pl lumen-patient -am test -q`, full patient module): exit 0 — 18 tests, 0 failures, 0 errors (5 schema-migration b1 + 5 data-layer + 8 issuance). Log `lumen-sev/logs/b2-verify.log`. No maven-collision re-run needed.
- **Residue proof** (jshell JDBC against the shared test server): all five fixture families = 0 rows; seed rule untouched (`pat_id/serial_cur=0/stamp_value=NULL/active=1`).

## Files changed

- New: `lumen-sev/lumen-patient/src/main/java/org/birdbro/lumen/patient/entity/{PatientMaster,PatientIdentifier,PatientNumberRule,PatientEventOutbox,PatientMergeLog}.java`; `…/mapper/{PatientMasterMapper,PatientIdentifierMapper,PatientNumberRuleMapper,PatientEventOutboxMapper,PatientMergeLogMapper}.java`; `…/service/PatientNumberService.java`; `…/service/impl/PatientNumberServiceImpl.java`; `lumen-sev/lumen-patient/src/test/java/org/birdbro/lumen/patient/{PatientDataLayerTests,PatientNumberIssuanceTests}.java`.
- Modified: `lumen-sev/common/src/main/java/org/birdbro/common/enums/ExceptionEnum.java` (+1 additive constant); `lumen-sev/lumen-patient/src/test/java/org/birdbro/lumen/patient/PatientIntegrationTestBase.java` (datasource URL += `stringtype=unspecified`, one line); `lumen-sev/lumen-patient/src/main/resources/application-dev.yml.example` (URL template + comment, same param).
- Test detail: fixture discipline per decision 9 — `b2_`-prefixed rule types / `B2T-` masters / `b2_` merge reasons / `b2_` event types; seeded `pat_id` row never touched; hard-DELETE sweep (`@AfterAll`) PLUS `@BeforeEach` pre-clean (a crashed prior JVM must not collide with `uk_patient_number_rule_active` or leave stale serial counters); concurrency test 8 threads × 5 issues → 40 distinct numbers, `serial_cur = 40`, continuation `B2C-0041`.

## Deviations / decision outcomes

- **Decision 6 (jsonb route): the pinned route LANDED — no fallback.** String field + `@TableField(typeHandler = JacksonTypeHandler.class)` + `@TableName(autoResultMap = true)` round-trips JSON-equal (Hutool `JSONUtil.parseObj` map-equality, not string-equality). Evidence honesty: `stringtype=unspecified` was added to the test-base URL BEFORE the first green run, based on bytecode inspection of MP 3.5.3.1 (`AbstractJsonTypeHandler.setNonNullParameter` → `toJson` → `ps.setString` — javap on the local repo jar) plus the known pgjdbc `stringtype=VARCHAR` default; the live PG type-mismatch failure was therefore NOT observed, it was pre-empted. Consequence recorded: every lumen-patient datasource URL (test base + dev template) must carry `stringtype=unspecified`; both landed. Note (no action now): because the handler JSON-quotes the String property, the stored jsonb is a scalar string rather than an object — the round trip and the consumer's String read stay faithful; server-side `payload->>` queries would need a later proposal if ever required.
- **Decision 7 outcome**: A0715 landed additively; A0714 untouched as the generic fallback; coordination-doc entry left to the coordinator.
- Design.md D1 amendment, coordination entries, tasks.md, loop-state: deliberately NOT touched (coordinator-owned).

## Self-review

- Completeness: 2.1/2.2/2.3 all done per the brief's test plan (5 data-layer cases incl. logic-delete transition + `stamp_value` NULL round trip; 8 issuance cases incl. day reset, same-day continuation, active-rule resolution, B0001, concurrency, burn); nothing from 3.x/4.x built.
- Quality: house patterns followed (OrgRoom entity shape, OrgRoomMapper @Select style, RoleService interface+impl split, AuthDataLayerTests fixture discipline); javadoc carries the contract on interfaces/entities.
- Discipline: framework/common touch = the one additive constant only; stage-by-explicit-path; no full-reactor run; mvn runs kept short (single-class `-Dtest` runs during iteration per the coordinator's concurrency note).
- Testing: RED honest (compile-stage failure of both classes recorded); all 13 new tests assert real behavior against the shared server; residue sweep verified end-to-end.

## Issues or concerns

- **Deployment note (needs coordinator visibility)**: `stringtype=unspecified` is now a lumen-patient URL requirement wherever the outbox writes (test infra + `application-dev.yml.example` updated); any future deployment doc must carry it.
- `@Configuration`-enhanced `@TestConfiguration` harness bean got CGLIB-proxied for `@Transactional` — works on this train (proven by the green burn test); worth remembering if batch 3 reuses the pattern.
- jshell smoke runs left temporary script dirs under `$TMPDIR` only (auto-cleaned by the OS); nothing written into the repo.

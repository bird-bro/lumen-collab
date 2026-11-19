# Batch 1 Review: rep-template-structured-content (tasks 1.1–1.4)

Commit `a62da9a` · 2026-11-18 · Verdict: **APPROVED** · Findings: 0 Critical / 0 Important / 3 Minor

## Findings

### Critical
(none)

### Important
(none)

### Minor
1. **[M1] Untested novel write path: update-with-content.** Test 1 covers create (entity insert) and test 3 covers update with content=NULL, but the wrapper placeholder-mapping route `.set(RepTemp::getContent, content, CONTENT_HANDLER_MAPPING)` (RepTempServiceImpl.java:137) has zero coverage. If the mapping string ever fails to resolve, a structured update would silently double-encode (jsonb_typeof='string'). Task 2.1 already schedules "create/update/page 往返 content" — batch 2 MUST include a structured update→page round-trip re-asserting jsonb_typeof='object'; do not let it drop.
2. **[M2] SettingSchemaSeedTests.TEMP_COLUMNS lacks "content" at HEAD** (SettingSchemaSeedTests.java:52-54). assertTableColumns is existence-only so nothing fails today, and the file sits in the parallel session's dirty set (V38 work) — editing now would collide. ACCEPT as batch-4/verify coordination item (implementer's proposal is right); record it in openspec/sdd/progress.md so verify picks it up.
3. **[M3] Delta "Invalid content rejected" says "create/update body" — all six rejection cases run via POST only.** Update-path rejections (same DTO @Valid + same guardContent call in update) are untested; content.impression=null round-trip is also implicit only (test 3 creates with impression absent, no explicit assertion). Fold both into batch 2's api-layer tests.

## Special gates (8/8 PASS)

1. **提交卫生: PASS.** `git show --stat a62da9a` = exactly 11 files, all under lumen-system setting (V39 + RepTemp + content model ×3 + handler + 2 Temp DTOs + service javadoc + impl + test). Post-commit `git status` intact: patient rename still RM-staged, exam-method series + lumen-sev/pom.xml still modified-unstaged, V38__exam_method_remark.sql + lumen-core/ still untracked. Nothing swallowed. (The extra files in the 389d481..a62da9a range are the prior archive commits 16ea882/b489637, not this commit.)
2. **D1 互斥: PASS.** create nulls finding/impression after copyProperties (RepTempServiceImpl.java:116-119); update content branch wrapper-sets both NULL (:139-141); null-content branch sets content NULL (:144-148). GREEN log shows `UPDATE ... content=?` bound with null; tests assert DB finding/impression NULL (:65-66) and content NULL post-conversion (:214).
3. **D8: PASS.** guardContent(flag=0) → A0400 "目录节点不允许携带 content" (:173-176), msg names content; tested (:171-179). flag=null branch is dead defense — flag is @NotNull on both DTOs.
4. **校验覆盖: PASS.** version=1 service assert (:177-181); @NotNull version / @NotEmpty sections / @NotBlank name+code+label with @Valid cascade on DTO fields and nested lists; all six delta rejection cases asserted A0400 + field-naming msg (RepTempContentApiTests.java:113-182). Gap noted in M3.
5. **handler 保真: PASS.** RepTempContentJsonbTypeHandler shape-identical to PatientJsonbTypeHandler (extends JacksonTypeHandler, Class<?> ctor, setNonNullParameter → setObject(i, toJson, Types.OTHER)); RepTemp @TableName(value=..., autoResultMap=true) + @TableField(typeHandler=...). DB proof: jsonb_typeof(content)='object' (test :67-68).
6. **迁移: PASS.** V39__rep_temp_content.sql matches brief SQL verbatim + IF NOT EXISTS idempotency; V38 untouched (still untracked, other session's).
7. **TDD 证据: PASS.** RED real (3 run: 1 failure "code":"200" on blank name + 2 errors column "content" does not exist — failure causes are genuine missing-feature, /tmp/batch1-red.log:117-148). GREEN surefire XML tests=3 errors=0 failures=0; regression AuthRepTempApiTests + AuthRepTempPermPinTests XMLs 3/3 + 3/3 clean. Tests hit real HTTP + real PG on the shared test server (TestRestTemplate + JdbcTemplate jsonb assertions); sweep hook ran, rep_temp=0.
8. **Concerns 处置: PASS.** a) TEMP_COLUMNS → accept as coordination item (M2). b) HTTP 200 + envelope A0400 is the framework's actual unified form: GlobalExceptionHandler.java:46-52 (BusinessException) and :60-73 (MethodArgumentNotValidException → R.ofFail("A0400", ...)) both return envelopes with no @ResponseStatus — the "// 返回 400" section comment is stale. Tests assert envelope code + msg per the existing AuthRepTempApiTests idiom; the delta's "validation error naming the field" does not pin HTTP status. Brief's "4xx" was imprecise; implementer's call is correct. Stale framework comment cleanup can ride a later framework touch, not this batch.

## Spec compliance

- Missing: none within batch scope. Update-with-content round-trip is task 2.1 (M1/M3 carry-forward); "Template payloads carry the content object" is half-covered (create half here, update half due in 2.1).
- Extra: none. CONTENT_HANDLER_MAPPING is required for wrapper jsonb writes; keeping update(null, wrapper) (vs entity-patch) avoids an out-of-scope MetaObjectConfig update_by behavior change — correctly reasoned in the report.
- Misunderstood: none. Entity-package placement of the content model follows the brief's contract-face ruling; service interface change is javadoc-only (never-modify respected; entity change is additive field + annotation).

## Code quality / Tests

Layering clean (model in entity pkg per brief, handler in mapper pkg mirroring patient precedent); guardContent DRY-shared by create/update; edge cases handled (measures [] legal, bare section null finding/measures verbatim, unit/normal null round-trip asserted, prose row page payload content:null asserted). Real-behavior tests on the shared test server, no mocks; fixtures ride the registered "ut模板" sweep family (SettingIntegrationTestBase.java:105).

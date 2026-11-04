# Review: dict-city-reference Batch 1 (all tasks)

**Verdict: Approved** — Critical 0 / Important 0 / Minor 3
(Reviewer could not write files per its operating rules; coordinator persisted this copy.)

**Seed integrity:** V4's 3751 tuples are byte-identical to data/rows.sql after the
documented terminator normalization (12x300 + 1x151 batches, 1 unique INSERT header,
interior ; boundaries verbatim, only the final trailing-comma + standalone-; fixed) -
independently diffed, SEED_IDENTICAL.

**Critical/Important:** none.

**Minor findings:**
1. Report test math phrasing - "baseline 53 + 12 = 65" folds 3 untracked in-flight tests
   (AuthUserAgentResilienceTests, another workstream) into the baseline; committed
   baseline at 847790c is 50, committed head total is 62, 65 only with the untracked file
   present (report discloses the unstaged file elsewhere, so informational only).
2. Lineage-data oddities carried verbatim by design D1 (not implementer errors, V5+
   revision candidates): pinyin mojibake at id 820103; zip/city_code apparently swapped
   at id 712925 ("05","651").
3. DictCityMapper javadoc says "no insert/update/delete methods" - true for added
   methods, but BaseMapper still inherits them; harmless since no HTTP write path exists
   (spec requires no mutation endpoints, which holds).

**Verification evidence (all pass):**
- V4__dict_city.sql: 13-column DDL matches brief lineage (id bigint PK, up_id integer,
  level smallint, 10 varchar, no audit block), PK + idx(up_id) + idx(code), CREATE
  SEQUENCE + OWNED BY + nextval default + setval(pg_get_serial_sequence(...),
  COALESCE(MAX(id),1)) - OWNED BY makes pg_get_serial_sequence resolve; migration test
  asserts last_value >= MAX(id)=900000 and passed in my run.
- Read-only: CityController has exactly 2 GETs; diff grep for
  PostMapping/PutMapping/DeleteMapping/insert/update/delete hits only test code and
  javadoc; no @Log; 405 envelope + frozen-checksum + swagger-OAS tests prove no write path.
- Entity deviations verified sound: Long id/upId required by generic TreeUtils<T,K>
  same-key-type matching (org.birdbro.common/utils/TreeUtils.java:45); DDL stays
  up_id integer (JDBC widening; round-trip tests green); does not extend EntityBase per
  design D2.
- Independent re-run: MVN_EXIT=0, surefire aggregate 65 tests / 0 failures / 0 errors
  (AuthDictCityMigrationTests 6, AuthDictCityTests 6, all green on the shared test DB).
- Scope: commit 032221c contains exactly 17 dict-city files; V1-V3 untouched (only V4
  added), framework/common untouched, zero dict_catal/dict_detail refs; rows.sql is
  git-tracked via the directory .gitignore negation; all spec WHEN/THEN scenarios
  (checksum, tree nesting, district list, no-write) are covered by passing tests.

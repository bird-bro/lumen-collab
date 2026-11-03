# Batch 1 Review — system-data (tasks 1.1, 1.2, 2.1, 2.2)

Reviewer: code-reviewer agent · 2026-11-03
Commit reviewed: `88806a7` (base `e00d447`; range also contains `8393191` spec-hygiene docs — no code overlap, not reviewed here)
Independent suite re-run: **MVN_EXIT=0, 41 tests / 0 failures / 0 errors / 0 skipped**
(`AuthSystemDataMigrationTests` 7/7; existing 34 all green — matches the report's claim exactly).

## Verdict: **Approved**

All four claimed tasks are implemented to spec; the deviations are recorded, sanctioned, and sound; RED evidence is internally consistent (5 failures + 2 errors is exactly the set the 7 new tests produce against a DB without V3: 5 assertion failures + 2 `relation does not exist` errors from the two seed `queryForMap` calls). One Important item must be settled in Batch 2 before endpoints consume the copied enable-filtering methods (F below).

---

## Part 1 — Spec compliance

### Task 1.1 `V3__system_data.sql` — PASS
- Four tables explicitly `CREATE TABLE public.<name>`; Flyway history stays in auth (test base pins `spring.flyway.schemas=auth`, `AuthIntegrationTestBase.java:79`; main `application.yml` carries no datasource/Flyway — the gitignored dev file does, `.example` template updated).
- dict_catal/dict_detail end with the exact V1 five-column audit tail (order verified against `common/.../EntityBase.java`: deleted/create_by/create_time/update_by/update_time; `V1__auth_schema.sql:27` style match).
- Seed: `common_status` id=1 + 启用/停用 details id 1/2 with double anchor (ca_id=1, ca_code='common_status') + setval tail for both dict tables — V2 discipline (`V2__seed.sql:55-61` precedent).
- Deviation D (no setval for empty log tables) is correct: `setval(seq, COALESCE(MAX(id),1))` over an empty table sets is_called=true → first append would get id=2, skipping id 1. Sound.

### Task 1.2 `pg-init.sql` section six — PASS
Pure append (17 lines, all comment-state matching the role-block style), six roles named, GRANT USAGE hint (deviation H) is comment-only and technically necessary on PG15+.

### Task 2.1 de-`ice.` + connection strings — PASS
- Both `@TableName` edits + **both mapper XMLs** (deviation A, coordinator-sanctioned): `insertInfo`/`insertSign` and both page SELECTs were the real read/write path; without the XML edits every `@Log` write would still hit `relation does not exist`. Necessary, in-scope, recorded.
- Strict sweep `[^a-zA-Z0-9]ice\.` over all of lumen-sev (excl. target): **zero hits** — concern 3 verified clean.
- `currentSchema=auth,public` in `application-dev.yml.example`, `AuthIntegrationTestBase.java:63`, and the local gitignored `application-dev.yml` (verified on disk); `application.yml` correctly untouched (no connection string there).

### Task 2.2 `framework/dict/` copy — PASS
16 new files exactly per brief: entity 2, mapper 2, service 2, impl 2, pojo 6 (Catal/Detail × Create/Modify/Query), helpers 2. No DictMenu/DictCity. No controllers/endpoints (Batch 2 scope respected). Registration is proven: `@MapperScan("org.birdbro.**.mapper")` + `scanBasePackages="org.birdbro"` cover the new mappers/services (context loads in the suite — the `@Service` impls require the mapper beans), `mapper-locations: classpath*:mapper/**/*.xml` is unaffected (no dict XML). Hutool (deviation E) resolves transitively via `common` (declared in `common/pom.xml:88`, already used by 3 pre-existing framework files) — no new pom dependency.

### Missing: none. Extra: only the two recorded deviations (A, H), both sanctioned/justified.
### Cross-file sanity: framework existing files touched = exactly the 2 entities + 2 XMLs (adaptation (7) scope). Working-tree post-commit edits (lumen-auth entities, CLAUDE.md) are outside this commit and touch no framework file.

## Part 2 — Code quality

### DDL ↔ entity correspondence — verified 1:1
- **log_info**: 26 entity fields ↔ 26 columns (`date_ym`, `action_time` named per brief; `mobile boolean` ↔ Boolean; `param/result/exception text` ↔ String; `deleted integer NOT NULL DEFAULT 0` ↔ Boolean deleted field — required because the copied XML filters raw `WHERE deleted = 0` and MP logic-delete embeds 0/1; boolean column would break both — deviation C is correct).
- **log_sign**: 18 ↔ 18 (all fields incl. `token`, `app`).
- Write path integrity: XML `insertInfo`/`insertSign` omit id/deleted/create_time → DDL defaults supply them; the Boolean `deleted` field never touches the insert. Read path: PG JDBC getBoolean over integer 0/1 is fine.
- **dict_catal**: 8 entity fields + 5-field audit tail ↔ DDL exactly. **dict_detail**: 14 entity fields (incl. `back_1`/`back_2` per explicit `@TableField`, plus `remark`/`initial` — deviation B, brief-authorized "entity wins") + audit tail ↔ DDL exactly.

### Seed discipline — PASS (explicit ids + setval on every seeded table)
### Index sanity — PASS
Partial unique `WHERE deleted = 0` matches the `@TableLogic` 1/0 config (`application.yml:30-32`) and V1 precedent; `(ca_code, value)` with nullable `value` degrades gracefully (NULLs distinct → label-only details don't collide).

## Part 3 — Tests
- RED plausibility: 41 = 34 + 7; the 7 failures decompose exactly as reported (see header). Plausible and consistent.
- New tests do assert public placement via `information_schema.tables WHERE table_schema='public'` and seed visibility by keyed lookups (never row counts — correct for the shared long-lived DB).
- Independent re-run: **MVN_EXIT=0, 41/0/0** (team test server, fresh V3-applied DB).

## Known concerns — verdicts
1. **enable 11 vs 1/0 (report F) — CONFIRMED, Important.** `EnableConstants.TRUE=11/FALSE=10` (`common/.../EnableConstants.java:10,12`) is used in `DictDetailServiceImpl.mapByCatalIds`/`valueMapByCatalId` (`:62`, `:81`); seeds/DDL use 1/0; `EnableConstants` is referenced **nowhere else** in lumen-sev; the copied entities' own javadoc says "启用 1(def) 禁用 0"; V2 seed uses enable=1. **Recommendation: adapt the copied impls to lumen's 1/0 in Batch 2** (replace the two `EnableConstants.TRUE` filters with 1/0 — literal or a small dict-local constant — recorded as a copy adaptation in the coordination doc). Aligning seeds to 11 is not viable: V3 is checksum-frozen on the shared test DB, the DDL default is 1, and spec scenario "Fresh database carries the starter dictionary" would become unsatisfiable. Severity today: latent (no caller yet) — blocker only if Batch 2 wires these methods unchanged.
2. **DetailQuery value vs label (report G) — no spec impact.** The spec scenario pins only "live details of that catalog, ordered by sort" — no search filter. Batch 2's list-by-caCode is a new method anyway (`getPage` filters by `caId`, not `caCode`). Keep OPS lineage; add label search additively only if a UI needs it.
3. **`ice.` sweep — clean** (zero hits, strict pattern).
4. **V3 completeness — good enough to freeze.** All copied-entity fields have columns; audit tail exact on dict tables; nothing in known Batch 2 work (endpoints/@Log/login events) requires editing V3. dict_city remains a future V4.

## Additional findings (reviewer)
- **Minor** `framework/src/main/resources/mapper/LogSignMapper.xml:40`: `AND account = #{req}` binds the whole `LogRequest` instead of `#{req.account}` — pre-existing copied defect (untouched line in this commit; LogInfoMapper has the correct `#{req.account}`). Only fires if an account filter is ever passed to `getLogSignPage`. Fixing it means editing a copied file → needs a recorded adaptation; flag to Batch 2.
- **Minor** `framework/dict/pojo/request/CatalCreate.java` (`enable` typed `Byte` vs entity `Integer`): OPS lineage quirk; a mapping footgun for Batch 2 controller wiring — convert explicitly.
- **Minor** `getLogInfoPage`/`getLogSignPage` have no `type`/`uid`/`dateYm` filters (spec scenario 7 needs them) — expected; Batch 2 task 3.2 adds them; noting so the copied page query isn't mistaken for spec-satisfying.
- **Minor** no index on `dict_detail.ca_code` while `DictLabelHelper`/`DictReverseHelper`/Batch-2 list-by-caCode filter on it (only `ca_id` indexed). Dictionary tables are tiny; if ever needed, add via V4 — do NOT touch frozen V3.
- **Housekeeping** `openspec/changes/system-data/tasks.md` checkboxes 1.1/1.2/2.1/2.2 still unticked despite being done; `openspec/sdd/reports/system-data-b1-report.md` is untracked.

## Findings summary
Critical: 0 · Important: 1 (concern 1, Batch 2 action) · Minor: 4 + 2 housekeeping

**Verdict: Approved**

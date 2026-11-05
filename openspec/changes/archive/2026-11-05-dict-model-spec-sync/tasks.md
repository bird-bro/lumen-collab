# Tasks

## 1. Delta review

- [x] 1.1 Re-read `openspec/specs/system-data/spec.md` and the delta; confirm no drift
      since drafting (field lists, seed counts, error-code wording) and that no later
      migration (V11+, parallel work) touched the dict tables — if it did, re-scope the
      delta before anything else.
      PASS 2026-11-05: V11 is menu-icons-only (zero dict mentions); main spec content
      matches the drafting baseline.
- [x] 1.2 Confirm the proposal's scope boundary still holds: no other spec enumerates
      the dict detail payload or the menu seed (grep `caCode|label|back1|seed` across
      `openspec/specs/`).
      PASS: old fields only in system-data/spec.md (the rewrite target); menu_info only
      in auth-rbac's model-level requirement (still accurate).

## 2. Verification (L2 evidence — no new tests needed)

- [x] 2.1 Map each delta scenario to its existing coverage and run the lumen-system
      suite green: `AuthSystemDataMigrationTests` (schema columns tag/flag, unique
      `uk_dict_detail_ca_id_value`, seed join-by-code), `AuthDictCrudTests` (create
      anchored by caId, duplicate-value `A0714`, by-code list), `AuthDataLayerTests`
      (seeded 启用/停用 via `value`, catalog 1000).
      PASS 2026-11-05: full suite 65/65, BUILD SUCCESS.
- [x] 2.2 Verify the fresh-database seed counts claimed by the delta against the real
      migrations (7 live catalogs / 30 live details; `common_status` id 1000; detail
      ids offset 100000) — one guarded psql read against the shared test database plus
      the migration files as source of truth.
      PASS: 7 catalogs / 30 details / common_status id 1000 / V6 detail ids 100001-100350.
      OBSERVATION (benign, out of scope): the shared DB's common_status detail rows sit
      at ids 10001/10002 (user's manual re-base alongside the V7-equivalent catalog
      move); fresh DBs produce 1/2. Internal surrogate ids only, zero behavioral
      difference — flagged for an optional future alignment migration.

## 3. Documentation and closure

- [x] 3.1 Write the Chinese proposal intro `docs/proposals/dict-model-spec-sync.zh.md`
      (non-authoritative disclaimer header; 提案解释、文件路径+角色、下一步动作建议、流程规划).
      Already done at propose time per the project's zh-intro convention.
- [x] 3.2 Run `/opsx:verify` — L1 build n/a (doc-only), L2 = the mapped WHEN/THEN
      above, L3 = the green suite from 2.1; write verify.md.
      PASS — verify.md written (overall PASS; backend L1/L2/L3 = PASS/6-6/PASS, lumen-web SKIP).
- [x] 3.3 `/opsx:archive` — syncs the delta into `openspec/specs/system-data/spec.md`
      (Purpose + three requirements).

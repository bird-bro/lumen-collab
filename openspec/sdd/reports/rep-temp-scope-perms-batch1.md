# Batch 1 Report: rep-temp-scope-perms (tasks 1.1-1.4, lumen-sev)

## Status: DONE_WITH_CONCERNS (concern = V41->V42 renumber, see Issues)

## Implemented

- **1.1 Migration** `lumen-sev/lumen-system/src/main/resources/db/migration/V42__rep_temp_public_perms.sql`
  (NOT V41 - collision, see Issues): 4 F nodes under the 报告模板 C page (resolved by
  perms, no fixed ids), sorts 4-7, titles 公共模板可见/新建公共模板/编辑公共模板/删除公共模板,
  perms `setting:temp:public:{list,create,update,delete}`; role 1 guarded grant (V28
  role_menu NOT EXISTS shape, perms IN); setval tail (V14). All inserts NOT EXISTS among
  live rows; comment lines carry no semicolons (V28 comment discipline).
- **1.2 Service guard** `RepTempServiceImpl.requireScopeFamily(Integer type, String op)`:
  reads `SecurityContextHolder...getAuthentication().getAuthorities()` directly (no new
  framework surface); type=1 -> `setting:temp:public:<op>`, else `setting:temp:<op>`;
  refusal throws `AccessDeniedException("缺少公共模板权限: ...")` -> AuthDenyAdvice A0301
  content-free. Wiring per D2: create asserts BEFORE validation on request.type;
  update/delete assert AFTER requireOwnedRow on the row's stored type (non-owner private
  stays A0400, order proven by test); getTree/getPage assert public:list only when
  type=1.
- **1.3 Controller pins** `RepTempController`: create/update/delete pins ->
  `hasAnyAuthority('setting:temp:<op>', 'setting:temp:public:<op>')`; class gate
  `setting:temp:list` untouched.
- **1.4 Seed assertions**: SettingSchemaSeedTests left UNTOUCHED (it is in the parallel
  session's dirty set AND its V29-table shape does not cover menu F nodes - brief's
  fallback applies); assertions live in the new V42-related test class instead.

## TDD evidence

- RED: `./mvnw -pl lumen-system -am test -q -Dtest='AuthRepTempScopePermTests,
  AuthRepTempPermPinTests,AuthRepTempApiTests,AuthRepTempContentApiTests'
  -DfailIfNoTests=false` (note: content class has no Auth prefix, so only 13 ran) ->
  exit 1, **13 run, 6 failures + 1 error** - exactly the 7 new/updated expectations
  (pin strings, matrix 4 quadrants, 2 seed assertions); regression suites green in same
  run. Log /tmp/rep-temp-red.log.
- GREEN: same filter + `RepTempContentApiTests` -> exit 0; surefire: ScopePerm 7/7,
  PermPin 3/3, Api 3/3, Content 7/7 = **20/20** (13 regression + 7 new).
  Log /tmp/rep-temp-green3.log.
- Full regression `./mvnw -pl lumen-system -am test -q` -> framework 4/0, common 4/0,
  lumen-core 26/0, lumen-system 274 run with 15 failures ALL in 8 manage-package
  classes (203 session-loss / "org id 4242 does not exist" / A0301-on-login-derived
  authorities = shared-server parallel-suite interference; rep-temp classes 20/20
  green in that same run). Isolated `-am` rerun of those 8 classes -> exit 0,
  **68/68** (AuthLoginTests 14, AuthDictItemsLookup 4, AuthDeviceApi 10,
  AuthPermEnforcement 9, AuthRoomApi 7, AuthOrgDep 8, AuthRoleEnableToggle 3,
  AuthUserProfileApi 13). Logs /tmp/rep-temp-full-regression.log,
  /tmp/rep-temp-manage-rerun2.log.

## Files changed (commit 9e18f6c, pathspec-limited, 6 files +556/-13)

- `lumen-sev/lumen-system/src/main/resources/db/migration/V42__rep_temp_public_perms.sql` (new)
- `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/setting/service/impl/RepTempServiceImpl.java`
  (guard + 6 wire-in points + imports)
- `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/setting/controller/RepTempController.java`
  (3 pins + javadoc)
- `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/setting/AuthRepTempScopePermTests.java`
  (new: D5 matrix 5 + seed 2)
- `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/setting/AuthRepTempPermPinTests.java`
  (expected pin strings -> hasAnyAuthority)
- `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/setting/SettingIntegrationTestBase.java`
  (fullPermToken + 4 public perms - D5 fixture ruling, keeps the 13 legacy cases green)

## Self-review

- Delta coverage 1:1: exam-setting 4 new scenarios (pins admit either family =
  public-only tokens passing pins + service; public write w/o family A0301
  content-free incl. refused-delete leaves row live; public write w/ family succeeds
  owner_uid NULL; public read w/o public:list A0301 tree+page with private-tree
  contrast) + existing scenarios still green; system-data scenario (role 1 gains all
  four perms, idempotent re-run) = 2 seed tests.
- D2 order honored (create family-before-validation; update/delete ownership-before-
  family; A0400 preserved - explicit test where a public:update-only stranger gets
  A0400, not A0301).
- No new error codes; A0301 content-free (envelope echoes only the missing perm
  string); ownership semantics untouched (requireOwnedRow unchanged).
- Migration idempotent: NOT EXISTS + guarded grants + setval; re-execution proven by
  test (live-row no-op).
- exam-method files / V38 / SettingSchemaSeedTests untouched (git status checked; only
  the 6 pathspec files in the commit).

## Issues / concerns

1. **V41 collision (cause of the renumber)**: the parallel number-rule session created
   `V41__number_rule_menu_component.sql` mid-task (10:32, while my GREEN run was
   failing on the duplicate-version Flyway boot). Renumbered my migration to V42
   (file + test reference); their file untouched. Design.md D3 says "V41" - parent may
   want to amend the design wording; the spec delta pins no version number.
2. **Checksum episode**: my V42 was applied to the shared test DB (green2 run) before I
   removed semicolons from its comment header; per the Flyway ruling (never rewrite an
   applied migration) I deleted ONLY the version-42 history row
   (system.flyway_schema_history) and let the next boot re-apply the clean file -
   verified applied + green in green3. No repair-and-diverge.
3. **Shared-server interference**: 15 manage-suite failures during the full regression
   are fixture/session races with the concurrently-running parallel session (documented
   hazard); all 8 classes pass 68/68 on the isolated rerun. Batch verify should use an
   exclusive window per the standing ruling.
4. Brief's example message text "缺少公共模板权限: ..." kept verbatim for the public
   family; the private-family refusal uses the symmetric "缺少个人模板权限: ..." (brief:
   或对应消息).

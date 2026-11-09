---
verify:
  change: role-type-dict-api
  date: 2026-11-09
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "10/10"
      L3: PASS
---

# verify.md — role-type-dict-api

> Three-layer verification (2026-11-09). Overall **PASS**: L1 build green, L3
> full reactor 145/145, all 10 delta scenarios IMPLEMENTED with code+test evidence.

## Stacks

| Stack | L1 build | L2 spec alignment | L3 tests |
|:--|:--|:--|:--|
| backend (lumen-sev) | ✓ PASS (`./mvnw compile -q` exit 0) | 10/10 IMPLEMENTED | ✓ PASS (`./mvnw test -q` exit 0, 145/145, 0 failures) |

## Scenario alignment detail

### api delta — Dict Items Lookup By Catalog Name

| Scenario | Status | Evidence |
|:--|:--|:--|
| Items returned for a known catalog | ✓ IMPLEMENTED | `DictDetailController.itemsByName` (DictDetailController.java:101-103) + `DictDetailManageServiceImpl.listEnabledItemsByName` sort-asc projection (:99-122); test `seededRoleTypeCatalogResolvesSlimAndSorted` (AuthDictItemsLookupTests.java:66) asserts the slim `{code,value}` pair, seed order 10 before 11 |
| Unknown catalog resolves to an empty list | ✓ IMPLEMENTED | null-catal branch → empty list (DictDetailManageServiceImpl.java:107-109); test `unknownCatalogResolvesToEmptyArray` (:110) asserts `code: "200"` + empty array |
| Disabled details are excluded | ✓ IMPLEMENTED | enable filter (DictDetailManageServiceImpl.java:114); test `disabledDetailsAreExcluded` (:122) - disabled row absent, null-code row pinned (JSON null projection) |
| Lookup requires authentication but no permission string | ✓ IMPLEMENTED | method pin `@PreAuthorize("isAuthenticated()")` overriding the class gate (DictDetailController.java:101); test `lookupNeedsNoPermissionStringButRejectsAnonymous` (:160) - perm-less principal 200, dict family A0301, anonymous envelope 203 |

### system-data delta — Role Type Dict Catalog

| Scenario | Status | Evidence |
|:--|:--|:--|
| Seed provisions the role type catalog | ✓ IMPLEMENTED | `V23__role_type_dict_seed.sql` (catalog id 1020 line 19, details 1020001/1020002 lines 23-24 mirroring `RoleConstants.TYPE_GLOBAL=10/TYPE_RCP=11` RoleConstants.java:12,16); test asserts both seeds resolve in sort order (AuthDictItemsLookupTests.java:66-105) |
| Seed re-run is a no-op | ✓ IMPLEMENTED | ON CONFLICT (id) DO NOTHING guards (V23:20,25); V23 applied once on the shared test DB with no duplicate rows (Flyway success=true; suite re-boots clean) |
| Operator extends the vocabulary | ✓ IMPLEMENTED | the lookup reads LIVE rows at call time (listByCaId per request, DictDetailManageServiceImpl.java:111-121) - dict CRUD endpoints untouched, so operator-added items appear with zero code change; the throwing-away-catalog test exercises the same live-read path |

### data delta — Role Enable Column

| Scenario | Status | Evidence |
|:--|:--|:--|
| Migration adds the column with a safe default | ✓ IMPLEMENTED | `V24__role_info_enable.sql` - rebuild in the V17 pattern (enable positioned before the audit block, line 20; audit tail restored, test `everyTableCarriesIdBusinessColumnsAndAuditBlock` green); fresh roles read 1 (AuthRoleEnableToggleTests.java:57-59) |
| Toggle persists through the entity | ✓ IMPLEMENTED | `RoleServiceImpl.setEnable` focused patch (RoleServiceImpl.java:114-125); test asserts 0 and 1 persist with `data: true` and profile fields untouched (AuthRoleEnableToggleTests.java:52-85) |

### auth-rbac delta — Roles and Menu-Button Permissions

| Scenario | Status | Evidence |
|:--|:--|:--|
| Dict items lookup stays authenticated-only | ✓ IMPLEMENTED | same evidence as api scenario 4 (DictDetailController.java:101 + AuthDictItemsLookupTests.java:160-205: 200 / A0301 contrast / anonymous 203) |

### api delta — Role Management Endpoints (enable additions)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Enable toggle persists 0 or 1 | ✓ IMPLEMENTED | `RoleController.setEnable` (RoleController.java:113-121, `system:role:update` pin) + `SetRoleEnableRequest` strict 0/1 validation (SetRoleEnableRequest.java:20-24); test togglePersistsAndEchoesOnList |
| Enable toggle rejects other values | ✓ IMPLEMENTED | bean-validation rejection + no-mutation assert (AuthRoleEnableToggleTests.java:88-105) |
| Role list returns paged rows with grant echo (+enable) | ✓ IMPLEMENTED | `RoleItemDto.enable` (RoleItemDto.java:36) rides the BeanUtil copy (RoleServiceImpl toDto); test asserts fresh=1 → toggle 0/1 echo |

## Issues

(none — no PARTIAL, no NOT_IMPLEMENTED)

## Notes (process record)

- URL contract discovered during apply and pinned in the test javadoc
  (AuthDictItemsLookupTests header): catalog-name path segments must reach the
  client RAW (UTF-8 verbatim); pre-encoded `%XX` segments land on the security
  entry point (envelope 203). Browser/axios consumers encode automatically.
- Flyway history repair on the shared test DB (memory rule: delete history row +
  re-run): the interim append-shape V24 was applied once during apply TDD; the
  rewritten rebuild-shape V24 re-applied cleanly after the history-row delete,
  and `AuthSchemaMigrationTests` pins the audit-tail convention.
- One full-suite flake (AuthWorkstationSeedTests, grants 2→3) reproduced only
  while a PARALLEL session (login-lockout) was testing against the same shared
  DB; standalone re-run green — recorded as shared-DB concurrency, not a
  regression of this change.
- Smoke (browser UI) remains a user-side step per the house pattern; the API
  contract is fully covered by the integration tests above.

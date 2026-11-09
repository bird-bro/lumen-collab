# Tasks: role-type-dict-api

> TDD house rule: each code task lands its failing test first (red → green).
> Zero edits to `common`/`framework` (blocking red line).

## 1. Seed migration

- [x] 1.1 Write `V23__role_type_dict_seed.sql` — catalog 「角色类型」 (id 1020,
      enable 1, sort 99) + details 1020001 (code 10, 系统角色) / 1020002 (code 11,
      RCP角色), ON CONFLICT guards, header comment recording the RoleConstants
      lineage and the id-band provenance (coordination D6).

## 2. Endpoint (TDD)

- [x] 2.1 RED: integration tests in `AuthIntegrationTestBase` lineage — known
      catalog returns slim `{code, value}[]` sort-ordered with disabled rows
      excluded; unknown catalog returns `code: "200"` + empty `data`; a
      `system:role:list`-only principal gets 200 while unauthenticated is
      rejected (4 scenarios of the api delta).
- [x] 2.2 GREEN: `DictItemDto {code, value}` + `DictDetailManageService
      .listEnabledItemsByName(name)` (catalog by unique name+enable → live
      enabled details by `caId`, sort asc; unknown → empty list).
- [x] 2.3 Wire `DictDetailController.itemsByName(@PathVariable String name)` —
      `@GetMapping("/items/{catalogName}")` with method-level
      `@PreAuthorize("isAuthenticated()")` overriding the class page gate
      (explicit authenticated-only, D2); javadoc records the city-dict precedent
      and the coordination pointer.
- [x] 2.4 Seed verification: assert the V23 rows resolve through the new
      endpoint (integration test reading `items/角色类型` → exactly the two
      seeded items, order 10 then 11).

## 3. Role enable field (TDD, D7)

- [x] 3.1 RED: integration tests — toggle `PUT /roles/{id}/enable` persists 0/1
      and returns `data: true`; non-0/1 body rejected without mutation;
      `system:role:update` required (A0301 otherwise); `roles/list` rows carry
      `enable` (existing rows read 1 after V24).
- [x] 3.2 GREEN: `V24__role_info_enable.sql` (`ADD COLUMN enable integer NOT NULL
      DEFAULT 1`); `RoleInfo` + `RoleItemDto` gain `enable`; `RoleController
      .enableToggle` mirrors the orgs status toggle (strict 0/1 body check,
      `system:role:update` method pin, `data: true`).
- [x] 3.3 `RoleServiceImpl` list payload maps the new column; create path
      untouched (column default 1 = born enabled, no drawer field).

## 4. Regression + verify

- [x] 4.1 Full `lumen-system` test suite green (`./mvnw test`, JDK 11); no
      changes to `common`/`framework`; `roles/*` DTO gains only `enable` (D3 —
      `type` stays a raw int).
- [x] 4.2 `/opsx:verify` three layers; record evidence in verify.md.

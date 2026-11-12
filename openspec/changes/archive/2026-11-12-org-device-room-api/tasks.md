# Tasks

Backend first (the frontend builds against the real API - no mock layer, user ruling
2026-11-05). This change is the `-api` scope of the coordination feature
`org-device-room`; the `org-device-room-web` change carries the frontend tasks.
Shared contracts live in `openspec/coordination/org-device-room.md`.

## 1. Migration V27 (tables + dicts + menus)

- [x] 1.1 Write `V27__org_device_room.sql`: `CREATE TABLE system.org_device` (type
  varchar = the dict-1024 VALUE token) and `system.org_room` (no type column) per the
  coordination Shared Design (EntityBase audit columns, audit-block-tail order per
  V17/V18; zero physical FKs), the dict catalogs 1024 设备类型 (兼检查类型 - the
  single modality vocabulary) / 1025 设备状态 / 1026 归属类型 plus the guarded
  retirement of V26's 1021 检查类型 (1023 retired) with detail bands 1024001+ etc.
  (idempotent, V23/V26 house style), C pages 设备档案 /
  诊室配置 under 13 (CONTENT-guarded serial inserts + setval tail - the V15/V22
  discipline, no pinned ids: fresh-deployment V15 F nodes occupy ids 18+), F button nodes
  (`system:device:create/update/delete`, `system:room:create/update/status/delete`,
  serial inserts with NOT EXISTS guards), admin auto-grant guarded inserts. Every
  statement idempotent (hot test DB: hand-apply first, Flyway re-run = no-op).
  Verify: migration test asserting tables/columns/catalogs/menus exist after run and
  a second run changes zero rows.
- [x] 1.2 Add entities `OrgDevice` / `OrgRoom` (`entity/`, `@TableName`, Lombok combo
  `@Data @Builder @AllArgsConstructor @NoArgsConstructor`, EntityBase audit fields) and
  mappers `OrgDeviceMapper` / `OrgRoomMapper` (`mapper/`, BaseMapper + the room LEFT
  JOIN device page/detail select in XML or annotation - D2). Verify:
  `./mvnw -pl lumen-system -am compile -q` green.

## 2. Device registry endpoints

- [x] 2.1 DTOs `dto/deviceroom/`: `DeviceCreateRequest`, `DeviceUpdateRequest`
  (full editable surface, bean validation - required code/name/type/status/tag/oid;
  type = the dict-1024 VALUE token string),
  `DevicePageQuery` (orgId required, type/status/keyword optional, pageNum/pageSize).
  Verify: compiles; validation messages house style.
- [x] 2.2 `OrgDeviceService` (concrete, service-package-layout) with `getPage`,
  `getList`, `getById`, `create`, `update`, `delete`: org-scoped page (keyword =
  name/code contains, D4 duplicate-code check A0400, D3 did-same-org check A0400,
  D9 null-aware full update, D3 delete guard - live room reference count in msg). Controller
  `OrgDeviceController` (`/api/v1/system/org-devices`) shaped on OrgDepController
  (class gate `system:device:list`, method pins). Verify: RED→GREEN tests for each
  api delta scenario (page scoping, keyword+filters, create + both A0400 paths,
  update missing id, update clears an optional field, delete guard with room-count
  msg, picker list ordering).
- [x] 2.3 TDD the perm pins: `@PreAuthorize` strings `system:device:create/update/
  delete` present on the mutating methods (annotation-assertion test, sign-log-query
  precedent). Verify: test green.

## 3. Room registry endpoints

- [x] 3.1 DTOs: `RoomCreateRequest` (oid/did/name/enable required; devId,
  callIp/callPort/callMac optional), `RoomUpdateRequest` (full surface incl. nullable
  devId = rebind/clear), `RoomPageQuery` (orgId required, depId/name optional),
  `RoomStatusRequest` (enable). Read DTO `RoomRow` (room fields + devId/devName/
  devAe/devIp/devPort, nulls when unbound). Verify: compiles.
- [x] 3.2 `OrgRoomService` with `getPage` (LEFT JOIN device summary - D2, no N+1),
  `getList`, `getById` (detail echo), `create` (D3 oid/did-same-org/devId-same-org
  checks A0400, D4 duplicate name-in-dep check), `update` (D9 full-replace incl. rebind/
  clear dev_id), `setStatus` (enable only, D6), `delete` (logical). Controller
  `OrgRoomController` (`/api/v1/system/org-rooms`, class gate `system:room:list`,
  method pins incl. `system:room:status`). Verify: RED→GREEN tests for each api delta
  scenario (page carries device summary + nulls when unbound, detail echo, create
  binds by reference without copying device fields, dead/foreign devId/oid/did reject
  A0400 no-change, update rebind + clear, status toggle only mutates enable, delete).
- [x] 3.3 TDD the perm pins incl. `system:room:status`. Verify: test green.

## 4. Verify readiness

- [x] 4.1 Full module suite `./mvnw -pl lumen-system -am test -q` green (existing
  suites + new). Redirect output to `logs/build-backend.log` / `logs/test-backend.log`
  (exit code + counts only in the report).
  RESOLUTION (user ruling 2026-11-12, accept-drift): this change's 21 new tests are
  ALL GREEN (build exit 0; the 21 device/room/seed/perm-pin tests pass on the shared
  test DB). The full-suite run shows 5 PRE-EXISTING failures traced to one external
  drift - shared-DB org 1 was hand-renamed to 「Lumen 的测试医院」 on 2026-11-03
  while the seed tests guard V16's 未命名医院 placeholder (AuthSeedMigrationTests /
  AuthOrgCrudTests / AuthDataLayerTests cascade on that one value). None of this
  change's code paths touch org_info. Accepted as known external drift; repair
  deferred to a later change. ALSO RECORDED (repo-level debt, out of scope): the
  fresh-deployment probe fails at the EXISTING V24 migration (role_info_v24 column
  reference) - the migration chain has never been fresh-path clean since V24; the
  shared DB was hot-repaired by hand at the time.
- [x] 4.2 Spec-alignment self-check against the api delta WHEN/THEN list (L2
  dry-run): every scenario maps to a test name; gaps fixed before verify.

## 5. Post-verify revision (second-round ruling 2026-11-12: 设备定科 + 根=全部院区)

- [x] 5.1 RED tests: device create WITHOUT `did` rejects A0400 (fixture helpers gain a
  department); page WITHOUT `orgId` returns every campus's rows (root-means-all);
  device picker list narrows by `depId`. Then GREEN: `OrgDeviceService` did-required
  guard (create + update), page queries drop the orgId-required rejection (optional
  eq), `getList(orgId, depId)` + mapper depId filter. Verify: new + adjusted suites
  green.
- [x] 5.2 Refresh execution-contract hash after the artifact edits; guard
  proposing→applying; full module suite green; re-verify (verify.md overwrite).

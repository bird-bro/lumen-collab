# Design

## Context

Cross-stack feature `org-device-room` (coordination doc: `openspec/coordination/
org-device-room.md` - the single source for the shared table/dict/menu/API contracts;
this design covers the backend half only). Reference implementation: OPS
`ops_sev/birdbro-manage/.../manage/org/*` + `birdbro-launch/.../controller/manage/
OrgController.java`, re-modeled per the 2026-11-10 split ruling and the 2026-11-12
field-trim ruling.

## Goals / Non-Goals

- Goals: `org_device` + `org_room` tables, the dict catalogs 1024-1026 (1023 retired,
  V26's 检查类型 1021 retired by unification),
  menu seeds (18/19 + F nodes), the two endpoint families with button-perm gating,
  service-level referential checks, TDD coverage of the api delta scenarios.
- Non-Goals: 技师↔检查室绑定 (workstation feature), 叫号/Box runtime protocols,
  cross-service Feign contracts (no other service consumes the registry yet),
  MWL/MPPS routing (integration consumes later).

## Decisions

### D1: Package + layering (naming.md service-package-layout)

`org.birdbro.lumen.system` additions: `entity/OrgDevice`, `entity/OrgRoom`; concrete
lumen services `service/OrgDeviceService`, `service/OrgRoomService` (single impl, no
interface - MenuService precedent); mappers `mapper/OrgDeviceMapper`, `OrgRoomMapper`;
DTOs `dto/deviceroom/` package (`DeviceCreateRequest`, `DeviceUpdateRequest`,
`DevicePageQuery`, `RoomCreateRequest`, `RoomUpdateRequest`, `RoomPageQuery`,
`RoomStatusRequest`, response shape = entity + device-summary fields on the room read
DTO `RoomDetail`/`RoomRow`). Controllers `controller/OrgDeviceController`
(`/api/v1/system/org-devices`), `controller/OrgRoomController` (`/api/v1/system/org-rooms`),
shaped 1:1 on `OrgDepController` (class gate + method perm pins).

### D2: Binding is by reference, reads join (the split-model consequence)

Room create/update stores ONLY `dev_id`; AE/IP/Port are never copied onto the room.
Reads (page rows + detail) resolve the device summary via a single LEFT JOIN mapper
query (`RoomRow` carries devId/devName/devAe/devIp/devPort) - no N+1 service loop.
`GET /list` (picker feed) stays summary-light. Unbind = `devId: null` on update; the
column is nullable by design (a room can exist before its device arrives).

### D3: Service-level referential checks (zero physical FKs)

`oid` must hit a live `org_info` row; `did` a live `org_dep` row of the SAME org;
room `devId` a live `org_device` row of the SAME org. Violations reject A0400
no-change. Device delete guard: reject when any live room references it (count query),
msg names the count - the room must rebind first. Room delete has no guard (no
downstream rows exist yet; the workstation feature will add its own).
SECOND-ROUND RULING (2026-11-12, prototype review): the DEVICE `did` is REQUIRED on
create + update (A0400 on a missing anchor) - the 使用科室 asset convention, OPS's
required did, and the department-mediated exam workflow; the DB column stays
nullable (no V27 churn), the service is the enforcement point.

### D11: The type rulings (third round, 2026-11-12, user smoke review)

Three user rulings reshape the type surface mid-apply:

- **Modality token as the machine value**: the dict detail `code` stays the integer
  admin key for every catalog, but 设备类型/检查类型 make the detail VALUE the machine
  token - `org_device.type` is varchar(16) storing 'CT'/'DR' verbatim (EIS
  DictExam.modality shape; future exam tables join 检查类型 on the same token). The
  numeric catalogs 设备状态/归属类型 keep the code-stored convention. The frontend
  binds the type select to `item.value` and resolves labels by value
  (`resolveDictLabelByValue`); status/tag stay code-keyed.
- **检查类型 RETIRED - 1024 is the single modality catalog** (final unification
  ruling 2026-11-12; an intermediate dual-catalog mirror plan - a new 1027, then an
  in-place 1021 alignment - was superseded): ONE vocabulary 设备类型/检查类型共用,
  no lockstep maintenance burden. V27 deletes the V26 catalog 1021 by name (guarded,
  catches any duplicate band) and retitles 1024's remark to the dual purpose. The
  users page's 检查类型 multi-select reads '设备类型'; the modalitys CSV keeps the
  dict-code storage shape against 1024's codes.
- **诊室类型 dissolved**: org_room carries NO type column, the 1023 catalog is never
  seeded (the band slot retires, NOT reused; 检查类型 is the adopted 1021), and the
  room page/form lose
  the type surface. Type matching chains 检查类型 ↔ 设备类型 (the bound device), the
  room stays a pure business place under its department.

### D10: Page orgId semantics - the root means all campuses

Both page queries treat `orgId` as OPTIONAL: absent lists every campus (the tree's
hospital-root selection shows the whole estate), a present orgId filters to that
campus - exactly the department page's semantics (OrgDepPageQuery orgId absent = all).
The original orgId-required rule left the auto-selected root showing an empty table
on multi-campus hospitals. The device picker list narrows by the optional `depId`
(the room form feeds from its own 负责科室); binding validation stays same-org.

### D4: Uniqueness - service-enforced, org-scoped

Duplicate live `code` within the same org rejects for devices (OPS keyed the table by
code per org). Rooms: duplicate live `name` within the same `did` rejects (room names
are operational labels inside a department). Checks run in the service before insert /
inside the update transaction; not DB constraints (logical delete would break a plain
unique index).

### D5: Dict rendering stays name-lookup based

Endpoints return raw dict codes; the web renders labels via the existing by-name dict
lookup (`dictItemsByName` + raw-code fallback, role-type-dict D4 shape). No typeName/
statusName shadow columns (the OPS copies are dropped with the trim).

### D6: Status toggle mirrors OrgDepController

`PUT /org-rooms/{id}/status` takes `{ "enable": <code> }`, perm `system:room:status`,
only mutates that column. Devices have NO toggle - `status` is a lifecycle dict field
edited through the normal update (an asset state, not a row flag).

### D7: Migration shape

One additive `V27__org_device_room.sql`: two CREATE TABLEs (EntityBase audit columns,
audit-block-tail column order per V17/V18 discipline), four dict catalogs + details,
two C menus (ids 18/19, ON CONFLICT-guarded) + F nodes (serial, NOT EXISTS-guarded) +
admin auto-grant (guarded). Follows the V23/V26 guarded-DDL house style; safe to
hand-apply on the shared hot test DB before Flyway re-runs it as a no-op.

### D8: Dates as `date` columns

production/purchase/acceptance/start/end are `date` (no time component - OPS used
`yyyy-MM-dd` JSON formatting over datetime columns); serialized as ISO strings.

### D9: Full updates are null-aware

Update requests model the full editable surface, but nullable optional fields must be
clearable too. The service SHALL not rely on MyBatis-Plus' default null-skip
`updateById` for those fields: it uses an explicit update wrapper / mapper UPDATE for
the full editable column set (including room `dev_id`), after validating required
values and references. This is the mechanism behind `devId: null` clearing.

## Risks / Trade-offs

- Dict seeds (1024/1025) invent lumen-specific vocabularies where OPS had its
  own values we did not copy - listed in the coordination doc for the user to adjust
  at verify time; codes are data, fixable without schema churn.
- `org_room.name` uniqueness scoped to `did` (not org) is looser than OPS's flat list;
  acceptable because rooms always render inside a department context.

## Migration Plan

1. Merge V27; Flyway applies on next service boot (hot test DB: hand-apply first per
   house discipline).
2. No backfill - both tables start empty; OPS data import is not in scope.

## Open Questions

- (none - field trim, binding semantics, department anchoring, and page semantics
  ruled 2026-11-12; vocabulary values flagged as user-adjustable data)

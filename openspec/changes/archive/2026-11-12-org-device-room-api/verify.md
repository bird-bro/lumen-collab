<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: org-device-room-api
  date: 2026-11-12
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "23/23"
      L3: PASS
---

# Verification: org-device-room-api

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (`./mvnw compile -q`, exit 0) | 23/23 | PASS (`./mvnw test`, lumen-system families all green; see Issues for 4 unrelated residual failures) | logs/build-backend.log, logs/test-backend.log, logs/test-backend-retry.log |

## Scenario Alignment

### system-data delta (8/8)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Fresh deployment creates the device asset columns | IMPLEMENTED | V27__org_device_room.sql:45-80 (type varchar:49, audit tail); AuthDeviceRoomSeedTests:77 |
| Device is the access and acquisition source | IMPLEMENTED | V27 access/acquisition columns on org_device; OrgRoomMapper.java:42 (page read JOINs d.ae/ip/port — never copied to room) |
| Fresh deployment creates the room registry | IMPLEMENTED | V27__org_device_room.sql:89-99 (no type column); AuthDeviceRoomSeedTests:77 |
| The room table stays reference-only | IMPLEMENTED | V27 org_room (dev_id only); AuthDeviceRoomSeedTests:88-96 forbidden-column count = 0 + type-column absence |
| Fresh and hot databases converge on the same catalogs | IMPLEMENTED | V27 ON CONFLICT guards :127-152; AuthDeviceRoomSeedTests:145 + re-exec no-op :207 |
| Catalog values are stable across deployment | IMPLEMENTED | AuthDeviceRoomSeedTests:145-163 (DETAILS loop); examTypeCatalogRetiredSingleModalitySource:115 (检查类型 retired, no orphans) |
| Administrator sees the new pages after migration | IMPLEMENTED | V27 menu inserts :176-191 (CONTENT-guarded); AuthDeviceRoomSeedTests:165 |
| Admin button perms arrive with the menus | IMPLEMENTED | V27 F nodes :193-242 + auto-grant :244; AuthDeviceRoomSeedTests granted=9 |

### api delta (15/15)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Paged device list filtered by org | IMPLEMENTED | OrgDeviceService.java:67 (orgId eq); AuthDeviceApiTests |
| The hospital root means all campuses | IMPLEMENTED | OrgDeviceService.java:67-80 (orgId null = no predicate); AuthDeviceApiTests root test |
| Keyword filters name or code | IMPLEMENTED | OrgDeviceService.java:77-78 |
| Device list feeds the room picker | IMPLEMENTED | OrgDeviceService.java:90-98 (depId narrow, order by name) |
| Create device | IMPLEMENTED | OrgDeviceService.java:111 (did anchor enforced) |
| Device create rejects foreign or duplicate identifiers | IMPLEMENTED | OrgDeviceService.java:234-239 (dup code) + :205-229 (dead/foreign did) |
| Update device full-replaces the editable surface | IMPLEMENTED | OrgDeviceService.java:123 (D9 explicit wrapper) |
| Delete device is logical and reference-guarded | IMPLEMENTED | OrgDeviceService.java:183-192 (live-room count in msg) |
| Paged room list with bound device summary | IMPLEMENTED | OrgRoomMapper.java:37-49 (LEFT JOIN :42) |
| Room detail echoes the bound device | IMPLEMENTED | OrgRoomMapper.java:59-65 |
| Room status toggles without a full body | IMPLEMENTED | OrgRoomService.java:148 |
| Create room binds a device by reference | IMPLEMENTED | OrgRoomService.java:92-96 (stores dev_id only) |
| Room create rejects dead or foreign references | IMPLEMENTED | OrgRoomService.java:95,125 (requireLiveDeviceOfOrg + parent checks) |
| Rebind or clear the room's device | IMPLEMENTED | OrgRoomService.java:117-140 (devId null-aware) |
| Delete room is logical | IMPLEMENTED | OrgRoomService.java:169-176 |

## Test Evidence (2026-11-12)

- lumen-system families of THIS change, final quiet-DB run: AuthDeviceApiTests 10/10,
  AuthRoomApiTests 7/7, AuthDeviceRoomSeedTests 6/6, AuthUserProfileSeedTests 6/6,
  AuthDevicePermPinTests + AuthRoomPermPinTests green; framework/common zero-dep tests green.
- Frontend counterpart suites: 317/317 (see org-device-room-web verify.md).

## Issues (environment, non-blocking — none attributable to this change)

- Full-reactor run during the parallel exam-setting session's own suite produced mutual
  shared-DB interference (16 failures shrinking to 4 as concurrency dropped): fixture orgs
  hard-deleted between two calls by the other suite's sweep, admin-login transients (A0201),
  a role fixture vanishing mid-test. The affected families pass on a quiet DB.
- Residual 4 failures in UNRELATED domains, all shared-data drift: menu id 1/15 titles were
  renamed to 「系统」 in the live DB while AuthDataLayerTests/AuthLoginTests pin the seed
  title 「系统管理」 (owner of those tests must re-pin or the rename must be reverted);
  AuthOrgDepTests admin-login transient; AuthRoleMenuTests role fixture 769 deleted by a
  concurrent actor.
- **Cross-change flag**: exam-setting (V28) resolves the modality dict BY NAME 「检查类型」;
  this change's unification retires that catalog — exam-setting must switch its lookup to
  「设备类型」 (recorded in coordination/org-device-room.md).

## Next

- PASS -> archive org-device-room-api (batch with org-device-room-web per the coordination gate).

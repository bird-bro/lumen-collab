# Tasks

## 1. Backend — contract fields out (lumen-system, TDD)

- [x] 1.1 TDD red: extend `AuthDeviceApiTests` so the create/update fixture bodies
      and assertions carry NO box fields (delete the five fixture lines); the
      create/update-echo assertions stay green on the remaining surface — run
      `./mvnw -pl lumen-system -am test -q` and confirm the suite compiles+passes
      BEFORE entity/DTO edits (the DTOs still accept the absent fields today)
- [x] 1.2 Remove `boxAdapter/boxIp/boxPort/boxMac/boxPrint` from
      `DeviceCreateRequest` and `DeviceUpdateRequest`; strip the five
      `.set(OrgDevice::getBox*, ...)` lines from `OrgDeviceServiceImpl.update()`;
      remove the five fields from the `OrgDevice` entity — verify by
      `grep -rn "boxAdapter\|boxIp\|boxPort\|boxMac\|boxPrint"
      lumen-sev/lumen-system/src/main` returning only migration hits
- [x] 1.3 Run `./mvnw -pl lumen-system -am test -q` (JDK 11) — full lumen-system
      suite green including `AuthDeviceRoomSeedTests` (drop its box fixture lines
      too)

## 2. Backend — schema migration

- [x] 2.1 Add `lumen-system/src/main/resources/db/migration/V48__org_device_drop_box.sql`
      with five `ALTER TABLE system.org_device DROP COLUMN IF EXISTS box_adapter/box_ip/box_port/box_mac/box_print;`
      statements; verify the ledger has no V48 collision
      (`select version from system.flyway_schema_history order by version desc limit 3;`
      via pgsql-test-lumen) and a fresh `./mvnw -pl lumen-system -am test -q`
      migrates cleanly (the suite boots Flyway on the test DB)

## 3. Web — type mirror (no behavior)

- [x] 3.1 Remove the five box fields from `OrgDevice` and `DeviceCreateBody` in
      `lumen-web/src/api/types.ts`; delete the five null lines from the
      `device()` fixture in `src/test/device-page.test.tsx` and the device fixture
      in `src/test/room-page.test.tsx`; verify `pnpm tsc --noEmit` clean and
      `pnpm vitest run src/test/device-page.test.tsx src/test/room-page.test.tsx`
      green

## 4. Integration verification

- [x] 4.1 Full reactor check from `lumen-sev/`:
      `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw test -q`
      exit 0 (all services — proves the shared modules never referenced box
      fields); plus `openspec validate org-device-drop-box` PASS

## Workflow follow-up

- Sync delta → main spec (`/opsx:sync` or archive-time sync), then
  `/opsx:archive org-device-drop-box` after verify.md overall PASS (auto-commit
  per project convention).

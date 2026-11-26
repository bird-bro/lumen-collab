# Tasks

> Stacks AFTER registration-order-status-dict-sev (its V56 + enum collapse must be in
> the working tree/archived before this change applies — the coordination doc owns the
> order).

## 1. Kernel aggregate column (TDD)

- [x] 1.1 Aggregate persistence test FIRST: a created order stamps the supplied
      `roomId` on both entry branches and a legacy-shape order (no room) still loads —
      red against the current `ExamOrder`
- [x] 1.2 Add `roomId` to `ExamOrder` (lumen-core entity, OPS Lombok combo, javadoc:
      the exam-room anchor, nullable, surface-enforced) — green
- [x] 1.3 Add `V57__exam_order_exam_room.sql` to the lumen-us ledger (`ALTER TABLE
      exam_order ADD COLUMN room_id BIGINT NULL`) and bump the seed-test column
      ledger if the schema seed test pins columns

## 2. Provider internal rooms + Feign system adapter (TDD)

- [x] 2.1 Provider tests FIRST (lumen-system): the internal rooms list returns the
      dept's enabled rooms as `{id, name}` ordered by id, the detail carries
      `did`/`enable`, and a keyless caller is refused — red
- [x] 2.2 Implement the provider internal reads
      (`GET /api/v1/system/internal/org-rooms/list` + `/{id}`) in lumen-system under
      the existing `InternalKeyInterceptor` (the NumberIssueController precedent;
      console endpoints untouched) — green
- [x] 2.3 Adapter contract test FIRST: `SystemRoomApi` exposes the internal list +
      detail reads with the split fallback (list → empty, detail → throw); the suite
      stubs it via the `@MockBean` seam (feign-consumer-test-seam precedent) — red
- [x] 2.4 Implement `org.birdbro.lumen.feign.system` (`SystemRoomApi` reusing
      `lumen.feign.system.url` + slim DTOs + the fallback factory; add-only package)
      — green
- [x] 2.5 Add the `lumen.us.station-dept-id` knob to lumen-us `application.yml`
      (javadoc comment: the deployment declares the station's department; absent =
      directory empty + creates refused, fail closed)

## 3. Station rooms endpoint (TDD)

- [x] 3.1 Endpoint test FIRST: `GET /api/v1/us/rooms` (perm `us:order:create`) returns
      the configured dept's ENABLED rooms as `{id, name}` ordered by id; the fallback
      path returns empty — red
- [x] 3.2 Implement the directory service + controller wiring the adapter list with
      the configured dept id — green

## 4. Create path: require, validate, stamp (TDD)

- [x] 4.1 Create-refusal tests FIRST: roomless body → 检查室不能为空; unknown /
      disabled / other-dept room → 检查室不存在或未启用 — nothing persisted; the
      validation read failing (system down) refuses the create (fail closed) — red
- [x] 4.2 Implement validation + the both-branch stamp in `RegistrationOrderService`
      (validation BEFORE the aggregate dance; one stamp point) — green
- [x] 4.3 View test FIRST: page rows carry `roomId` + `roomName` (dangling id → null
      name) — red; extend `RegistrationOrderView` + the read join — green

## 5. Change-scoped regression

- [x] 5.1 Run the backend suite once green — verify: `./mvnw test -q` exit 0 (JAVA_HOME
      pinned to JDK 11)

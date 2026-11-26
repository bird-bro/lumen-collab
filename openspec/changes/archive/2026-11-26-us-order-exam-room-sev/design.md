# Design: us-order-exam-room-sev

> Shared decisions live in `openspec/coordination/us-order-exam-room.md`. This file
> records the backend-side how.

## D1 — Kernel column `room_id`, us ledger V57

`exam_order` gains `room_id` BIGINT NULL (V57__exam_order_exam_room.sql in the
**lumen-us** ledger — the service hosts the kernel DDL; **V56 is owned by the active
registration-order-status-dict-sev pair**, this change stacks after it). NULL, not
NOT NULL DEFAULT: there is no natural default room and no backfill story for legacy
rows. The requirement (must supply a room at create) is a SURFACE rule, not a column
rule — enforcement lives in the create path, so the column never needs a migration
dance to relax. `ExamOrder` (lumen-core) gains the `roomId` field (the OPS Lombok
combo; plain column mapping).

## D2 — Create contract: required, validated, stamped

`UsOrderCreateBody.roomId` is required. Validation order in
`RegistrationOrderService.createOrder` (BEFORE the aggregate/registration dance — a
bad room must refuse with nothing persisted, mirroring the A0400 verbatim-refusal
posture):

1. null → refuse 检查室不能为空
2. the room row does not exist or is disabled → refuse 检查室不存在或未启用
3. the room's `did` ≠ the configured `lumen.us.station-dept-id` → refuse the same
   message (the room scope is the station's department, resolved server-side)

The room row lookup goes through the Feign system adapter's single-room read
(the provider's INTERNAL detail endpoint — the console endpoints are JWT +
`system:room:list` gated and unreachable for a service call: no JWT exists between
services). The validation read is fail-CLOSED: if lumen-system is unreachable the
create is refused (a required field must not be stamped unverifiable — the Sentinel
fallback for the VALIDATION read throws, unlike the directory read's empty-list
fallback). Then both branches (walk-in / pre-registration) stamp `roomId` on the
aggregate — one stamp point, not two.

## D3 — Station room directory + the Feign system package + the provider internal family

**Provider side (lumen-system)**: new INTERNAL reads
`GET /api/v1/system/internal/org-rooms/list?depId=` (enabled rooms, slim `{id, name}`)
and `GET /api/v1/system/internal/org-rooms/{id}` (the room row with `did`/`enable`),
registered under the EXISTING `InternalKeyInterceptor` (the `NumberIssueController`
`/api/v1/system/number-rules` internal family precedent — the patient internal-family
shape). The console registry endpoints are untouched. The provider-side tests assert
the key gate refuses keyless callers and the payloads carry the validation fields.

**Consumer side (lumen-feign)**: new add-only package
`org.birdbro.lumen.feign.system` — `SystemRoomApi` (`@FeignClient` reusing the
EXISTING `lumen.feign.system.url` target key — the NumberApi family, no new config
family) with the two reads, slim DTOs (`RoomSummaryDto` id+name; `RoomDetailDto`
id/oid/did/name/enable), and a fallback factory whose LIST read returns the empty
list (honest block) while the DETAIL read THROWS C0001 (fail-closed validation).
New config knob `lumen.us.station-dept-id` (Long, REQUIRED for the directory +
validation — the yml entry documents it; absent knob = the directory returns empty
with the config error logged, and creates are refused 检查室不存在或未启用 — fail
closed both ways).

New endpoint `GET /api/v1/us/rooms` (perm `us:order:create` — the pick is a
create-time decision): returns the configured department's ENABLED rooms as
`{id, name}` rows ordered by id. Sentinel fallback on the LIST read = empty list +
the load failure surfaced (the desk sees an empty Select; submission is refused
client/server-side anyway).

## D4 — View: roomId + roomName, name joined at read time

`RegistrationOrderView` gains `roomId` (the stored anchor) + `roomName` (joined from
org_room at page/view time; a dangling id → null name → the UI renders '—'). A name
snapshot column was REJECTED as YAGNI (coordination doc); the nurse-edit proposal
would own any future snapshotting.

## D5 — Feign consumer test seam

Suite-level `@MockBean` stubbing of `SystemRoomApi` (the feign-consumer-test-seam
precedent): room-fixture tests stub the adapter instead of wiring a live
lumen-system — the suite stays zero-infrastructure.

## D6 — Wire contract

| Field | Where | Shape |
|---|---|---|
| `roomId` | create body (required) | number |
| `roomId` | order view | number (null for legacy rows) |
| `roomName` | order view | string or null |
| `GET /api/v1/us/rooms` | station surface | `{code, data: [{id, name}]}` |
| `lumen.us.station-dept-id` | us application.yml | Long, deployment-set |

## D7 — Explicitly out of scope

- Nurse editing of a created order (future proposal — room edits ride it).
- Room↔slot binding (号源不绑房间 — slots × rooms free combination, ruling 2026-11-26).
- Radiology/endo adoption (kernel-wide column; department services adopt with their
  own proposals).

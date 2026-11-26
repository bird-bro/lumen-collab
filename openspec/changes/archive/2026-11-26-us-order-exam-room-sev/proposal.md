# Proposal: us-order-exam-room-sev

> Cross-stack feature `us-order-exam-room` — backend half. The frontend half is
> `us-order-exam-room-web`; shared decisions live in
> `openspec/coordination/us-order-exam-room.md`.

## Why

The US registration order cannot record WHERE the exam happens: neither the kernel
order nor the US create path carries an exam-room field, so the desk cannot route a
patient to a room and the booking dialog cannot stamp the room onto a pre-registered
order. The room registry (`org_room`, org-device-room split model) already exists and
lists rooms; the order side is the missing half.

## What Changes

- New kernel column `exam_order.room_id` BIGINT NULL (us ledger **V57** — stacks on
  the active registration-order-status-dict-sev pair, which owns V56). NULL by
  design: no natural default, no backfill for legacy rows; the API layer enforces the
  requirement, the DB stays permissive, legacy rows read null.
- `ExamOrder` (lumen-core) gains the `roomId` field; both create branches
  (walk-in workbench / booking dialog → PRE_REGISTERED) stamp it.
- **BREAKING** (US create contract): `UsOrderCreateBody.roomId` is REQUIRED —
  submission without a room is refused (validation error); unknown, disabled, or
  out-of-department room ids are refused. The booking dialog and the workbench share
  the one create endpoint.
- `RegistrationOrderView` (and the page row) gain `roomId` + `roomName` — the name
  resolved by join at read time; a dangling id yields a null name (UI renders '—').
- New Feign consumer package `org.birdbro.lumen.feign.system` (`SystemRoomApi` over the
  internal rooms family, reusing the existing `lumen.feign.system.url` target key — the
  NumberApi family) + new config knob `lumen.us.station-dept-id` — the deployment
  declares which department the station serves.
- **Provider side**: lumen-system gains INTERNAL room reads —
  `GET /api/v1/system/internal/org-rooms/list?depId=` (enabled rooms of the department,
  slim `{id, name}` rows) and `GET /api/v1/system/internal/org-rooms/{id}` (the room
  row with `did`/`enable` for the create-time validation) — guarded by the existing
  `InternalKeyInterceptor` (the NumberIssueController `/issue` + patient
  internal-family precedent; the console room endpoints stay JWT + `system:room:list`
  gated — service-to-service calls have no JWT).
- New us-station endpoint `GET /api/v1/us/rooms` — the station's department's enabled
  rooms (id + name only), station-scoped server-side (the web never passes org/dep
  ids). Fallback yields an empty list: a required field with no options must block
  the desk honestly, not silently pass.

## Capabilities

### New Capabilities

(none — this change modifies existing capabilities only)

### Modified Capabilities

- `exam-workflow-kernel`: the Exam-Order Aggregate carries `room_id` and both create
  branches stamp it.
- `us-station`: registration create requires and stamps the exam room; the station
  exposes its department's room directory (`GET /api/v1/us/rooms` — the US endpoint
  contracts live in the us-station capability, the established home).
- `api`: the system service's room surface gains the internal reads family
  (`/api/v1/system/internal/org-rooms/**`, X-Internal-Key guarded) — the Feign
  consumer's provider side; the console registry endpoints are untouched.

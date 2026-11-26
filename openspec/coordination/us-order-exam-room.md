# us-order-exam-room — Coordination

> Feature: US 登记单检查室字段全链路（列 + 契约 + 两个录入点 + 列表展示）。
> Cross-stack pair tracked here. Created 2026-11-26.

## Problem

The US registration order has no exam-room (检查室) field: the desk cannot record
WHERE the patient goes for the exam. The room registry itself exists
(`org_room`, the org-device-room split model, rooms bind `oid`/`did` and one optional
device), but neither the kernel order nor the US create path carries a room. The
lumen-web workbench also has no session-level department identity (UserProfile carries
no org/dept ids), so the room list cannot be scoped client-side.

## User rulings (2026-11-26)

1. **预约=登记 (carried over, us-prereg-as-registered)**: a booked patient arrives and
   goes straight to the exam — no re-check-in. Exceptions are handled by a NURSE
   editing the registration order (out of scope here — future proposal).
2. **预登记的检查室在预约时选定**: the booking dialog (选择预约时段) gains a room
   Select — date + slot + room confirmed in one step, stamped onto the
   PRE_REGISTERED order at creation.
3. **现场单（WALK_IN）检查室必选，默认上次房间**: the workbench 检查信息 section
   gains a required room Select (section-3 left column top row), defaulting to the
   workstation's last choice; submission without a room is refused.
4. **护士修改登记单 = 另开提案**：room editing on existing orders rides the future
   nurse-edit proposal; this change only CREATES with a room.

## Shared Design

- **Kernel column**: `exam_order.room_id` BIGINT NULL in the `us` schema
  (lumen-us hosts the kernel DDL ledger). NULL, not NOT NULL DEFAULT: there is no
  natural default room and no backfill story for legacy rows — the API layer enforces
  the requirement (validation), the DB stays permissive, legacy rows read '—'.
  Room identity is by id (dict-lookup-by-id-anchor precedent); the view resolves the
  display name by join at read time (dangling id → null name → UI '—'). A name
  snapshot was rejected as YAGNI — revisit only if 历史单 display integrity becomes a
  real requirement (the nurse-edit proposal would own it anyway).
- **Create contract**: `UsOrderCreateBody.roomId` (required, validated) feeds BOTH
  entry kinds — the booking dialog path (PRE_REGISTERED, bookingTime set) and the
  walk-in workbench path share the one create endpoint. The service validates the
  room exists, is enabled, and belongs to the station's department before stamping.
- **Station room read = Feign over an internal family**: `org_room` lives in the
  system schema; sync cross-service reads go OpenFeign (architecture rule). The
  console room endpoints are JWT + `system:room:list` gated — service-to-service
  calls have no JWT, so lumen-system gains INTERNAL reads
  (`GET /api/v1/system/internal/org-rooms/list?depId=` + `/{id}`) guarded by the
  existing `InternalKeyInterceptor` (the NumberIssueController `/issue` + patient
  internal-family precedent). New `lumen-feign` package `org.birdbro.lumen.feign.system`
  — `SystemRoomApi` reuses the EXISTING `lumen.feign.system.url` target key (the
  NumberApi family — no new config family). The US station's department identity
  arrives as a NEW config knob `lumen.us.station-dept-id` (the deployment declares
  which dept the station serves — the same way nginx routes `/api/v1/us/**` to it).
  New us-station endpoint `GET /api/v1/us/rooms` returns the dept's enabled rooms
  (id + name only); Sentinel fallback on the LIST read yields an empty list — the
  desk sees an empty room Select plus the load error, registration is honestly
  blocked (a required field with no options must not silently pass); the VALIDATION
  read (room detail) fails CLOSED (throws — a roomless/unverifiable create is
  refused, never stamped unverifiable).
- **Frontend scoping**: the web never passes org/dep ids — the us rooms endpoint is
  station-scoped server-side (every other us read is). Last-choice memory is a
  localStorage key (`us-last-room`), shared by both entry points.
- **List display**: the order card (compact) and the wide table gain a 检查室 line —
  a room assignment invisible in the list would be untrustworthy.
- **Modality NOT involved**: `org_room` has no type (device-room split ruling
  2026-11-10); rooms are scoped by department, not modality. Deriving scope via the
  bound device's modality was rejected (rooms may legitimately have no device bound).

## Stacking

- **Applies AFTER `registration-order-status-dict-sev/-web`** (active, same page +
  contract surface): that pair owns us-ledger V56 and the BOOKED removal; this pair's
  migration is **V57** (us ledger) and its frontend builds on the post-dict page.
- us-apply-info / us-booking-slot-picker surfaces are otherwise untouched: the
  booking dialog gains the room Select in its day-summary area; the ext/apply fields
  are untouched.

## Change registry

| Change | Stack | Scope |
|---|---|---|
| us-order-exam-room-sev | lumen-core + lumen-feign + lumen-us | kernel entity column + V57 DDL + create validation/stamp + view fields + Feign system room adapter + station-dept-id knob + GET /us/rooms |
| us-order-exam-room-web | lumen-web | workbench section-3 room Select (required + last-choice default) + booking dialog room Select + rooms client + list display |

## Spec touch map

- exam-workflow-kernel: Exam-Order Aggregate (MODIFIED — room_id column + stamp rule)
- us-station: Registration (MODIFIED — create requires room; ADDED — station rooms read)
- api: US create body/view fields + GET /api/v1/us/rooms
- web-us-station: workbench + booking dialog room selection scenarios

## Out of scope

- Nurse editing of a registration order (future proposal; room edit rides it).
- Room↔slot binding (号源不绑房间 — ruling: slots × rooms free combination).
- Radiology/endo rooms (the column is kernel-wide; department services adopt it with
  their own proposals).

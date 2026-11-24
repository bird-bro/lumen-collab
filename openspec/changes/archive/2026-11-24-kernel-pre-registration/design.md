# Design: kernel-pre-registration

> Parent coordination: `openspec/coordination/us-registration-redesign.md`; UI authority
> `docs/prototypes/us-registration-redesign.html` (拍板 02-B / 06-A). See proposal.md for
> motivation.

## Context

The kernel today: `ExamOrder` (no booking timestamp; `booking_no` reserved and unused),
`ExamOrderRegistrationService.registerOrder(identity, order, execution, report)` with the
entry status as the CALLER's choice (spec Patient Registry Port: "entry state stays the
caller's choice — the aggregate service refuses a stateless document"), state machine
`BOOKED → REGISTERED` (arrival confirmation), refusals via
`BusinessException(ExceptionEnum.ERROR_A0400, <中文消息>)`, kernel DDL as copy-from sources
under `lumen-core/src/main/resources/db/kernel/` (V1 applied by kernel tests to the
test-only `core` schema; each department vendors its own ledger). The US service derives
nothing today — the US proposal (②) will call the policy helper this change adds.

## Goals / Non-Goals

**Goals**
- `booking_time` on the order document; slot-anchored pre-registration semantics.
- One-transaction derived entry status (now/past/NULL → REGISTERED; future → BOOKED).
- Per-modality slot capacity config (granularity/capacity/open windows) with vendorable DDL.
- Capacity + window + past-time refusals through the house A0400 pattern.

**Non-Goals**
- No slot entities, reservation, locking, cross-day schedules (EIS BookingNumber — v2 号池).
- No pre-registration number issuance (`booking_no` stays unused for now).
- No controller/API surface (kernel carries none; contract extension is proposal ②).
- No config CRUD UI (the web delta, proposal ③).
- No change to existing state-machine edges or to `registerOrder`'s signature.

## Decisions

- **D1 — Policy helper, not a `registerOrder` rewrite.** The entry-status derivation ships
  as a kernel policy component (e.g. `PreRegistrationPolicy.deriveEntryStatus(bookingTime)`):
  NULL/now/past → REGISTERED, future → BOOKED. `registerOrder` keeps its "caller chooses"
  contract; proposal ②'s create flow calls the helper then passes the derived status.
  *Alternative rejected:* changing `registerOrder` to derive internally — it would break the
  documented caller-choice contract (spec Patient Registry Port) and force HIS two-phase
  intake through the same path.
- **D2 — Capacity check belongs to the kernel service, the count query to the caller's
  mapper.** The kernel `ExamSlotCapacityService` exposes `validatePreregistration(modality,
  bookingTime, occupancyCount)` + `resolveSlotWindow(config, bookingTime)`; the occupancy
  count itself is a `SELECT count(*)` on the department's own `exam_order` (status=BOOKED,
  booking_time in window) — the department service runs it against its own schema and hands
  the number in. *Why:* the kernel is a library without its own datasource for department
  schemas; one count keeps the kernel schema-free.
  *Alternative rejected:* kernel connects to department schemas directly (violates the
  vendored ownership).
- **D3 — Config shape: one row per modality, windows as jsonb.** `exam_slot_config(id,
  modality unique, granularity_minutes int default 30, slot_capacity int default 4,
  open_windows jsonb default '[]' → `[{"start":"08:00","end":"12:00"},...]`) with the house
  audit columns. jsonb windows per the flexible-structure rule (variable pair count);
  granularity/capacity are ints. Empty `open_windows` = pre-registration refused (opt-in).
  *Alternative rejected:* a generated slot table (号格实体) — v2 号池 territory, and it
  would couple the kernel to schedule lifecycle.
- **D4 — Slot window resolution is pure time math.** Window resolution floors
  `booking_time`'s time-of-day to the granularity grid inside a window (e.g. 09:17 with
  30-min granularity → 09:00–09:47 window [09:00,10:00)) and the occupancy count matches
  `booking_time >= windowStart AND < windowEnd` on the same date. Future-only enforcement:
  `booking_time > now` for the BOOKED derivation; past → the caller refuses (A0400) before
  invoking the count.
- **D5 — Refusals reuse `ExceptionEnum.ERROR_A0400` with descriptive messages** (kernel
  aggregate/registration precedent) — no new error-code enum value; the message carries the
  slot/window context (该时段已满 / 不在开台时间窗内 / 预登记时间不能早于当前时间 / 该检查类型未开通预登记).
- **D6 — Vendored DDL source as a second kernel file.** New copy-from source
  `db/kernel/V2__exam_kernel_pre_registration.sql`: `ALTER TABLE exam_order ADD COLUMN
  booking_time timestamp;` + `CREATE TABLE exam_slot_config (...)` + unique index on
  modality + GIN on open_windows. Kernel tests apply V1+V2 to the test-only schema; each
  department vendors V2's contents as its next ledger number.
- **D7 — Race window on the capacity count is accepted.** Two concurrent creates in the
  same slot can both pass the count check (read-then-insert without a slot lock).
  *Mitigation:* the count + insert run inside the create transaction with a `FOR UPDATE`
  lock on the modality's config row as the serialization point (cheap, one row); the
  residual risk (two departments on isolated schemas sharing nothing) is nil, and v1 US
  volume makes a rare overbook acceptable — surfaced as a known trade-off, not silently.

## Risks / Trade-offs

- [Concurrent same-slot creates can overbook beyond capacity] → `FOR UPDATE` on the
  config row inside the create transaction serializes per modality; documented residual
  risk, EIS-grade slot locking deferred to the v2 号池.
- [Clock skew between app and DB shifts the now/past boundary] → derive with the DB
  server time (`now()` through the count query context), not app clocks; single-host v1
  keeps the risk theoretical.
- [Departments forgetting to vendor V2] → kernel tests assert the source file exists and
  applies; proposal ②'s tasks carry the vendoring step explicitly (coordination gate).
- [Open-windows jsonb malformed by a future config UI] → kernel validation fails closed
  (unparseable windows = pre-registration refused, same as unconfigured).

## Migration Plan

1. Kernel tests apply V1+V2 to the test-only `core` schema (additive: new column + new
   table; no data migration).
2. Departments vendor V2's contents as their next Flyway number when they adopt
   pre-registration (US: proposal ②). `booking_time` NULL on all existing rows — zero
   backfill.
3. Rollback: drop the column/table; the kernel policy helper is inert for NULL
   booking_time, so callers that never pass it are unaffected.

## Open Questions

- Slot-config CRUD surface (which service exposes it, URL family) — proposal ②/③ decide;
  the kernel only fixes the storage shape.
- Whether pre-registered orders should occupy capacity until arrival only, or also block
  same-slot walk-in volume — v1 counts BOOKED only (spec scenario), revisit with real
  desk feedback.

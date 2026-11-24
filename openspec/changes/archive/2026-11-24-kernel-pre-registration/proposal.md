# Proposal: kernel-pre-registration

> Cross-stack parent: `openspec/coordination/us-registration-redesign.md` (proposal ① of ③;
> UI authority `docs/prototypes/us-registration-redesign.html`, 拍板 01-07 all settled).
> Kernel-first: the US registration proposal (②) and the web delta (③) consume this capability.

## Why

The US workstation redesign (拍板 02-B) makes submit register in one step and redefines
预约 as **预登记** (pre-registration): a checkup — e.g. a prenatal follow-up — booked to a
specific day + time slot. The kernel has a `BOOKED` entry state but no booking timestamp
and no slot-capacity concept, so a pre-registration cannot be placed on a slot, cannot be
refused when a slot is full, and the desk cannot distinguish a pre-registered order from a
walk-in by data. Radiology and endoscopy workstations will need the same mechanism, so the
capability belongs in the department-neutral kernel, not the US service.

## What Changes

- Add `booking_time timestamp` to the order document (`exam_order`) — the pre-registration
  slot anchor. `booking_no` already exists and stays as-is (walk-in orders carry none; no
  pre-registration number is issued in v1).
- Add a **derived entry status** policy: the create flow derives the entry status from
  `booking_time` in one transaction — immediate/past-or-now → `REGISTERED` (现场单, submit
  registers directly); future slot → `BOOKED` (预登记态). The `BOOKED → REGISTERED`
  state-machine edge is unchanged and remains the arrival confirmation for pre-registered
  orders. The existing "entry state stays the caller's choice" contract is preserved — the
  policy is a kernel-provided helper callers use, not a forced rewrite of `registerOrder`.
- Add a **slot capacity config** (kernel entity + vendored-source DDL): per modality —
  slot granularity in minutes (default 30), capacity per slot (default 4), open time
  windows (jsonb array of `{start,end}` `HH:mm` pairs, default none = no pre-registration
  allowed). Department services vendor the DDL into their own schema and own their config
  rows.
- Add a **slot capacity validation service**: for a pre-registration create, validate the
  requested `booking_time` against the config (inside an open window, not in the past) and
  count the live `BOOKED` orders whose `booking_time` falls in the same slot window —
  refusal at/above capacity with a named `A0400` message; capacity-unconfigured modality
  refuses pre-registration (the safe default: pre-registration is opt-in per modality).
- Refusals ride the house `BusinessException(ExceptionEnum.ERROR_A0400, <中文消息>)`
  pattern (kernel precedent: aggregate/registration services) — no new error-code class.
- Kernel vendored-source DDL gains a second copy-from file (the V1 source stays untouched;
  kernel tests apply both to the test-only `core` schema).

## Capabilities

### Modified Capabilities

- `exam-workflow-kernel` — the Exam-Order Aggregate requirement gains the `booking_time`
  document field (existing scenarios preserved); a new Pre-Registration Support requirement
  covers derived entry status, slot config, capacity validation, and refusal semantics.

### New Capabilities

- None (the capability lives inside the existing `exam-workflow-kernel`; splitting a
  scheduling capability out now would fragment the state-machine ownership for one field
  plus one validation service — revisit if the v2 号池 (slot entities/reservation) ever
  lands).

## Impact

- `lumen-sev/lumen-core` — `ExamOrder` entity (+`bookingTime`), new `ExamSlotConfig`
  entity + mapper, new pre-registration policy/capacity service (+impl), kernel source DDL
  `db/kernel/V2__exam_kernel_pre_registration.sql`, kernel tests (test-only `core` schema
  re-runs both sources).
- Consumers (future proposals, NOT this change): `lumen-us` vendors the DDL and extends the
  create contract with `bookingTime` + capacity refusal surfacing (us-station-registration
  extension, proposal ②); `lumen-radiology`/`lumen-endo` reuse by vendoring when their
  workstations are built. The web workstation pages belong to proposal ③.
- No wire-format change to existing endpoints; no existing scenario is broken — the delta
  is additive on the aggregate requirement and a new requirement.

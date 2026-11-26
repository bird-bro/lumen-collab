# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: us-slot-options-window-fix
- **Problem to solve**:
The 2026-11-25 code review surfaced four defects in
`RegistrationOrderServiceImpl.slotOptions` (and one doc debt family):
1. **Infinite loop** — `cursor.plusMinutes(granularity).isAfter(windowClose)`
   never advances when `LocalTime.plusMinutes` wraps (granularityMinutes=1440,
   or 720 with an 08:00–20:00 window): the servlet thread pins and one
   unbounded `selectCount` runs per iteration.
2. **Grid misalignment** — the picker enumerates from the raw window start,
   but the create path floors booking times onto the MIDNIGHT granularity grid

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: rewrite `slotOptions` (day-minute termination, midnight-grid
  selection mirroring the create's floor, STRICT parse with the create's LAZY
  dead-end mirror — offer valid windows in order, stop at the first malformed
  one — `selectDbNow` future-only filter, one absolute-minute span load with
  cross-midnight tails, a sorted/deduped candidate grid + empty-answer-before-
  any-query) + the stale BOOKED-entry javadoc sync (the D5 token promotion was
  REVERTED in review round 2 — dead API once the dead-end removed the picker's
  refusal site); TDD in
  `UsRegistrationApiTests` (original 3 reds + 4 review-correction reds +
  existing occupancy guard). Rewind note: the 2026-11-25 draft's eager
  fail-closed mirrored a semantics the kernel does not have — corrected
  2026-11-26 (spec/design/proposal updated, phase rewound per 2c).
- **Out of scope**: kernel edits of any kind (`resolveSlotWindow`/
  `requireFutureBooking`/the impl constants stay the fixed reference — the
  token promotion was reverted); SlotConfigRequest input validation;
  SlotOptionsView shape; web code; migrations/error-code additions (reuses
  A0400).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/us-station/spec.md`
3:### Requirement: Booking Slot Options Read
23:#### Scenario: The configured grid carries live occupancy
31:#### Scenario: Unconfigured modality fails soft
36:#### Scenario: Invalid date refuses
41:#### Scenario: Wrap-around granularity cannot pin the enumeration
48:#### Scenario: Off-grid windows offer exactly the bookable grid points
56:#### Scenario: Malformed windows fail closed like the create


## Design Constraints

- **Architecture/Decisions**:
- **D1 — day-minute integers everywhere**: window spans and grid enumeration
  compute in `int` day-minutes (`startMinutes`, `endMinutes`, `t += g`);
  `LocalTime` appears only for formatting. Termination is structural: the loop
  runs while `t < endMinutes`, incrementing by `g > 0` — no wrap path.
- **D2 — midnight-grid selection (the create mirror)**: first grid point
  `t0 = ceil(startMinutes / g) * g`; offered slots are `t0, t0+g, … < endMinutes`.
  Each slot's occupancy cell is `[t, t+g)` — identical to the create's floored
  window, so picker counts and create acceptance agree. An off-grid window
  (e.g. 08:00 @ 45) yields 08:15…11:15; a window with no grid point inside
  yields zero slots.
- **D3 — strict parse + fail-closed mirror**: reuse the create's parse contract
  (trimmed `HH:mm`; blank/null → malformed). ANY malformed window in the list
- **Technical constraints**: JDK 11 (`export JAVA_HOME=.../jdk-11.jdk/Contents/Home`);
  reactor builds from `lumen-sev/` (`./mvnw -pl lumen-us -am test -q`); shared
  test DB needs an exclusive window (parallel-suite fixture races documented in
  the birthdate verify notes); strict HH:mm parse contract copied from the
  kernel's `parseHhMm` (trimmed, blank→malformed) — no second parser dialect.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 Red: `UsRegistrationApiTests` — three new scenarios: (a) granularityMinutes=1440
10:- [ ] 1.2 Green: rewrite `slotOptions` per design D1–D4 (day-minute enumeration,
16:- [ ] 2.1 `RegistrationCreateRequest` class doc + `registerPreRegistration` doc + the
19:- [ ] 2.2 `ExamSlotCapacityService` + `ExamSlotCapacityServiceImpl` javadoc: same sync.
23:- [ ] 3.1 `./mvnw -pl lumen-us -am test -q` (JDK 11) — full lumen-us suite green (logs/).

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: original three reds (wrap 1.1a /
  off-grid 1.1b / strict-parse divergence 1.1c — the last now asserts the
  DEAD-END offer, not a refusal) + the review-correction reds (1.4:
  [valid→malformed] dead-end offer, [malformed-first] empty answer, past-date
  empty, g=50 cross-midnight tail booked=3) before their green steps.
- **Required edge cases**: granularity 1440/720 (wrap); window with no grid
  point inside → zero slots, still terminates; seconds-form window AFTER a
  valid one (earlier slots stay offered) vs FIRST (configured=true, empty);
  end<=start window; past calendar date → nothing offered; tomorrow's 00:02
  bookings counting into today's 23:20 cell at g=50; unconfigured modality
  stays fail-soft; invalid date stays A0400.
- **Regression-sensitive areas**: the existing occupancy scenario (09:17 →
  09:00–09:30 slot, 30-min grid) must stay green; fail-soft + invalid-date
  scenarios verbatim; slot option JSON shape (start/end/booked/capacity);
  kernel wording — the interface constant is the token's ONE home (the impl
  reads the inherited constant; the us dead-end read never re-states it).

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 5 tasks, one module (lumen-us + doc-only lumen-core),
  no cross-module behavioral deps, no risk indicators beyond the documented
  test-window hazard — same mode as the recent us-* precedents.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: red→green ordering (1.1 before 1.2, 1.4 before
  1.5, sort/dedup red before its green); the create's occupancy semantics stay
  byte-identical (kernel untouched).
- **Blocking categories**: any behavior change to `resolveSlotWindow` /
  capacity validation; a second divergent HH:mm parser dialect; response-shape
  drift; an eager whole-modality refusal returning (the lazy mirror is the
  corrected contract); dead public API on the kernel interface.

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

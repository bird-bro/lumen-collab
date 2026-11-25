# Tasks: us-booking-slot-picker-sev (retrospective — all landed, evidence per task)

## 1. Endpoint

- [x] 1.1 `SlotOptionsView` DTO (configured + slots: start/end/booked/capacity) — evidence: dto/SlotOptionsView.java; compile green
- [x] 1.2 `GET /orders/slot-options` on RegistrationController gated to `us:order:create` ∪ `setting:slot:list` — evidence: RegistrationController.java:97-104; perm-gate tests green
- [x] 1.3 The service read: strict date parse (A0400 on garbage), live-config read (no FOR UPDATE), window flooring onto the granularity grid, per-slot not-cancelled occupancy count (first-item modality scope), fail-soft unconfigured answer — evidence: RegistrationOrderServiceImpl.java:241-285; suite green

## 2. Tests

- [x] 2.1 Grid + occupancy test (8 slots, booked=1 @ 09:00, capacity 2, untouched 0; soft-answer for the unconfigured modality) — evidence: slotOptionsExposeTheConfiguredGridWithLiveOccupancy green
- [x] 2.2 The occupancy predicate stays glued to the create path's (extended by us-prereg-as-registered: REGISTERED occupies / cancelled frees) — evidence: registeredPreRegistrationOccupiesTheSlotAndCancelledFreesIt green

## 3. Verification hooks

- [x] 3.1 L1 compile + L3 us suite green — evidence: verify.md (75/0/0)
- [x] 3.2 `openspec validate us-booking-slot-picker-sev` passes — evidence: validate run 2026-11-25 (one pre-existing prose-length warning)

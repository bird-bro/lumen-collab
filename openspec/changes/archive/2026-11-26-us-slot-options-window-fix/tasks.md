# Tasks: us-slot-options-window-fix

## 1. Slot options rewrite (TDD)

- [x] 1.1 Red: `UsRegistrationApiTests` — three new scenarios: (a) granularityMinutes=1440
      answers finite/empty slots within latency (no hang); (b) 08:00–12:00 @ 45 offers
      exactly 08:15/09:00/09:45/10:30/11:15 and a booked pre-registration counts into its
      own cell; (c) a seconds-form window (`08:00:00`) refuses A0400 (fail-closed mirror).
      Existing occupancy scenario stays the regression guard. Run → fail.
- [x] 1.2 Green: rewrite `slotOptions` per design D1–D4 (day-minute enumeration,
      midnight-grid selection, strict fail-closed parse, single span load with in-memory
      bucketing). Run → pass.

- [x] 1.3 Rewind (2026-11-25 review, 4 findings): spec/design corrected — the
      create validates windows LAZILY (dead-end, not eager fail-closed), the
      future-only guard and cross-midnight tails are part of the mirror, and
      the D5 token promotes onto the kernel interface.
- [x] 1.4 Red (review corrections): [valid→malformed] offers the valid window's
      grid points only; [malformed-first] answers configured=true + empty; a
      past date offers nothing; g=50 cross-midnight tail counts next-day
      bookings into the 23:20 cell. Run → fail against the 1.2 code.
- [x] 1.5 Green (review corrections): dead-end enumeration + `selectDbNow`
      future filter + absolute-minute bucketing with tail-extended span +
      `ExamSlotCapacityService.MSG_INVALID_WINDOWS` promotion (impl dedup).
      Run → pass.
- [x] 1.6 Round-2 review (2026-11-26, 3 findings): sorted/deduped candidate grid
      via TreeSet (out-of-order/overlapping stored windows — red 24≠20 confirmed),
      empty-answer-before-any-query (the dbNow probe skips unbookable configs),
      and the D5 token promotion REVERTED (dead public API once the dead-end
      removed the picker's refusal site — kernel restored byte-identical).
      Full suite green (146/146, logs/test-backend.log).

## 2. Javadoc sync (doc-only)

- [x] 2.1 `RegistrationCreateRequest` class doc + `registerPreRegistration` doc + the
      walk-in comment in `RegistrationOrderServiceImpl`: retired BOOKED-entry wording →
      REGISTERED/PRE_REGISTERED stamp + live not-cancelled occupancy.
- [x] 2.2 `ExamSlotCapacityService` + `ExamSlotCapacityServiceImpl` javadoc: same sync.

## 3. Verify

- [x] 3.1 `./mvnw -pl lumen-us -am test -q` (JDK 11) — full lumen-us suite green (logs/).

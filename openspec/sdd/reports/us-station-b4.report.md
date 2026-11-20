# us-station-registration-sev — Batch 4 report (task 4.1 + two B3-review riders)

Date: 2026-11-20 · Author: implementer agent (B4) · Scope: `lumen-us` only (+ change-local
tasks.md); kernel / lumen-patient / lumen-api / framework / common diffs all EMPTY (git-verified).

## 4.1 Snapshot poller — `org.birdbro.lumen.us.poller.PatientSnapshotPoller` (new)

- Scheduling: `@Scheduled(fixedDelayString = "${lumen.us.snapshot-poll-delay-ms:10000}")` on a thin
  `scheduledPoll()` wrapper; the testable unit is public `pollOnce()`. `@EnableScheduling` added to
  `UsApplication` (the ONLY `@Scheduled` bean in this context — framework/common carry none). Knobs
  documented in `application.yml` (`lumen.us.snapshot-poll-delay-ms` / `-limit`, inline defaults kept).
- Batch: pull limit 50 (configured limit clamps back to 50 when non-positive — unit-proven).
- `PATIENT_CHANGED`: payload `id` = pat_uid (the patient writer emits `BeanUtil.beanToMap` of the
  updated archive) → live set (`deleted=0` via `@TableLogic` + status NOT IN BOOKING_CANCELLED/
  CANCELLED) → kernel `refreshPatientSnapshot` each → ack.
- `PATIENT_MERGED`: payload `survivorId`/`mergedId` → live set of the OLD uid fetched → us-side
  mapper UPDATE repoint (predicate re-evaluated atomically in the UPDATE's WHERE; cancelled orders
  keep the old uid) → refresh the same set → ack.
- Snapshot overlay discipline (documented in javadoc): refresh target = the order's CURRENT snapshot
  + the event's delivered archive fields (patId/name/gender/phone — exactly the adapter's four),
  non-null overlay; exam-time demographics (age/patientSource/admissionNumber/bedNumber/height/
  weight) NEVER touched (patient decision #4); a null archive field never erases a request-sourced
  snapshot value; no stored snapshot + nothing delivered → no kernel call.

### Failure taxonomy (all proven by tests)

1. Pull/ack transport failure (fail-fast C0001 fallback included): failed pull skips the WHOLE batch
   (no acks, replay next pass); failed ack leaves the row PENDING — the dispatch stands (refresh is
   an idempotent overwrite). Transport doubt never marks consumed.
2. Per-entry dispatch failure (unparseable payload / missing uid / unknown event type): per-entry
   try/catch — batch continues, poisoned row gets NO ack and stays PENDING (the requested poison
   semantics). Unknown event types deliberately do NOT ack (never consume on doubt).
3. Kernel ISSUED-guard refusal (`BusinessException` from `refreshPatientSnapshot` ONLY — structurally
   scoped): deterministic, delivered-but-not-applicable → the entry still ACKS (DOCUMENTED choice per
   the brief's hint; re-pulling cannot change the report state; kernel delegates the follow-up to the
   consumer). Tested: ISSUED report → jsonb byte-identical + acked.

### NEEDS_CONTEXT item — PATIENT_MERGED payload carries no snapshot fields

The patient outbox writer (`PatientArchiveServiceImpl.merge`) emits only
`{"survivorId":..,"mergedId":..}` — NO archive fields — so the D5 assumption "payload → PatientSnapshot
mapping possible" holds for PATIENT_CHANGED (all four snapshot archive fields present) but NOT for
PATIENT_MERGED. Implemented per brief (no silent widening): the merged branch repoints (the real
delivery) and runs the refresh through the UNIFORM overlay path — today the overlay is empty, so the
kernel write is content-neutral; the day the patient side widens the payload (suggested one-line-ish
follow-up: `payload.put("survivorArchive", BeanUtil.beanToMap(survivor))` — the payload is
contract-opaque jsonb, NO lumen-api/schema change), the same us code applies the survivor's archive
with ZERO us-side change. Flagging for the patient-domain owner's decision; not done from here.

## B3-review riders (fixed in passing, per coordinator brief)

- **Minor-1 (real bug, RED→GREEN)**: `RegistrationCreateRequest` binds ext 1:1 to `UsRegistrationExt`;
  the stashed ext is now stripped of `deleted/createBy/updateBy/createTime/updateTime` in
  `RegistrationOrderServiceImpl.create` before the hook persists. RED evidence: client-posted
  `createBy=999` persisted verbatim (`expected: <9364> but was: <999>`); GREEN: `deleted=0`,
  `create_by` = meta-fill acting user, `update_by` NULL on INSERT (house `MetaObjectConfig.insertFill`
  fills only createBy — updateBy fills on UPDATE, platform-wide behavior, test pins it), timestamps
  DB-defaulted.
- **Minor-3 (premise FALSE — readability only)**: the sweep SQL in `UsRegistrationFixtures` already
  read `WHERE deleted = 0 AND ( (band) OR name-marker OR accession-marker )` — the parenthesized group
  already exists, so `deleted = 0` ALREADY gated the whole row-match; no AND-precedence bug existed
  (empirically proven: the new semantics test PASSED against the unfixed code — RED unreachable).
  Applied the explicit inner parenthesization `(patient_uid >= ? AND patient_uid < ?)` as a
  readability change so the misreading cannot recur; the new `UsRegistrationSweepTests` pins the
  semantics (live marker row swept; soft-deleted marker row survives; seeds hard-deleted in finally).

## TDD evidence

- RED (riders): `./mvnw -pl lumen-us -am test -q -Dtest='UsRegistrationApiTests,UsRegistrationSweepTests'`
  exit 1 — rider-1 failure `create_by ... expected: <9364> but was: <999>` + sweep-seed DDL fix
  (accession_no NOT NULL) → clean behavioral RED. Log `/tmp/b4-rider-red.log`.
- GREEN (riders): same selector exit 0 — 12/12 + 1/1. Log `/tmp/b4-rider-green2.log`.
- RED (poller): compile error (missing `PatientSnapshotPoller`), then envelope-shape fix
  (`R<List<...>>` unwrap). Log `/tmp/b4-us-full.log`.
- GREEN (full module): `./mvnw -pl lumen-us -am test -q` exit 0 — **49 tests, 0 failures, 0 errors**:
  UsAdapterTests 12, UsContextBootTests 2, UsMenuSeedTests 5, UsRegistrationApiTests 12, UsSweep 1,
  UsRegistrationExtTests 4, UsSchemaMigrationTests 5, **UsSnapshotPollerTests 8** (CHANGED refresh+
  ack with exam-time preservation; MERGED repoint + cancelled-uid-kept + ack; poisoned-entry
  isolation; unknown-type no-ack; pull-transport no-ack; ack-transport replay-safety; ISSUED-guard
  acks; limit clamping). Log `/tmp/b4-us-full2.log`.
- L1: `./mvnw compile -q` exit 0, zero errors. Log `/tmp/b4-l1-compile.log`.

## Notes

- Poller tests seed entries as crafted raw-jsonb payload strings through the Feign seam stubs (the
  D8 pattern — no patient service runs in the us test JVM; the pull/ack MECHANISM is proven
  patient-side by the task-2.2 suites); the kernel side is fully real (orders through the real HTTP
  surface, refresh through the real kernel aggregate service, repoint through the real mapper).
- Test-context scheduling: `UsIntegrationTestBase` pins `lumen.us.snapshot-poll-delay-ms=3600000`
  (the suites drive `pollOnce()` directly; the scheduler can never race a stubbed batch).
- Shared test server behaved (no interference observed; all runs single-shot clean).
- Batch 5 (verify) remains open; loop-state.yaml slot untouched (held by the parallel
  audit-log-unify-api session — race discipline).

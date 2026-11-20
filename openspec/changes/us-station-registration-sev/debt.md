# Debt

- [DEBT] MERGED-payload snapshot staleness (B4 review ruling 2026-11-20, option (a)): the patient
  MERGED outbox payload carries only `{survivorId, mergedId}` — no archive fields — so post-merge
  live orders keep the ABSORBED patient's jsonb identity (display + the GET /orders name filter go
  stale for the survivor until a later PATIENT_CHANGED; a merge emits none; linkage/repoint is
  correct, staleness is display/filter only, self-heals on any later archive edit). Follow-up for
  the patient-domain owner: widen the MERGED payload additively — **flatten `putAll` of the
  survivor archive into the payload top level = ZERO us-side change** (the poller reads only
  top-level patId/name/gender/phone, `PatientSnapshotPoller.archiveDelivery`); a NESTED
  `survivorArchive` map would need a small us parse addition — pick the flatten shape. Risk: low
  (display staleness only, merges exceptional in v1).
- [DEBT] Unknown outbox event types stay PENDING forever (re-pulled every pass, cost a batch slot +
  a warn; no DLQ). This is the ruled never-consume-on-doubt semantics; consequence recorded, no
  action v1. Risk: low.

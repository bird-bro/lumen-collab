# Tasks: us-apply-info-sev (retrospective — all landed, evidence per task)

> The implementation landed before this closure file was written (coordination
> parent `openspec/coordination/us-apply-info.md`); verify.md PASS 2026-11-25 is the
> credential. Tasks below record what shipped, retroactively checked.

## 1. Migration

- [x] 1.1 Add `V5__us_apply_info.sql` (nullable ADD COLUMN IF NOT EXISTS: apply_dept / apply_doctor / apply_time varchar trio + the reserved apply_dept_id / apply_doctor_id anchors, no-semicolon comments) and verify the us migration tests apply it — evidence: full us suite green 2026-11-25
- [x] 1.2 Extend `UsSchemaMigrationTests`' column-set pin with the origin trio + reserved ids appended after the audit tail — evidence: UsSchemaMigrationTests green in the same run

## 2. Entity + path

- [x] 2.1 `UsRegistrationExt` gains applyDept/applyDoctor/applyTime/applyDeptId/applyDoctorId (the ext javadoc notes the US-only rule); verify compile — evidence: L1 exit 0
- [x] 2.2 The create path strips client-posted audit columns from the ext before the hook persists it (server-owned stamps) — evidence: extAuditFieldsAreServerOwnedNeverClientBindable green

## 3. View + tests

- [x] 3.1 `RegistrationOrderView` carries the apply trio + anchors; `toView` flattens them; `UsRegistrationApiTests` extRoundTrip asserts the six fields end-to-end — evidence: extRoundTripsThroughThePageQuery green (later extended by us-prereg-as-registered in the same file, also green)
- [x] 3.2 Full us suite green — evidence: `./mvnw test` reactor exit 0 (verify.md L3)

## 4. Verification hooks

- [x] 4.1 `openspec validate us-apply-info-sev` passes — evidence: verify.md / the synced delta (MODIFIED US Registration Extension)

# Coordination: us-station-registration

## Decision

- US workstation registration surface = route §5.3 step 3's first half: the archived kernel
  registration domain (`2026-11-19-lumen-core-registration-kernel`) gets its FIRST real host.
- New module `lumen-us` (`org.birdbro.lumen.us`, `UsApplication`, **port 8083** — next port-table
  row), own `us` schema + Flyway ledger, nginx `/api/v1/us/**`.
- Kernel DDL adoption follows D2: us ledger **V1 vendor-copies** `db/kernel/V1__exam_kernel_registration.sql`
  verbatim; department fields stay OUT of the vendored copy.
- US extension fields (user ruling 2026-11-20: 进这刀) live in a us-side 1:1 extension table
  `us_registration_ext` (us ledger V2) reached through the kernel's `RegistrationHook` seam (D8) —
  minimal set: pregnancy status + gestational weeks + note (EIS 对照: US 登记无 EIS 特有字段, 孕检为
  lumen 决策 #5).
- `PatientRegistryPort` adapter = Feign → **patient internal endpoints** (internal-key guarded,
  `InternalKeyInterceptor` precedent from system's `/issue`); `lumen-api` gains the `PatientApi`
  contract (add-only; NumberApi precedent). patient also exposes the **outbox-pull + ack** internal
  endpoints — the `patient.changed` delivery channel is **internal polling, no MQ** (user ruling
  2026-11-20; RocketMQ stays undeployed).
- us runs a scheduled poller: pull PENDING outbox batch → PATIENT_CHANGED refreshes non-cancelled
  orders' snapshots via kernel `refreshPatientSnapshot`; PATIENT_MERGED repoints `exam_order.patient_uid`
  (decision #3 minimal-merge hygiene) then refreshes; ack marks rows consumed (failure keeps PENDING).
- HIS two-phase intake: kernel service stays library-tested; its HTTP surface + worklist UI DEFER to
  the `lumen-integration` HL7v2 change (no producer today — cache would stay permanently empty).
- Registration UI: patient resolution chain flow (id_no → card_no → soft-match candidates → 建档,
  patient decision #2) + order form (检查项目 from the setting catalog, state actions
  预约/登记/取消) + order list. `PatientDemographicsForm` is a SHARED component (decision #5) for
  radiology/endo reuse. Page perms ride the `us:order:*` family; patient data ops are proxied
  server-side through the adapter (no patient:* perms client-side).
- Menu: us ledger V3 seeds the 登记 C page + F buttons under the existing US W node cross-schema
  into `system.menu_info`/`role_menu` (patient V2 precedent); component key `us/registration/index`.
- Deployment order: sev first → user verifies real APIs → web builds against them → verify each →
  batch archive (coordination gate).

## Change registry

| Stack | Change |
|:--|:--|
| Backend (lumen-us + patient internal + lumen-api) | `us-station-registration-sev` |
| Frontend (US workstation registration UI) | `us-station-registration-web` |

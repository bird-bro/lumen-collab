# Design: us-station-registration-sev

## Decisions

- **D1 — Module shape**: `lumen-sev/lumen-us`, `org.birdbro.lumen.us.UsApplication` (service-root
  app class, OPS single-domain shape), port **8083** (port-table next row), scan `org.birdbro` +
  `@MapperScan org.birdbro.**.mapper` (kernel mappers register against the us schema), aggregator
  pom + one row in `init-project-skeleton.md`; Flyway `schemas: us`, `default-schema: us`.
  AMENDED (B1 review 2026-11-20, ACCEPT): nginx location + docker compose wiring DEFERRED —
  `docker/` is README-only and the api spec explicitly defers deployment config to the future
  docker change; the patient precedent registers only the port row. The "unified nginx entry"
  scenario is a runtime/deployment concern, not a code artifact of this change.
- **D2 — Ledger**: V1 vendor-copies the kernel registration DDL verbatim (D2 contract; statement
  comments carry no semicolons — V36 lesson); V2 `us_registration_ext` (order_id PK/FK 1:1,
  pregnancy_status varchar(16), gestational_weeks integer, note varchar(255), EntityBase audit set);
  V3 cross-schema menu seed (patient V2 pattern: system.menu_info + role_menu, CONTENT-guard,
  setval tail). Schema migration tests assert tables/columns per ledger step.
- **D3 — Adapters** (both in `org.birdbro.lumen.us.adapter`): `PatientRegistryPortAdapter` →
  `PatientApi` Feign (lumen-feign, direct url `lumen.feign.patient.url`, NumberApi precedent);
  `NumberIssuerPortAdapter` → `NumberApi` (mirror patient's adapter). Internal key propagates via
  the existing header mechanism (patient `FeignInternalKeyConfig` precedent, copied per-service —
  additive-first; framework promotion is a future proposal).
- **D4 — patient internal surface**: `InternalPatientController` (`/api/v1/patient/internal/**`)
  behind a patient-side `InternalKeyInterceptor` config (copy of system's): `POST
  /patients/resolve-or-register` composes the EXISTING `PatientArchiveService` resolve + create in
  one transaction (no chain rewrite; `A0715` unchanged); `GET /events/outbox?limit=` + `POST
  /events/outbox/{id}/ack` over `patient_event_outbox` (status PENDING→CONSUMED, rows kept for
  audit). Contract DTOs live in `lumen-feign/patient` (add-only).
- **D5 — Snapshot poller** (`us/bootstrap` or `adapter`): `@Scheduled(fixedDelay)` batch pull
  (limit 50), per-entry try/catch, ack only after successful dispatch. `PATIENT_CHANGED` → non-cancelled
  orders (`status NOT IN ('BOOKING_CANCELLED','CANCELLED')` and `deleted=0`) of the payload uid →
  kernel `refreshPatientSnapshot(orderId, snapshot)` each. `PATIENT_MERGED` → repoint
  `exam_order.patient_uid` old→new for the same live set (us-side mapper update — historical
  cancelled orders keep the old uid) → refresh → ack. Replay-safe: refresh is an overwrite, repoint
  is idempotent.
- **D6 — Registration HTTP surface**: `RegistrationController` thin over kernel services —
  `POST /api/v1/us/patients/resolve` (lookup-only passthrough, gated `us:order:create`),
  `POST /api/v1/us/orders` (identity-or-demographics body → adapter →
  `ExamOrderRegistrationService`; ext fields ride the hook), `GET /api/v1/us/orders` (page query),
  `POST /api/v1/us/orders/{id}/register` / `POST /api/v1/us/orders/{id}/cancel` (reason body).
  Perms `us:order:{list,create,update,cancel}` via the framework security chain; errors ride the
  house `GlobalExceptionHandler` codes.
- **D7 — Extension hook**: the us `RegistrationHook` implementation persists/loads
  `us_registration_ext` around the aggregate write (interface shape read at apply — kernel D8
  seam; the create body carries the optional ext block).
- **D8 — Tests**: shared test server `us` schema via gitignored `.env-test.local` (patient
  pattern); adapter tests stub `PatientApi`/`NumberApi` at the Feign seam (`@MockBean` base
  precedent, feign-consumer-test-seam-stub); poller tests seed outbox rows + assert refresh/repoint/ack;
  controller tests hit the real API through the test-server bootstrap (patient `PatientApiTestBase`
  pattern).

## Risks / Trade-offs

- Polling latency (fixedDelay seconds) is acceptable for snapshot freshness v1; MQ migration later
  keeps the outbox table unchanged (publisher swap only).
- The composite resolve-or-register widens patient's surface minimally — one endpoint, internal-key
  only, zero change to public chain semantics.
- repoint-on-merge as a us-side mapper update bypasses the aggregate service; acceptable v1
  hygiene (decision #3), revisit with the report domain if audit needs order-side merge logs.

## ADDED Requirements

### Requirement: US Service Host

The US workstation SHALL be served by a new executable module `lumen-us` (`org.birdbro.lumen.us`,
app class `UsApplication`, port 8083 per the coordination port table, own `us` schema and Flyway
ledger) behind the unified `/api/v1/us/**` entry. The module SHALL adopt the kernel registration
DDL by vendoring the canonical statements verbatim into its own ledger (D2 contract) and SHALL
modify the kernel library in no way.

#### Scenario: Endpoints are reachable under the unified entry

- **WHEN** a client calls any US-station endpoint
- **THEN** the path is `/api/v1/us/**` behind the unified nginx entry and the kernel tables
  resolve in the `us` schema through the module's own ledger

#### Scenario: Kernel DDL is vendored verbatim

- **WHEN** the us ledger applies its registration migration
- **THEN** the statements match the canonical kernel DDL (`exam_order` / `exam_execution` /
  `exam_report` / `exam_apply_cache`, audit column set intact, no department columns added)

### Requirement: Registration Surface

The module SHALL expose the registration lifecycle over `/api/v1/us/orders/**`: create a booked
order from a patient-registry identity + 检查项目 (setting-catalog references snapshotted into
`exam_items`), page-query orders, transition 登记, and cancel with a reason. Creation SHALL issue
`accession_no` through the `NumberIssuerPort` (the kernel resolves the accession rule token from
the 编号类型 family — the adapter never picks a rule) inside the creation transaction,
and the HTTP surface SHALL gate every action behind the `us:order:*` permission family.

#### Scenario: Create a booked order

- **WHEN** an authorized client posts a create body with a resolved patient identity, 检查项目
  references, and optional 急诊 flag
- **THEN** the order persists with status `BOOKED`, an `accession_no` issued by the platform
  number rule, the patient snapshot stored, and the id returned

#### Scenario: Illegal transition is refused verbatim

- **WHEN** a client attempts a state jump the kernel state machine forbids (e.g. 登记 on a
  cancelled order)
- **THEN** the kernel refusal surfaces verbatim and the order state is unchanged

#### Scenario: Cancel carries a reason

- **WHEN** an authorized client cancels a live order with a reason
- **THEN** the order lands in the matching cancel state (`BOOKING_CANCELLED` from BOOKED,
  `CANCELLED` otherwise) with the reason stored

### Requirement: Patient Registry Adapter

The module SHALL adapt the kernel `PatientRegistryPort` with a Feign adapter over the patient
service's internal `resolve-or-register` endpoint (internal-key guarded), following the `NumberApi`
contract precedent; the adapter SHALL refuse incomplete port results (missing `patUid`) before any
order row is written.

#### Scenario: Registration consumes the port result

- **WHEN** the create path resolves a patient through the adapter
- **THEN** the order links the returned `patUid` and snapshots the delivered archive fields

#### Scenario: Incomplete port result refused

- **WHEN** the adapter receives a result without `patUid`
- **THEN** order creation is refused and nothing persists

#### Scenario: US-side resolve passthrough for the registration UI

- **WHEN** the registration UI looks up a patient by `id_no` / `card_no` demographics through
  `POST /api/v1/us/patients/resolve` (gated `us:order:create`)
- **THEN** the exact-match identity, the soft-match candidate list, or an empty result returns
  with no side effect — order creation itself posts the identity-or-demographics and resolves
  server-side through the adapter

### Requirement: Snapshot Refresh Wiring

The module SHALL run a scheduled poller over the patient outbox pull/ack internal endpoints:
`PATIENT_CHANGED` refreshes the snapshot of every non-cancelled order of the patient through the
kernel `refreshPatientSnapshot`; `PATIENT_MERGED` repoints `exam_order.patient_uid` to the
surviving archive before refreshing; rows are acked only after successful dispatch, and a failed
dispatch keeps the row PENDING for the next pass (idempotent replay).

#### Scenario: Patient change refreshes open orders

- **WHEN** a `PATIENT_CHANGED` entry is polled for a patient holding live orders
- **THEN** each non-cancelled order's jsonb snapshot is refreshed with the delivered archive and
  the entry is acked

#### Scenario: Merge repoints live orders

- **WHEN** a `PATIENT_MERGED` entry is polled
- **THEN** the merged-away uid's live orders repoint to the surviving uid before the snapshot
  refresh, and the entry is acked only after both steps succeed

### Requirement: US Registration Extension

The module SHALL carry the US-specific registration fields in a us-side 1:1 extension table
`us_registration_ext` (pregnancy status, gestational weeks, note) — the vendored kernel DDL stays
verbatim — read and written through the kernel `RegistrationHook` seam so the create/query path
carries the extension without kernel changes.

#### Scenario: Extension persists with the order

- **WHEN** an order is created with US extension fields
- **THEN** the hook persists them 1:1 against the order id and the query path returns them

### Requirement: Menu and Perms Seed

The us ledger SHALL seed, cross-schema (patient V2 precedent), a 登记 C page under the existing US
workstation W node (component `us/registration/index`, perm `us:order:list`) with F button nodes
新建/登记/取消 (`us:order:create/update/cancel`) in the CONTENT-guard discipline, with the admin
role auto-granted.

#### Scenario: Registration page joins the US workstation tree

- **WHEN** the menu seed migration completes
- **THEN** the US W node carries the 登记 C page with its component key and the F button family,
  and the admin role's menu set includes every seeded node

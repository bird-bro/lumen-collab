# us-station Specification

## Purpose

Defines the US workstation host - the first department workstation: module identity and
the vendored kernel registration ledger, the registration lifecycle served over
`/api/v1/us/**` (accession issuance through the platform number rules), the patient-registry
adapter over the patient domain's internal-key surface, the scheduled snapshot-refresh wiring
over the patient outbox, the US-specific registration extension, and the menu/perms seed under
the US workstation W node. The exam kernel library stays untouched - the host only adapts and
wires it.

## Requirements

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

<!-- synced from: us-registration-redesign-api 2026-11-24 -->

### Requirement: Registration Surface

The module SHALL expose the registration lifecycle over `/api/v1/us/orders/**`: create an
order from a patient-registry identity + 检查项目 (setting-catalog references snapshotted into
`exam_items`), page-query orders, and cancel with a reason. Creation SHALL
enter `REGISTERED` for every path in ONE transaction (拍板 02-B 提交即登记; 预约=预登记, user
ruling 2026-11-24): a null/absent `bookingTime` is a 现场单 stamped `registration_type`
`WALK_IN`; a strictly-future one is a 预登记 stamped `PRE_REGISTERED` that enters `REGISTERED`
as well, only after the kernel pre-registration validation passes — the composed flow runs the
capacity dance and the aggregate registration inside ONE transaction while the patient-registry
port resolve stays OUTSIDE it (kernel design D8). The `registration_type` stamp is SERVER-OWNED
(never read from the client body). The kernel A0400 refusals surface VERBATIM with nothing
persisted. Creation SHALL issue `accession_no` through the `NumberIssuerPort` (the kernel
resolves the accession rule token from the 编号类型 family — the adapter never picks a rule)
inside the creation transaction, and the HTTP surface SHALL gate every action behind the
`us:order:*` permission family. Each picked 项目 reference SHALL carry that item's modality
token from the setting catalog (the dict-1024 value), so the kernel issues the accession under
the modality-scoped rule when one exists and the family global rule otherwise. The create SHALL
store the body's `cardNo` into the patient snapshot (the additive jsonb key — the card-no
workbench query condition reads it). The former 登记 transition endpoint is REMOVED — the
machine no longer defines the `BOOKED` arrival edge (user ruling 2026-11-26, six-state model;
the page already dropped the 登记 action, us-prereg-as-registered-web). The page view rows
SHALL carry `createTime` / `createBy` (the 登记时间/登记人 stamps — the create stamps serve
BOTH kinds; the earlier `registered_time`/`registered_by` idea is retracted) and
`registrationType` alongside `bookingTime`.

#### Scenario: Create a booked order

<!-- scenario superseded in place by the 2026-11-24 ruling 预约=预登记: the retired
shape (future → BOOKED) survives only in this comment for history -->

- **WHEN** an authorized client posts a create body with a resolved patient identity, 检查项目
  references each carrying the item's modality token, and NO `bookingTime`
- **THEN** the order persists with status `REGISTERED` and `registration_type` `WALK_IN`
  (提交即登记 — the 拍板 02-B derived entry), an `accession_no` issued by the platform number
  rule resolved by that modality scope (or the family global rule when no scoped rule exists),
  the patient snapshot stored with the body's `cardNo`, and the id returned
- **WHEN** the same create carries a strictly-future `bookingTime`
- **THEN** the order persists with status `REGISTERED` (预登记=已登记) and `registration_type`
  `PRE_REGISTERED` with the requested `booking_time` as its slot anchor — the capacity
  validation having passed inside the same transaction

#### Scenario: Invalid pre-registration is refused verbatim

- **WHEN** a create carries a `bookingTime` that is in the past (预登记时间不能早于当前时间),
  targets a modality with no live slot config (该检查类型未开通预登记), falls outside the
  modality's open windows (预登记时间不在开台时间窗内), or lands on a slot at capacity
  (该时段已满)
- **THEN** the kernel A0400 refusal surfaces verbatim with its Chinese message and nothing
  persists

#### Scenario: Below-capacity pre-registration occupies the slot

- **WHEN** a pre-registration create passes the capacity validation against the us schema's
  own live not-cancelled count in the resolved slot window
- **THEN** the order persists as `REGISTERED` with the requested `booking_time` and the
  window's live count increases by one

#### Scenario: The create result carries the derived state

<!-- scenario superseded in place: registrationType + the operator stamps join the
response/view under the same scenario name -->

- **WHEN** any create succeeds
- **THEN** the response carries the derived `status`, the echoed `bookingTime` (null for
  walk-ins), and the `registrationType`, and the page view rows carry `bookingTime`,
  `registrationType`, and the operator stamps `createTime`/`createBy` (登记时间/登记人 = the
  create stamps for both kinds)

#### Scenario: Illegal transition is refused verbatim

- **WHEN** a client attempts a state jump the kernel state machine forbids (e.g. a cancel
  whose report is 已出, or any backward move)
- **THEN** the kernel refusal surfaces verbatim and the order state is unchanged

#### Scenario: Cancel carries a reason

<!-- scenario superseded in place by the 2026-11-26 six-state ruling: the retired
shape (the machine's reserved BOOKING_CANCELLED branch) survives only in this
comment for history -->

- **WHEN** an authorized client cancels a live order with a reason
- **THEN** the order lands in `CANCELLED` with the reason stored — the six-state machine has
  ONE cancel landing (已取消 from 已登记/检查中/诊断中 per the kernel edges); the former
  `BOOKING_CANCELLED` branch is removed

#### Scenario: Modality reaches the issuer port

- **WHEN** the create body's first item reference carries modality `US`
- **THEN** the kernel port receives the 登记号 family together with that modality token and
  the order stores the item references with their modality intact

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
`us_registration_ext` (pregnancy status, gestational weeks, note, apply department, apply
doctor, apply time, and the reserved id anchors apply_dept_id / apply_doctor_id) — the
vendored kernel DDL stays verbatim — read and written through the kernel `RegistrationHook`
seam so the create/query path carries the extension without kernel changes. The apply fields
record the HIS application origin (the department, doctor, and moment that opened the
检查申请单): `apply_dept varchar(64)` / `apply_doctor varchar(64)` / `apply_time varchar(19)`,
all nullable; `apply_time` is a raw-text stamp (informational only — no parse policy, no
state semantics in v1); the id anchors are varchar(64) reservations that stay null until an
HIS feed or a picker surfaces them.

#### Scenario: Extension persists with the order

- **WHEN** an order is created with US extension fields
- **THEN** the hook persists them 1:1 against the order id and the query path returns them

#### Scenario: Apply origin persists and flattens onto the view

- **WHEN** an order is created whose ext block carries applyDept / applyDoctor / applyTime
  (and optionally the reserved applyDeptId / applyDoctorId anchors)
- **THEN** the ext row stores them verbatim and the page query's flattened view returns them
- **AND** an ext block without them stores NULLs and the view returns null

### Requirement: Menu and Perms Seed

The us ledger SHALL seed, cross-schema (patient V2 precedent), a 登记 C page under the existing US
workstation W node (component `us/registration/index`, perm `us:order:list`) with F button nodes
新建/登记/取消 (`us:order:create/update/cancel`) in the CONTENT-guard discipline, with the admin
role auto-granted.

#### Scenario: Registration page joins the US workstation tree

- **WHEN** the menu seed migration completes
- **THEN** the US W node carries the 登记 C page with its component key and the F button family,
  and the admin role's menu set includes every seeded node

<!-- synced from: us-registration-redesign-api 2026-11-24 -->

### Requirement: Workbench Query Surface

The page query SHALL serve the two-state workbench's wide-list state (拍板 03-B, the
RCP ∪ EIS condition union) over `GET /api/v1/us/orders` behind `us:order:list`: status
multi-select (IN semantics; empty = all), accession number fuzzy, patient name fuzzy (jsonb
snapshot), patient-id exact (snapshot `patId`), patient-source exact (snapshot), card-no
exact (snapshot `cardNo`), exam-item name fuzzy over the `exam_items` jsonb array, and the
apply-time range (the order's `create_time` — the kernel carries no separate apply
timestamp). All conditions SHALL compose. A status-count endpoint (`GET
/api/v1/us/orders/status-counts`, same `us:order:list` perm) SHALL return per-status
counts under the SAME filter set minus the status condition (EIS ReportStatusCheckbox
semantics — the chips are the desk workload meter), zero-filled for every document state
machine status.

#### Scenario: Status multi-select composes with the wide conditions

- **WHEN** the query carries multiple statuses together with any of the other conditions
- **THEN** the result rows match the status IN-set AND every other supplied condition

#### Scenario: Snapshot conditions hit the jsonb document

- **WHEN** the query carries patient-id, patient-source, or card-no
- **THEN** the match reads the corresponding `patient_snapshot` jsonb key (`patId` /
  `patientSource` / `cardNo`) — patient-id and card-no exact, patient-source exact

#### Scenario: Exam-item name matches fuzzily across the jsonb array

- **WHEN** the query carries an exam-item name fragment
- **THEN** orders whose `exam_items` jsonb array holds any item whose `itemName` contains the
  fragment match (per-element LIKE, no whole-document false positives)

#### Scenario: Apply-time range bounds the registration moment

- **WHEN** the query carries an apply-time range
- **THEN** the match bounds `create_time` (the registration moment) between the range ends

#### Scenario: Status counts respect the filters

<!-- scenario superseded in place by the 2026-11-26 six-state ruling: the retired
key set (the former six-state enum incl. BOOKED/BOOKING_CANCELLED) survives only in
this comment for history -->

- **WHEN** the status-count endpoint receives the wide filter set WITHOUT the status
  condition
- **THEN** it returns one count per document state machine status (`REGISTERED`, `IN_EXAM`,
  `IN_DIAGNOSIS`, `COMPLETED`, `PRINTED`, `CANCELLED`), zero-filled, each counted
  under the same remaining filters

### Requirement: Slot Config Administration

The us ledger SHALL vendor the kernel V2 pre-registration DDL verbatim as its next version
(the V1 vendoring precedent): `exam_order.booking_time` and `exam_slot_config` land in the
us schema — the config rows are department-owned (① design D3) and their FOR UPDATE row lock
is the pre-registration serialization point (① design D7), which requires the config in the
same schema as the orders. The module SHALL administer the config over
`/api/v1/us/slot-configs` — list, create, update, soft-delete (the ①-fixed DDL shape carries
no enable column; edit + delete only) — behind the `setting:slot:*` permission family (the
console page belongs to the 检查配置 family; proposal ③ renders it). One LIVE row per
modality (the kernel's live-unique index); the modality token stores trimmed verbatim (the
dict-1024 VALUE token the create flow consumes — the admin picks from the dict in the UI;
the API does not re-validate against the catalog).

#### Scenario: V4 vendoring lands both kernel artifacts

- **WHEN** the us service migrates its schema
- **THEN** `exam_order.booking_time` and `exam_slot_config` exist in the us schema with the
  kernel shapes (modality live-unique, granularity default 30, capacity default 4,
  open_windows jsonb default '[]', GIN index) and the kernel V1/V2 sources stay untouched

#### Scenario: Create stores one trimmed live row per modality

- **WHEN** an authorized admin creates a config (modality token, granularity, capacity, open
  windows)
- **THEN** the token stores trimmed verbatim and the row persists live
- **WHEN** a create targets a modality that already has a live row
- **THEN** the create is refused with an `A0400` business refusal naming the duplicate

#### Scenario: Update edits the policy, never the pool identity

- **WHEN** an authorized admin updates a config's granularity, capacity, or open windows
- **THEN** the row updates; the modality token is immutable after create (the pool identity)

#### Scenario: Delete is soft

- **WHEN** an authorized admin deletes a config
- **THEN** the row soft-deletes and the modality's pre-registration refuses fail-closed (the
  kernel unconfigured default)

#### Scenario: List returns the live configs

- **WHEN** an authorized admin lists the slot configs
- **THEN** every live row returns with its windows decoded as structured `{start,end}` pairs

#### Scenario: The perms family gates every action

- **WHEN** a client without the matching `setting:slot:*` perm attempts any config action
- **THEN** the action is refused by the authorization layer

### Requirement: Booking Slot Options Read

The US registration service SHALL expose `GET /api/v1/us/orders/slot-options?date&modality`
gated to `us:order:create` ∪ `setting:slot:list`: the modality's live slot config rendered
as the date's slot grid — the CREATE path's semantics mirrored exactly. The create floors
booking times onto the midnight granularity grid (`(minutesOfDay / g) * g`) and validates
windows LAZILY in order (a booking is accepted by the first containing VALID window; a
malformed window refuses any booking that reaches it). The read therefore SHALL enumerate
the valid windows in order and DEAD-END at the first malformed one (unparseable under the
create's strict trimmed `HH:mm` contract, or `end <= start`): that window and every later
window is unreachable for acceptance and never advertised, while the valid windows before
it stay offered. A slot is offered only when its grid point `t` satisfies `start <= t <
end` AND its start is strictly after the DB-server clock (the create's future-only guard
mirrored — a past date offers nothing). A slot's occupancy window is `[t, t+g)` — the
same cell the create floors into — with LIVE occupancy per slot (the create path's count
predicate: live NOT-CANCELLED orders whose booking_time falls in the window —
未取消即占位, the us-prereg-as-registered anchor; the first-item modality token scopes the
count) and the config's per-slot capacity; a cell whose granularity tail crosses midnight
(granularities that do not divide 1440) counts the tail on the next day. The enumeration
SHALL terminate unconditionally for any stored granularity value (day-minute arithmetic,
no `LocalTime` wrap-around). An unconfigured modality SHALL answer `configured=false`
with no slots (200, fail-soft — the create path stays the fail-closed half); an invalid
date SHALL refuse A0400. The read SHALL NOT take the config-row lock (the create path's
FOR UPDATE serialization stays create-side); the day's occupancy loads in ONE span query
(extended to cover cross-midnight tails; the per-slot N+1 retires).

#### Scenario: The configured grid carries live occupancy

- **WHEN** the slot config defines a 08:00–12:00 window at 30-minute granularity with
  capacity 2 and one BOOKED order sits at 09:17 tomorrow
- **THEN** the endpoint answers 8 slots for tomorrow's date
- **AND** the 09:00–09:30 slot reports booked=1, capacity=2
- **AND** untouched slots report booked=0

#### Scenario: Unconfigured modality fails soft

- **WHEN** the modality has no live slot config
- **THEN** the endpoint answers `configured=false` with an empty slot list (200)

#### Scenario: Invalid date refuses

- **WHEN** the date parameter is not a `yyyy-MM-dd` calendar date
- **THEN** the endpoint refuses with A0400

#### Scenario: Wrap-around granularity cannot pin the enumeration

- **WHEN** the slot config stores granularityMinutes=1440 (or 720 with an 08:00–20:00
  window) — values the config surface stores verbatim
- **THEN** the endpoint returns within normal latency with a finite slot list (an
  off-grid window yields zero slots; no request hangs, no unbounded query loop)

#### Scenario: Off-grid windows offer exactly the bookable grid points

- **WHEN** the slot config defines an 08:00–12:00 window at 45-minute granularity
- **THEN** the offered slot starts are the midnight-grid points within the window
  (08:15, 09:00, 09:45, 10:30, 11:15) — not the raw window start
- **AND** a pre-registration booked at an offered slot start counts into that slot's
  own `[t, t+g)` cell (the picker's count matches the create's acceptance window)

#### Scenario: Malformed windows dead-end the enumeration

- **WHEN** the stored window list carries a VALID window followed by a malformed one
  (unparseable under the strict `HH:mm` contract, e.g. the seconds form `08:00:00`, or
  `end <= start`)
- **THEN** the read offers the valid window's grid points and nothing from the malformed
  window or any later one (a booking reaching them refuses 开台时间窗配置无效)
- **WHEN** the malformed window comes FIRST
- **THEN** the read answers `configured=true` with an empty slot list — nothing is
  bookable, nothing is advertised, and the endpoint does not refuse

#### Scenario: A past date offers nothing

- **WHEN** the date parameter names a past calendar day (a live config exists)
- **THEN** the read answers a finite slot list in which no cell starts at or before the
  DB-server clock — the create's 预登记时间不能早于当前时间 guard mirrored

#### Scenario: Cross-midnight cells count their tails

- **WHEN** granularityMinutes=50 with a 00:00–23:59 window and three live NOT-CANCELLED
  bookings sit on the day AFTER the queried date between 00:02 and 00:08
- **THEN** the queried date's 23:20 slot — whose cell `[23:20, 00:10)` crosses midnight —
  reports booked=3 (the tail counts exactly as the create's occupancy window does)

### Requirement: Birth Date Capture

The US registration create chain SHALL carry an optional birth date end to end:
`RegistrationCreateRequest.birthDate` (nullable) flows through the kernel port
request into the resolve-or-register contract, where the patient domain stores
it on the created walk-in archive and treats it as a soft-match key. The birth
date lives on the archive only — the order snapshot stays age-only and the
order view does not surface a birth date.

#### Scenario: Walk-in birth date joins the archive chain

- **WHEN** an order is created whose body carries `birthDate`
- **THEN** the resolve-or-register contract sent to the patient domain carries
  that birth date as a soft-match key and the created archive holds it
- **AND** the create succeeds and returns the derived state as before

#### Scenario: Birth date is optional

- **WHEN** an order is created whose body carries no `birthDate`
- **THEN** the contract's birth date is null and archive creation behaves
  exactly as before (PROVISIONAL/RESOLVED resolution unchanged)
- **AND** the order snapshot carries age only — no birth date field appears on
  the snapshot or the order view

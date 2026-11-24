<!-- us-registration-redesign-api delta. Parent coordination: openspec/coordination/us-registration-redesign.md
     (拍板 02-B/03-B/06-A/07-A). Consumes kernel-pre-registration (① verify PASS 2026-11-23).
     MODIFIED blocks are supersets of the main-spec scenarios (delta layering rule). -->

## MODIFIED Requirements

### Requirement: Registration Surface

The module SHALL expose the registration lifecycle over `/api/v1/us/orders/**`: create an
order from a patient-registry identity + 检查项目 (setting-catalog references snapshotted into
`exam_items`), page-query orders, transition 登记, and cancel with a reason. Creation SHALL
derive the entry status from the optional `bookingTime` in ONE transaction (拍板 02-B 提交即
登记): a null/absent `bookingTime` enters `REGISTERED` (现场单), a strictly-future one enters
`BOOKED` (预登记态) only after the kernel pre-registration validation passes — the composed
flow runs the capacity dance and the aggregate registration inside ONE transaction while the
patient-registry port resolve stays OUTSIDE it (kernel design D8). The kernel A0400 refusals
surface VERBATIM with nothing persisted. Creation SHALL issue `accession_no` through the
`NumberIssuerPort` (the kernel resolves the accession rule token from the 编号类型 family —
the adapter never picks a rule) inside the creation transaction, and the HTTP surface SHALL
gate every action behind the `us:order:*` permission family. Each picked 项目 reference SHALL
carry that item's modality token from the setting catalog (the dict-1024 value), so the kernel
issues the accession under the modality-scoped rule when one exists and the family global rule
otherwise. The create SHALL store the body's `cardNo` into the patient snapshot (the additive
jsonb key — the card-no workbench query condition reads it). The transitional break on the
pre-③ page is accepted (提交立即 REGISTERED; its 登记 button A0400s on new orders).

#### Scenario: Create a booked order

- **WHEN** an authorized client posts a create body with a resolved patient identity, 检查项目
  references each carrying the item's modality token, and NO `bookingTime`
- **THEN** the order persists with status `REGISTERED` (提交即登记 — the 拍板 02-B derived
  entry), an `accession_no` issued by the platform number rule resolved by that modality
  scope (or the family global rule when no scoped rule exists), the patient snapshot stored
  with the body's `cardNo`, and the id returned
- **WHEN** the same create carries a strictly-future `bookingTime`
- **THEN** the order persists with status `BOOKED` (预登记态) and the requested
  `booking_time` as its slot anchor — the capacity validation having passed inside the same
  transaction

#### Scenario: Invalid pre-registration is refused verbatim

- **WHEN** a create carries a `bookingTime` that is in the past (预登记时间不能早于当前时间),
  targets a modality with no live slot config (该检查类型未开通预登记), falls outside the
  modality's open windows (预登记时间不在开台时间窗内), or lands on a slot at capacity
  (该时段已满)
- **THEN** the kernel A0400 refusal surfaces verbatim with its Chinese message and nothing
  persists

#### Scenario: Below-capacity pre-registration occupies the slot

- **WHEN** a pre-registration create passes the capacity validation against the us schema's
  own live `BOOKED` count in the resolved slot window
- **THEN** the order persists as `BOOKED` with the requested `booking_time` and the window's
  live count increases by one

#### Scenario: The create result carries the derived state

- **WHEN** any create succeeds
- **THEN** the response carries the derived `status` and the echoed `bookingTime` (null for
  walk-ins), and the page view rows carry `bookingTime`

#### Scenario: Illegal transition is refused verbatim

- **WHEN** a client attempts a state jump the kernel state machine forbids (e.g. 登记 on a
  cancelled order, or 登记 on a `REGISTERED` order)
- **THEN** the kernel refusal surfaces verbatim and the order state is unchanged

#### Scenario: Cancel carries a reason

- **WHEN** an authorized client cancels a live order with a reason
- **THEN** the order lands in the matching cancel state (`BOOKING_CANCELLED` from BOOKED,
  `CANCELLED` otherwise) with the reason stored

#### Scenario: Modality reaches the issuer port

- **WHEN** the create body's first item reference carries modality `US`
- **THEN** the kernel port receives the 登记号 family together with that modality token and
  the order stores the item references with their modality intact

## ADDED Requirements

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

- **WHEN** the status-count endpoint receives the wide filter set WITHOUT the status
  condition
- **THEN** it returns one count per document state machine status (`BOOKED`, `REGISTERED`,
  `IN_PROGRESS`, `COMPLETED`, `BOOKING_CANCELLED`, `CANCELLED`), zero-filled, each counted
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

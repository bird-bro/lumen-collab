# us-station Delta

## MODIFIED Requirements

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
`registrationType` alongside `bookingTime`. Creation SHALL additionally require the exam
room (user ruling 2026-11-26: 检查室必选, 默认上次房间 is a frontend affordance — the
server refuses a roomless body): the create body's `roomId` is validated to exist, be
enabled, and belong to the station's department, then stamped onto the order for BOTH
entry branches (预约时选房 for pre-registered, 现场选房 for walk-ins). The page view rows
SHALL carry `roomId` + `roomName` (the name joined at read time; a dangling id yields a
null name).

#### Scenario: Create a booked order

<!-- scenario superseded in place by the 2026-11-24 ruling 预约=预登记: the retired
shape (future → BOOKED) survives only in this comment for history -->

- **WHEN** an authorized client posts a create body with a resolved patient identity, 检查项目
  references each carrying the item's modality token, a validated `roomId`, and NO
  `bookingTime`
- **THEN** the order persists with status `REGISTERED` and `registration_type` `WALK_IN`
  (提交即登记 — the 拍板 02-B derived entry), an `accession_no` issued by the platform number
  rule resolved by that modality scope (or the family global rule when no scoped rule exists),
  the patient snapshot stored with the body's `cardNo`, the `room_id` stamped, and the id
  returned
- **WHEN** the same create carries a strictly-future `bookingTime`
- **THEN** the order persists with status `REGISTERED` (预登记=已登记) and `registration_type`
  `PRE_REGISTERED` with the requested `booking_time` as its slot anchor and the same `room_id`
  stamp — the capacity validation having passed inside the same transaction

#### Scenario: Invalid pre-registration is refused verbatim

- **WHEN** a create carries a `bookingTime` that is in the past (预登记时间不能早于当前时间),
  targets a modality with no live slot config (该检查类型未开通预登记), falls outside the
  modality's open windows (预登记时间不在开台时间窗内), or lands on a slot at capacity
  (该时段已满)
- **THEN** the kernel A0400 refusal surfaces verbatim with its Chinese message and nothing
  persists

#### Scenario: Create refuses a missing or invalid room

<!-- added by us-order-exam-room: the exam-room ruling 2026-11-26 -->

- **WHEN** a create body carries no `roomId` (检查室不能为空)
- **THEN** the refusal surfaces as a validation error and nothing persists
- **WHEN** a create body carries a `roomId` that does not exist, is disabled, or belongs
  to another department (检查室不存在或未启用)
- **THEN** the refusal surfaces with that message and nothing persists — the room scope is
  the station's department, resolved server-side

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
  `registrationType`, the operator stamps `createTime`/`createBy` (登记时间/登记人 = the
  create stamps for both kinds), and the room pair `roomId`/`roomName`

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

## ADDED Requirements

### Requirement: Station Room Directory

The module SHALL expose the station's exam-room directory over
`GET /api/v1/us/rooms` behind `us:order:create` (the room pick is a create-time
decision). The directory SHALL resolve the station's department rooms through the
`lumen-feign` system room adapter (`org.birdbro.lumen.feign.system`) calling the
provider's INTERNAL rooms read (`GET /api/v1/system/internal/org-rooms/list` — the
X-Internal-Key family, the NumberIssueController precedent) with the station's
configured department (`lumen.us.station-dept-id`), returning the ENABLED rooms as
slim rows (`id` + `name` only). The Sentinel fallback SHALL return an empty list with
the load error surfaced — a required field with no options blocks the desk honestly.

#### Scenario: Station rooms list the department's enabled rooms

- **WHEN** the workbench requests `GET /api/v1/us/rooms`
- **THEN** the response lists the configured department's enabled rooms as
  `{id, name}` rows, ordered by id

#### Scenario: Room service unavailability degrades to an honest empty list

- **WHEN** the lumen-system room read fails (Sentinel fallback)
- **THEN** the endpoint returns an empty list and the station surfaces the load
  failure — the desk cannot submit a roomless order (the requirement is enforced at
  create time regardless)

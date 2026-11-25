## MODIFIED Requirements

### Requirement: Registration Surface

The module SHALL expose the registration lifecycle over `/api/v1/us/orders/**`: create an
order from a patient-registry identity + 检查项目 (setting-catalog references snapshotted into
`exam_items`), page-query orders, transition 登记, and cancel with a reason. Creation SHALL
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
workbench query condition reads it). The 登记 transition endpoint stays for machine completeness;
US-flow rows never sit in `BOOKED` (no arrival-confirmation step exists — the page drops the
登记 action, us-prereg-as-registered-web). The page view rows SHALL carry `createTime` /
`createBy` (the 登记时间/登记人 stamps — the create stamps serve BOTH kinds; the earlier
`registered_time`/`registered_by` idea is retracted) and `registrationType` alongside
`bookingTime`.

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

- **WHEN** a client attempts a state jump the kernel state machine forbids (e.g. 登记 on a
  cancelled order, or 登记 on a `REGISTERED` order)
- **THEN** the kernel refusal surfaces verbatim and the order state is unchanged

#### Scenario: Cancel carries a reason

- **WHEN** an authorized client cancels a live order with a reason
- **THEN** the order lands in `CANCELLED` with the reason stored (the US flow no longer
  produces `BOOKED` rows — the machine's `BOOKING_CANCELLED` branch stays reserved but
  unreachable from the US flow)

#### Scenario: Modality reaches the issuer port

- **WHEN** the create body's first item reference carries modality `US`
- **THEN** the kernel port receives the 登记号 family together with that modality token and
  the order stores the item references with their modality intact

# us-station Delta

## MODIFIED Requirements

### Requirement: Registration Surface

The module SHALL expose the registration lifecycle over `/api/v1/us/orders/**`: create a booked
order from a patient-registry identity + 检查项目 (setting-catalog references snapshotted into
`exam_items`), page-query orders, transition 登记, and cancel with a reason. Creation SHALL issue
`accession_no` through the `NumberIssuerPort` (the kernel resolves the accession rule token from
the 编号类型 family — the adapter never picks a rule) inside the creation transaction, and the
HTTP surface SHALL gate every action behind the `us:order:*` permission family. Each picked
项目 reference SHALL carry that item's modality token from the setting catalog (the dict-1024
value), so the kernel issues the accession under the modality-scoped rule when one exists and
the family global rule otherwise:

#### Scenario: Create a booked order

- **WHEN** an authorized client posts a create body with a resolved patient identity, 检查项目
  references each carrying the item's modality token, and optional 急诊 flag
- **THEN** the order persists with status `BOOKED`, an `accession_no` issued by the platform
  number rule resolved by that modality scope (or the family global rule when no scoped rule
  exists), the patient snapshot stored, and the id returned

#### Scenario: Illegal transition is refused verbatim

- **WHEN** a client attempts a state jump the kernel state machine forbids (e.g. 登记 on a
  cancelled order)
- **THEN** the kernel refusal surfaces verbatim and the order state is unchanged

#### Scenario: Cancel carries a reason

- **WHEN** an authorized client cancels a live order with a reason
- **THEN** the order lands in the matching cancel state (`BOOKING_CANCELLED` from BOOKED,
  `CANCELLED` otherwise) with the reason stored

#### Scenario: Modality reaches the issuer port

- **WHEN** the create body's first item reference carries modality `US`
- **THEN** the kernel port receives the 登记号 family together with that modality token and
  the order stores the item references with their modality intact

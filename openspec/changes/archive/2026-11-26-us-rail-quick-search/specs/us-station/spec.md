# us-station Delta

## MODIFIED Requirements

### Requirement: Workbench Query Surface

The page query SHALL serve the two-state workbench's wide-list state (拍板 03-B, the
RCP ∪ EIS condition union) over `GET /api/v1/us/orders` behind `us:order:list`: status
multi-select (IN semantics; empty = all), the rail quick-search `keyword` (ONE text box
OR-matching the identity handles — patient name, accession (检查号; the v1 影像号 rides
the accession family), snapshot `patId`/`cardNo` — AND-composed with the other wide
conditions; user ruling 2026-11-26), accession number fuzzy, patient name fuzzy (jsonb
snapshot), patient-id exact (snapshot `patId`), patient-source exact (snapshot), card-no
exact (snapshot `cardNo`), exam-item name fuzzy over the `exam_items` jsonb array, and
the apply-time range (the order's `create_time` — the kernel carries no separate apply
timestamp). All conditions SHALL compose. A status-count endpoint (`GET
/api/v1/us/orders/status-counts`, same `us:order:list` perm) SHALL return per-status
counts under the SAME filter set minus the status condition (EIS ReportStatusCheckbox
semantics — the chips are the desk workload meter), zero-filled for every document state
machine status; the keyword narrows the meter with the list.

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

#### Scenario: The rail quick-search OR-matches the identity handles

- **WHEN** the query carries a non-blank `keyword`
- **THEN** an order matches when its snapshot name, its `accession_no`, its snapshot
  `patId`, or its snapshot `cardNo` contains the keyword (OR-composed; the 检查号 and
  the 影像号 ride the accession family in v1)
- **AND** the keyword AND-composes with the other wide conditions as usual
- **AND** the status-count meter applies the same keyword (the chips narrow with the
  search)

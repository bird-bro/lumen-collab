# web-us-station Delta

## MODIFIED Requirements

### Requirement: Order Form and Actions

The order form SHALL collect 检查项目 from the setting catalog (US-modality items), the US
extension block (妊娠状态/孕周/备注, optional), and the急诊 flag, then submit the create body and
surface the returned accession on success. Each selected 项目 SHALL ride its modality token
(the setting catalog's dict-1024 value) in its item reference so the backend resolves the
modality-scoped number rule. The order list SHALL offer 登记 and 取消 actions on
live orders — 取消 requires a reason input with confirmation — and SHALL surface state-machine
refusals from the backend verbatim without altering the row.

#### Scenario: Create posts identity, items, and extension

- **WHEN** the operator submits a valid create body with a resolved identity, US items, and the
  pregnancy extension
- **THEN** the drawer posts to the us create endpoint, closes on success, and the refreshed row
  shows the issued accession

#### Scenario: Item modality rides the create payload

- **WHEN** the operator picks a 项目 from the setting catalog and submits the create body
- **THEN** each item reference in the payload carries that 项目's modality token as returned
  by the catalog

#### Scenario: 登记 transitions the order

- **WHEN** the operator runs 登记 on a BOOKED order
- **THEN** the transition posts and the row's state tag updates

#### Scenario: Cancel requires a reason and confirmation

- **WHEN** the operator cancels a live order
- **THEN** a confirmation step with a reason input runs first and the row lands in the cancel state

#### Scenario: Illegal transition surfaces verbatim

- **WHEN** a transition the backend state machine forbids is attempted
- **THEN** the backend refusal message renders verbatim and the row is unchanged

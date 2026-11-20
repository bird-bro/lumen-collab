## ADDED Requirements

### Requirement: Registration Page Shell

The US workstation SHALL gain a 登记 page mounted at route `us/registration` (component key
`us/registration/index`), reachable when the US workstation W node is activated and the user holds
`us:order:list`; page affordances SHALL gate on `us:order:{create,update,cancel}` and hide without
them. The page SHALL follow the workstation visual conventions already established by the console
shell kit.

#### Scenario: Page renders under the activated US workstation

- **WHEN** the user activates the US workstation W node and holds `us:order:list`
- **THEN** the sidebar offers 登记 and the route renders the registration page against the real
  `/api/v1/us/**` backend

#### Scenario: Actions hide without perms

- **WHEN** the user lacks `us:order:create`
- **THEN** the 新建登记 affordance is hidden and no order form opens

### Requirement: Patient Resolution Flow

Order creation SHALL drive the patient resolution chain (patient decision #2) through the us-side
resolve passthrough: an `id_no` query that exactly matches fills the demographics; a `card_no`
query that soft-matches offers the candidate list for selection; no match opens the demographics
form for a new archive, including the 急诊 unnamed placeholder (PROVISIONAL) path. The chosen or
created identity rides the order create body; the flow SHALL NOT write patient data outside the
us endpoints.

#### Scenario: Exact id_no match fills demographics

- **WHEN** the operator queries an `id_no` that exactly matches a live archive
- **THEN** the demographics form fills from the archive and the order links that identity on
  submit

#### Scenario: Soft-match candidates offer selection

- **WHEN** the query soft-matches by name+phone / name+gender+birth
- **THEN** the candidate list renders and picking one fills the demographics for the order

#### Scenario: No match opens a new archive with placeholder path

- **WHEN** the resolution returns no candidate
- **THEN** the demographics form opens for a new archive, with the 急诊占位 (unnamed PROVISIONAL)
  toggle issuing the image number normally

### Requirement: Order Form and Actions

The order form SHALL collect 检查项目 from the setting catalog (US-modality items), the US
extension block (妊娠状态/孕周/备注, optional), and the急诊 flag, then submit the create body and
surface the returned accession on success. The order list SHALL offer 登记 and 取消 actions on
live orders — 取消 requires a reason input with confirmation — and SHALL surface state-machine
refusals from the backend verbatim without altering the row.

#### Scenario: Create posts identity, items, and extension

- **WHEN** the operator submits a valid create body with a resolved identity, US items, and the
  pregnancy extension
- **THEN** the drawer posts to the us create endpoint, closes on success, and the refreshed row
  shows the issued accession

#### Scenario: 登记 transitions the order

- **WHEN** the operator runs 登记 on a BOOKED order
- **THEN** the transition posts and the row's state tag updates

#### Scenario: Cancel requires a reason and confirmation

- **WHEN** the operator cancels a live order
- **THEN** a confirmation step with a reason input runs first and the row lands in the cancel state

#### Scenario: Illegal transition surfaces verbatim

- **WHEN** a transition the backend state machine forbids is attempted
- **THEN** the backend refusal message renders verbatim and the row is unchanged

### Requirement: Order List

The registration page SHALL list orders with pagination, state tags
(BOOKED/REGISTERED/IN_PROGRESS/COMPLETED/cancel branches), accession 检查号, patient identity
(name + pat_id), 检查项目 summary, and the急诊 flag marker, querying the real page endpoint with
default filters suited to the day's work (today first).

#### Scenario: List renders with state and identity

- **WHEN** the page loads
- **THEN** the paged orders render with accession, patient identity, item summary, state tags, and
  the急诊 marker, from the real backend

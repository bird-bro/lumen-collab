# web-us-station Specification

## Purpose

Defines the US workstation registration UI: the 登记 page mounted under the US workstation
W node (component key `us/registration/index`), the patient resolution-chain flow with the
shared PatientDemographicsForm component, the order form (US-modality items + the pregnancy
extension) and state actions, and the order list. Every request goes through the real
`/api/v1/us/**` backend; `us:order:*` perms gate the affordances.

## Requirements

<!-- synced from: web-us-station-redesign 2026-11-24 -->

### Requirement: Registration Page Shell

The US workstation SHALL keep the 登记 page at route `us/registration` (component key
`us/registration/index`), reachable when the US workstation W node is activated and the user
holds `us:order:list`; page affordances SHALL gate on `us:order:{create,update,cancel}` and
hide without them. The page SHALL follow the workstation visual conventions already
established by the console shell kit, and SHALL render as the TWO-STATE workbench (拍板
01-B, the prototype's 壹 section): an edge toggle switches between the COMPACT state (the
392px 今日登记单 rail beside the registration workbench form) and the WIDE-LIST query state
(the full-width table with the wide query row). The registration drawer retires.

#### Scenario: Page renders under the activated US workstation

- **WHEN** the user activates the US workstation W node and holds `us:order:list`
- **THEN** the sidebar offers 登记 and the route renders the compact workbench state
  against the real `/api/v1/us/**` backend

#### Scenario: Actions hide without perms

- **WHEN** the user lacks `us:order:create`
- **THEN** the 提交登记 affordance is hidden and no order form accepts a submit

#### Scenario: The edge toggle switches the two states

- **WHEN** the operator toggles the workbench edge
- **THEN** the page switches between the compact state (rail + form) and the wide-list
  state (full-width table + query row) — one route, no navigation loss

### Requirement: Patient Resolution Flow

Order creation SHALL drive the patient resolution chain (patient decision #2) through the
us-side resolve passthrough from the workbench's TOP RESOLVE BAR (the prototype's single-row
解析条 above section 1): an `id_no` query that exactly matches fills the demographics;
a `card_no` query that soft-matches offers the 就诊人候选 dialog (the RCP card-style
candidates, the prototype's 贰 section) for selection; no match opens the demographics form
for a new archive. The 急诊占位 switch is RETIRED (拍板: server judges by name=null) — an
emergency order with an empty name is the placeholder archive, judged server-side. The
chosen or created identity rides the order create body; the flow SHALL NOT write patient
data outside the us endpoints.

#### Scenario: Exact id_no match fills demographics

- **WHEN** the operator queries an `id_no` that exactly matches a live archive
- **THEN** the demographics section fills from the archive and the order links that identity
  on submit

#### Scenario: Soft-match candidates offer selection

- **WHEN** the query soft-matches by name+phone / name+gender+birth
- **THEN** the 就诊人候选 dialog renders the RCP-style candidate cards and picking one fills
  the demographics for the order

#### Scenario: No match opens a new archive with placeholder path

- **WHEN** the resolution returns no candidate
- **THEN** the demographics form accepts a new archive (the emergency placeholder rides the
  empty-name rule — no client switch)

### Requirement: Order Form and Actions

<!-- synced from: number-rule-modality-scope 2026-11-23 (item modality token rides the create payload) -->
The order form SHALL be the three-section EIS workbench (拍板 05-A, the prototype's form
density: label 72px right-aligned, three columns, 9px row rhythm): 1 患者信息 (the 3×3 grid
with the ruled merged cells — 证件/身高体重/住院床号/妊娠 merge, 性别/年龄 do not), 2 请检
信息 (the 急诊 switch in the SECTION TITLE row, the pregnancy cell gender-conditional with
未孕/未知 disabling the weeks, the 预登记时间 picker, 临床诊断 riding ext.note), 3 检查信息
(the common-item chips + the picked list + the 添加项目 dialog over the setting catalog).
The form SHALL collect 检查项目 from the setting catalog (US-modality items), the US
extension block (妊娠状态/孕周/备注), and the 急诊 flag, then submit the create body and
surface the returned accession on success — SUBMIT REGISTERS for BOTH kinds (拍板 02-B;
预约=预登记, user ruling 2026-11-24): a null 预登记时间 creates a REGISTERED 现场单
immediately; a picked future time posts `bookingTime` (yyyy-MM-dd HH:mm:ss) and the order
ALSO enters 已登记 (REGISTERED) carrying the 预约 category — there is NO arrival
confirmation step, the patient goes straight into the exam. The backend's four A0400
refusals (该时段已满 / 不在开台时间窗内 / 预登记时间不能早于当前时间 / 该检查类型未开通预登记)
surface VERBATIM near the picker. Each selected 项目 SHALL ride its modality token (the
setting catalog's dict-1024 value) in its item reference so the backend resolves the
modality-scoped number rule. The order list SHALL offer 取消 on live orders — 取消 requires
a reason input with confirmation — and SHALL surface state-machine refusals from the backend
verbatim without altering the row; NO 登记 action exists (the arrival confirmation is
retired with the two-stage model).

#### Scenario: Create posts identity, items, and extension

- **WHEN** the operator submits a valid create body with a resolved identity, US items, and
  the pregnancy extension
- **THEN** the workbench posts to the us create endpoint, resets the form on success, and
  the refreshed list shows the issued accession and the derived state tag

#### Scenario: Pre-registration time posts the slot anchor

- **WHEN** the operator picks a future 预登记时间 and submits
- **THEN** the payload carries `bookingTime` and the refreshed row lands 已登记 (REGISTERED)
  with the 预约 category tag (预登记=已登记)
- **WHEN** the backend refuses the slot (full / outside windows / past / unconfigured)
- **THEN** the refusal renders verbatim near the picker and nothing persists

#### Scenario: Item modality rides the create payload

- **WHEN** the operator picks a 项目 from the setting catalog and submits the create body
- **THEN** each item reference in the payload carries that 项目's modality token as returned
  by the catalog

#### Scenario: 登记 transitions the order

<!-- scenario superseded in place by the 2026-11-24 ruling 预约=预登记: the arrival
confirmation is retired; the scenario pins its absence -->

- **WHEN** the operator looks for a 登记 (arrival-confirmation) action on any live row
- **THEN** none exists — 预登记 rows enter 已登记 at creation and need no confirmation
- **AND** the only row actions on live orders are 取消 (with reason + confirmation)

#### Scenario: Cancel requires a reason and confirmation

- **WHEN** the operator cancels a live order
- **THEN** a confirmation step with a reason input runs first and the row lands in the cancel
  state

#### Scenario: Illegal transition surfaces verbatim

- **WHEN** a transition the backend state machine forbids is attempted
- **THEN** the backend refusal message renders verbatim and the row is unchanged

### Requirement: Order List

The registration workbench SHALL present orders in BOTH states from the real page endpoint:
the COMPACT rail renders the day's 今日登记单 (the prototype's dense rows: patient composite
two-line, state tag, row actions) under the status chips; the WIDE state renders the full
paged table (accession 检查号, patient identity name + pat_id, 检查项目 summary, state tags,
the 急诊 marker, the 类别 tag, 预登记时间). The 类别 tag SHALL read 现场 / 预约 from the
row's `registrationType` (the business fact — the `bookingTime` NULL/NOT NULL inference is
retired). The status chips (拍板 03-B: not a dropdown — the chips are the desk workload meter)
SHALL carry the `/orders/status-counts` counts, support multi-select-toggle semantics with the
default 已登记 (the 预登记 status no longer occurs in the US flow — its chip renders zero),
and feed the status IN-filter. The wide state SHALL expose the wide query row (拍板 03-B, the
RCP∪EIS union: accession/patient-name/patient-id/patient-source/card-no/exam-item name/创建时间
range — the time filter binds `create_time` and is LABELED 创建时间, not 申请时间, so it never
collides with the form's ext 申请时间 field; uniform 150px controls, the 更多条件 toggle; the
compact state carries NO query row) and 我的查询 (拍板 07-A): saved searches persisted through
the search-filters REST (create/set-default/delete; the default auto-applies on load) with
column settings kept in localStorage (the EIS useHeaderColumn pattern, frontend-only). The
compact rail and the autorefresh mini switch follow the prototype's 紧凑化对照.

#### Scenario: List renders with state and identity

- **WHEN** the page loads
- **THEN** the orders render from the real backend — the compact rail beside the form, the
  wide table on toggle — with accession, patient identity, item summary, state tags, the
  急诊 marker, the 现场/预约 category tag from `registrationType`, and the 预登记时间 where set

#### Scenario: Status chips carry the meter and multi-select

- **WHEN** the workbench loads (or refreshes) in either state
- **THEN** the chips render the zero-filled per-status counts from the status-counts endpoint,
  multi-select toggles narrow the list through the status IN-filter, and the default selection
  is 已登记

#### Scenario: Wide conditions compose through the query row

- **WHEN** the operator fills any wide condition (including the 更多条件 extras) in the wide
  state
- **THEN** the list queries the page endpoint with the composed parameters and the results
  reflect every condition

#### Scenario: 我的查询 persists and auto-applies the default

- **WHEN** the operator saves the current conditions as a named search and later sets one
  as the page default
- **THEN** the searches persist through the search-filters REST for this user only, and
  reloading the page auto-applies the default search's conditions

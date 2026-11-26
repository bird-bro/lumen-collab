# web-us-station Delta

## MODIFIED Requirements

### Requirement: Order Form and Actions

<!-- synced from: number-rule-modality-scope 2026-11-23 (item modality token rides the create payload) -->
The order form SHALL be the three-section EIS workbench (拍板 05-A, the prototype's form
density: label 72px right-aligned, three columns, 9px row rhythm): 1 患者信息 (the 3×3 grid
with the ruled merged cells — 证件/身高体重/住院床号/妊娠 merge, 性别/年龄 do not), 2 请检
信息 (the 急诊 switch in the SECTION TITLE row, the pregnancy cell gender-conditional with
未孕/未知 disabling the weeks, the 预登记时间 picker, 临床诊断 riding ext.note), 3 检查信息
(the common-item chips + the picked list + the 添加项目 dialog over the setting catalog).
Section 3 SHALL additionally open with the REQUIRED 检查室 Select as the left column's
top row (user ruling 2026-11-26: 现场单检查室必选，默认上次房间): one line above the
picked-items table at the standard control height, options loaded from the station
rooms read (`GET /api/v1/us/rooms`, station-scoped server-side — the web never passes
org/dep ids), defaulting to the workstation's last choice (localStorage `us-last-room`,
shared with the booking dialog's picker) and persisting the choice back on every
successful submit. Submission without a room SHALL be refused client-side
(检查室不能为空) — the server's own refusal surfaces verbatim as well. The form SHALL
collect 检查项目 from the setting catalog (US-modality items), the US
extension block (妊娠状态/孕周/备注), the 急诊 flag, and the 检查室, then submit the create body and
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

#### Scenario: The room is required and rides the payload

<!-- added by us-order-exam-room: the 2026-11-26 检查室 ruling -->

- **WHEN** the operator submits without picking a 检查室
- **THEN** the submit is refused client-side with 检查室不能为空 and nothing posts
- **WHEN** the operator submits with a picked room
- **THEN** the payload carries `roomId` and the workstation's last choice is persisted
  (the next fresh form pre-selects it)

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
retired). The rows SHALL additionally carry the exam room: the compact card renders the
room's `roomName` inside an existing meta line (the four-row card-flow ruling — no fifth
row, no horizontal scroll, no font shrinking), the wide table gains a 检查室 column; a
null room (legacy rows, dangling id) renders '—'. The status chips (拍板 03-B: not a dropdown — the chips are the desk workload meter)
SHALL render the SIX machine statuses (已登记/检查中/诊断中/已完成/已打印/已取消, user ruling
2026-11-26) with labels AND colors resolved from dict catalog 1031 「登记单状态」 through
the id-anchored session-cached lookup (`dictItemsById` + the recorded enum-name→code
bridge; chip order follows the dict `sort`; the DB-configured `tag` color token maps
through the design-token hue map — blue/green/amber/red/gray, unknown/absent → the
neutral fills, never ad-hoc hex — user ruling 2026-11-26), carry the
`/orders/status-counts` counts, support multi-select-toggle semantics with the default
已登记, and feed the status IN-filter (the former 预登记 zero chip is retired with its
state — the machine no longer defines it; the hard-coded `STATUS_LABELS` map is retired
with it). The state tags on the compact cards and the wide table resolve their labels
and colors through the same catalog-1031 lookup with the bridge's 中文 labels and the
neutral tint as the failure fallback. The wide state SHALL expose the wide query row (拍板 03-B, the
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
  急诊 marker, the 现场/预约 category tag from `registrationType`, the 预登记时间 where set,
  and the 检查室 name (the compact card's meta line / the wide table's column; '—' when
  the room is absent)

#### Scenario: Status chips carry the meter and multi-select

<!-- scenario superseded in place by the 2026-11-26 six-state ruling: the retired
shape (hard-coded STATUS_LABELS over the former enum incl. a zero-rendering 预登记
chip) survives only in this comment for history -->

- **WHEN** the workbench loads (or refreshes) in either state
- **THEN** the six chips render in dict-1031 `sort` order with the dict-resolved labels
  and the dict-configured colors (the `tag` hue through the design-token map) and the
  zero-filled per-status counts from the status-counts endpoint, multi-select toggles
  narrow the list through the status IN-filter, and the default selection is 已登记

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

### Requirement: Booking Slot Dialog

The 预约时间 cell SHALL collapse the inline date picker + slot select into ONE
trigger (calendar icon + summary text, `aria-label="选择预约时段"`) that opens the
选择预约时段 dialog (`role="dialog"`; the UI authority is
docs/prototypes/us-booking-slot-dialog.html). The dialog SHALL open on TOMORROW with
the mini month calendar ALWAYS VISIBLE (no popover; the 2026-11-24 mid-apply ruling)
and bound the booking date domain to tomorrow onward (D+1): today and earlier SHALL
NOT be selectable (today = register in place = walk-in), and the calendar SHALL gray
today with the 即到即检 note. Each selectable day SHALL carry a day-level remaining
badge (余 N; 满 when the day's configured grid is fully booked) aggregated
CLIENT-SIDE over the existing per-date slot-options read (parallel prefetch of the
displayed month's future days; unconfigured or failed days simply carry no badge - a
dedicated day-level range endpoint stays a future optimization). The slot grid SHALL
group
by 上午/下午 and render each slot as a card with the `HH:mm–HH:mm` range, a
segmented capacity meter (1 cell = 1 号; capacity > 10 degrades to a progress bar)
and the remaining count; a full slot (booked = capacity) SHALL render grayed and
disabled with 已满, and a slot with exactly 1 remaining SHALL carry the tight
(仅剩 1 号) emphasis. A summary line SHALL total the day's 剩余/总号. The dialog
footer SHALL carry the REQUIRED 检查室 Select left of 取消/确定 (user ruling
2026-11-26: 预登记的检查室在预约时选定): the options load from the same station rooms
read, the choice defaults to the workstation's last choice (localStorage
`us-last-room`, shared with the section-3 picker), and 确定 stays disabled until BOTH
a slot and a room are picked; the confirmed room rides the create body's `roomId`
with the slot pair. 确定 SHALL
write the date+slot pair (bookingTime = `<date> <START>:00`, date ≥ tomorrow);
clearing a picked booking via the trigger's 清除预约时段 affordance (or closing
without confirming a selection) SHALL submit a null bookingTime (walk-in unchanged —
the room choice itself is NEVER cleared by clearing the booking; the walk-in path
requires its own room pick in section 3). An unconfigured modality (`configured=false`) or a failed lookup SHALL disable the
trigger (未开通预约) without blocking walk-ins. The submit value SHALL remain the
slot START `HH:mm` (the bookingTime composition is unchanged).

#### Scenario: The dialog opens on tomorrow with the grouped grid

- **WHEN** the operator clicks the 预约 trigger
- **THEN** the dialog opens with the calendar visible (tomorrow highlighted, today
  grayed/disabled) and the configured slots grouped 上午/下午, each card showing its
  range, capacity meter and remaining count
- **AND** the calendar's selectable days carry the day-level remaining badges for
  the displayed month

#### Scenario: The full slot is grayed and the tight slot emphasized

- **WHEN** the slot-options read answers a grid where one slot's booked equals its
  capacity and another has exactly 1 remaining
- **THEN** the full slot renders grayed/disabled with 已满 and cannot be picked
- **AND** the 1-remaining slot renders the 仅剩 1 号 tight emphasis

#### Scenario: Confirming a slot writes the date+slot pair

- **WHEN** the operator picks a slot and confirms
- **THEN** the trigger summary shows the picked 周X MM-DD HH:mm–HH:mm
- **AND** submitting composes bookingTime as `<picked-date> <START>:00` with the
  picked date ≥ tomorrow

#### Scenario: Confirm requires slot AND room

<!-- added by us-order-exam-room: the 2026-11-26 booking-room ruling -->

- **WHEN** the operator picks a slot but no 检查室 (and no last-choice default
  applies)
- **THEN** 确定 stays disabled until a room is picked
- **WHEN** the operator confirms with both picked
- **THEN** the pending booking carries the room and the create posts `bookingTime`
  together with `roomId`

#### Scenario: Blank or cleared keeps the walk-in path

- **WHEN** the operator closes without confirming a selection, or clears a picked
  booking via the trigger's 清除预约时段 affordance
- **THEN** submitting composes a null bookingTime and the order registers in place
- **AND** the walk-in submission still requires the section-3 检查室 pick (clearing
  the booking never clears the room choice)

#### Scenario: Unconfigured or failed lookup degrades without blocking

- **WHEN** the slot-options fetch fails or answers configured=false
- **THEN** the trigger renders disabled (未开通预约) and the walk-in path still works

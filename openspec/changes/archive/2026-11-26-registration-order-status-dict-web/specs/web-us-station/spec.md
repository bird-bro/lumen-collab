# web-us-station Delta

## MODIFIED Requirements

### Requirement: Order List

The registration workbench SHALL present orders in BOTH states from the real page endpoint:
the COMPACT rail renders the day's 今日登记单 (the prototype's dense rows: patient composite
two-line, state tag, row actions) under the status chips; the WIDE state renders the full
paged table (accession 检查号, patient identity name + pat_id, 检查项目 summary, state tags,
the 急诊 marker, the 类别 tag, 预登记时间). The 类别 tag SHALL read 现场 / 预约 from the
row's `registrationType` (the business fact — the `bookingTime` NULL/NOT NULL inference is
retired). The status chips (拍板 03-B: not a dropdown — the chips are the desk workload meter)
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
  急诊 marker, the 现场/预约 category tag from `registrationType`, and the 预登记时间 where set

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

### Requirement: Registration Workbench Dict-Resolved Vocabularies

The US registration workbench SHALL resolve its five vocabularies from the dict system through
the id-anchored session-cached lookup (`dictItemsById`): 性别 from catalog 1004 (stored token =
the dict numeric code), 证件类型 from catalog 1006, 患者来源 from catalog 1023, 妊娠状态 from
catalog 1002 (stored token = the dict value text for the three string fields), and the status
labels + colors from catalog 1031 「登记单状态」 (a LABEL/COLOR-ONLY vocabulary — the stored
status token stays the backend enum name, bridged by the recorded enum-name→code contract;
the bridge's 中文 labels and the neutral tint are the contract's failure fallback, not a
hard-coded option set; the `tag` color token maps through the design-token hue map). The page SHALL NOT
carry hard-coded option constants for these vocabularies. The wide-list 患者类型 filter SHALL
ride the same 患者来源 catalog. A failed dict fetch SHALL degrade to an empty option set with
one error message and never block the workbench — EXCEPT the catalog-1031 status labels, whose
failure SHALL degrade to the bridge's fallback labels (the desk meter must survive a dict
outage) with the same one error message.

#### Scenario: Form selects render dict-driven options

- **WHEN** the workbench mounts and the dict lookups succeed
- **THEN** the gender radios render the catalog-1004 labels with the dict codes as values
- **AND** the 证件类型 / 患者来源 / 妊娠状态 selects render the catalog value texts
  as both option value and label
- **AND** the wide-list 患者类型 filter offers the 患者来源 catalog values

#### Scenario: Pregnancy cell conditions on the dict female code

- **WHEN** the gender value equals the dict female code (100402)
- **THEN** the 妊娠 cell renders; any other code or an unset gender keeps it hidden
- **AND** the 孕周 input disables while 妊娠状态 is 未孕 or 未知

#### Scenario: Status chips resolve labels from catalog 1031

- **WHEN** the workbench resolves its status labels through catalog 1031
- **THEN** the chips and the state tags render the catalog's value texts AND the
  catalog's `tag` color tokens (through the design-token hue map) in dict `sort`
  order over the six machine statuses, anchored by the recorded enum-name→code bridge
- **AND** the stored/wire status token remains the enum name (the dict is the label
  and color authority only)

#### Scenario: Dict failure degrades without blocking

- **WHEN** a dict lookup fails
- **THEN** the page shows one error message and renders the affected control with
  no options (creation still possible without the optional fields)
- **AND** no hard-coded fallback option set reappears — EXCEPT the catalog-1031 status
  labels, which degrade to the bridge's fallback labels so the chips keep rendering
  the meter

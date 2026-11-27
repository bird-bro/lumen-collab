## MODIFIED Requirements

### Requirement: Order List

The compact rail card SHALL carry the v2 anatomy (user-drawn prototype
`docs/prototypes/us-rail-card-v2.html` variant A, approved 2026-11-26): identity band,
hairline separator, time band, items row, ids row, the left state stripe — with the
visit source in the head lead and ONE 检查时间 anchor. The wide table on toggle SHALL
keep the full-column form.

#### Scenario: List renders with state and identity

- **WHEN** the page loads
- **THEN** the orders render from the real backend — the compact rail beside the form,
  the wide table on toggle — with accession, patient identity, item summary, state
  tags, the 检查室 name (the compact card's time band pin segment, rendered only when
  a room is set / the wide table's column, which renders '—' when the room is absent)
- **AND** the compact card head reads the patient name (the card's only bold text),
  the gender·age pair, and the visit source as plain tertiary text behind a dot
  separator — the 现场/预约 category chip is dropped from the card (the fact stays
  server-stamped on `registrationType`; the wide table keeps the tag beside the status
  chip), and the head tail carries the cancel action alone (a cancelled row's tail
  renders the em-dash placeholder instead)
- **AND** the time band reads ONE 检查时间 anchor under the clock glyph — the booked
  slot datetime for 预约单, the registration moment (当天 + 时分) for 现场单 — the
  full 年月日时分 at regular weight in the normal ink (NOT the order-log semantics;
  the wide table's 创建时间 column keeps the audit value), with the status chip alone
  on the band's right edge

#### Scenario: The card anatomy carries the v2 layout

- **WHEN** a compact card renders
- **THEN** a 4px left stripe rides the dict-1031 tag hue of the order status (unconfigured
  hue renders transparent; CANCELLED renders the diagonal hatch with the whole card at
  0.55 opacity and no cancel action)
- **AND** a hairline separates the identity band from the clinical body
- **AND** the items line defaults to ONE line with a click-to-expand chevron on
  multi-item cards (the chevron toggles the full item list and its aria-expanded state;
  single-item cards render no chevron, and no hover tooltip serves the overflow)
- **AND** the ids line reads the 就诊卡号 (snapshot cardNo; absent on legacy rows — the
  number line then shows the accession alone) and the 检查流水号 behind icon prefixes
  with the quaternary dot dropped
- **AND** live emergency orders render the red 急 glyph at the ids line's right edge and
  a red card border; cancelled orders never alarm — the default line + the dim/hatch
  treatment is the card's exit state, and the 急 glyph stays as the record's fact dimmed
  with the card (the border is the alarm channel)

#### Scenario: The card keeps one icon and one bold discipline

- **WHEN** the card renders its icons and text
- **THEN** every card icon (clock, room pin, idcard, barcode, items chevron) renders
  tertiary gray with no tint or background — color stays reserved for the status chip,
  the 急 glyph, and the cancel action
- **AND** the 20px patient name is the card's only bold text (the time value and room
  read regular weight in the normal ink)

#### Scenario: Status chips carry the meter and multi-select

<!-- carried verbatim from the main spec (the six-state 2026-11-26 ruling note kept) -->

- **WHEN** the workbench loads (or refreshes) in either state
- **THEN** the six chips render in dict-1031 `sort` order with the dict-resolved labels
  and the dict-configured colors (the `tag` hue through the design-token map) and the
  zero-filled per-status counts from the status-counts endpoint, multi-select toggles
  narrow the list through the status IN-filter, and the default selection is 已登记

#### Scenario: The rail quick-search narrows the list and the meter

<!-- carried verbatim from the main spec -->

- **WHEN** the operator types into the compact rail's quick-search box
- **THEN** the debounced keyword rides BOTH the page query and the status-count query
  (the chips' meter narrows with the list), server-side OR-matching name / accession /
  snapshot patId / cardNo

#### Scenario: Wide conditions compose through the query row

<!-- carried verbatim from the main spec -->

- **WHEN** the operator fills any wide condition (including the 更多条件 extras) in the wide
  state
- **THEN** the list queries the page endpoint with the composed parameters and the results
  reflect every condition

#### Scenario: 我的查询 persists and auto-applies the default

<!-- carried verbatim from the main spec -->

- **WHEN** the operator saves the current conditions as a named search and later sets one
  as the page default
- **THEN** the searches persist through the search-filters REST for this user only, and
  reloading the page auto-applies the default search's conditions

## ADDED Requirements

### Requirement: Card Rail Anchor Semantics

The compact card's time anchor SHALL always read as the 检查时间 — the pre-registration
slot value (`bookingTime`) for 预约单, and the registration moment for 现场单 (a
same-day walk-in is examined at registration, so the moment stands in — and for a
walk-in the registration moment IS the row's `create_time` instant) — and SHALL never
present `create_time` as a booked slot's exam time or as audit metadata on the card
(the wide table's 创建时间 column keeps the audit value).

#### Scenario: The anchor never shows the audit clock

- **WHEN** a compact card renders its time anchor for either entry category
- **THEN** the value shown is the slot datetime for a pre-registration and the
  registration moment (当天 + 时分) for a walk-in, both formatted as the full 年月日时分
- **AND** the raw `create_time` clock appears as audit metadata only in the wide
  table's 创建时间 column; on the compact card `create_time` surfaces only as the
  walk-in's registration-moment anchor, never labeled as a creation/audit time

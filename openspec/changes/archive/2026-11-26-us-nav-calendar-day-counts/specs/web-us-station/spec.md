# web-us-station Delta

## MODIFIED Requirements

### Requirement: Registration Page Shell

The US workstation SHALL keep the 登记 page at route `us/registration` (component key
`us/registration/index`), reachable when the US workstation W node is activated and the user
holds `us:order:list`; page affordances SHALL gate on `us:order:{create,update,cancel}` and
hide without them. The page SHALL follow the workstation visual conventions already
established by the console shell kit, and SHALL render as the TWO-STATE workbench (拍板
01-B, the prototype's 壹 section): an edge toggle switches between the COMPACT state (the
392px 今日登记单 rail beside the registration workbench form) and the WIDE-LIST query state
(the full-width table with the wide query row). The registration drawer retires. The
compact state's date navigation (‹ date ›) opens a compact month popover (the 2026-11-26
in-session addition): month nav, day cells with the today ring and the selected fill —
and each day cell SHALL carry the day's registration count as a small badge (count > 0
renders the number in the primary tint under the day number; days without orders carry
no badge). The counts aggregate CLIENT-SIDE over the existing per-date page read
(parallel prefetch of the displayed month's days, `pageSize: 1`, reading the `total` —
the dedicated day-level range endpoint stays a future optimization, the booking-dialog
deferral precedent); a refetch runs when the displayed month changes and the badge
never blocks the day pick.

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

#### Scenario: The datenav popover calendar carries per-day counts

- **WHEN** the operator opens the date popover and the displayed month's prefetch
  answers (any day carrying orders)
- **THEN** that day's cell shows the count badge in the primary tint under the day
  number; days with no orders carry no badge; picking a day still jumps the rail and
  closes the popover unchanged

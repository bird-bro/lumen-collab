# Proposal

## Why

The compact rail card was rebuilt through a one-day ruling chain (2026-11-26) that
landed AFTER the `us-order-exam-room-web` archive swept the earlier card skeleton into
`web-us-station`. The main spec's Order List scenarios still carry two retired facts
(the 现场/预约 category chip, the 预登记时间 reading) and none of the post-archive
anatomy rulings (the state stripe, the single 检查时间 anchor semantics, the
click-to-expand items row, the emergency red frame, the cancel ×-circle glyph, the
icon-color discipline). The approved prototype `docs/prototypes/us-rail-card-v2.html`
(variant A, user-picked and visually confirmed against the real backend) is the UI
authority; this change records it as spec truth so future sessions implement from the
spec, not from chat archaeology.

## What Changes

- **Order List card anatomy (MODIFIED)**: the compact card drops the 现场/预约 category
  chip (the fact stays server-stamped on `registrationType`; the wide table keeps the
  tag) and reads ONE time anchor — 检查时间 (the booked slot for 预约单, the
  registration moment for 现场单; the wide table's 创建时间 column keeps the audit
  value). The visit source becomes plain tertiary text in the head lead behind a dot
  separator (no chip chrome).
- **Card anatomy v2 (ADDED)**: a 4px left stripe rides the dict-1031 tag hue (已取消 =
  diagonal hatch + 0.55 dim); a hairline separates the identity band from the body; the
  time band reads clock glyph + full 年月日时分 + the room pin glyph, with ONLY the
  status chip on the right edge; the items line defaults to ONE line behind a
  click-to-expand chevron (multi-item cards only, no hover tooltip); the ids line is
  icon-prefixed (idcard 就诊卡号 / barcode 检查流水号) with the RED emergency glyph at
  its right edge; live emergency cards take a red border (cancelled cards never alarm).
- **Visual discipline (ADDED)**: every card icon is tertiary gray with no tint — color
  belongs to the status chip, the 急 glyph, and the cancel action; the 20px name is the
  card's only bold text.
- **Cancel glyph (ADDED to the shared chrome)**: `ACTION_ICONS.cancel =
  CloseCircleOutlined` — a cancel is a state transition, not a deletion, so 单据取消
  verbs drop the trash glyph (`remove` stays 删除-only).

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `web-us-station`: the Order List scenarios gain the v2 card anatomy, the single
  检查时间 semantics, and the visual discipline; the shared ConsoleAction chrome gains
  the cancel glyph contract.

### Removed Capabilities

- (none)

## Impact

- `lumen-web/src/pages/us/registration/index.tsx` — the compact card (already
  implemented, committed through the 2026-11-26 sessions)
- `lumen-web/src/pages/system/console-shell.tsx` — the `cancel` ACTION_ICONS entry
  (already implemented; the archived sweep shipped the call site first)
- `docs/prototypes/us-rail-card-v2.html` — the approved UI authority (committed)
- No backend, contract, or data-model change; no migration. Spec-only reconciliation
  plus the test alignment already landed with the workbench commits.

# Proposal: us-order-exam-room-web

> Cross-stack feature `us-order-exam-room` — frontend half. The backend half is
> `us-order-exam-room-sev`; shared decisions live in
> `openspec/coordination/us-order-exam-room.md`.

## Why

The backend (us-order-exam-room-sev) makes the exam room a required create field and
exposes the station's room directory; the workbench must collect it at BOTH entry
points — the booking dialog stamps 预登记单 with a room chosen at booking time (user
ruling 2026-11-26: 预约=登记, room chosen at booking, no arrival re-confirmation), and
the walk-in path picks the room in the 检查信息 section — and the list must show the
assignment, or the field would be untrustworthy at the desk.

## What Changes

- New slim rooms read in the US client (`listStationRooms()` →
  `GET /api/v1/us/rooms`): the station-scoped `{id, name}` rows; no org/dep ids ever
  cross the wire (the backend scopes server-side). Load failure = empty list + the
  failure message — the desk is honestly blocked (submission is refused without a
  room anyway).
- The 检查信息 section (section 3) gains a REQUIRED 检查室 Select as the left
  column's top row (one line above the picked-items table, the standard 32px control
  height — no small density): required at submit (检查室不能为空 refused
  client-side), defaulting to the workstation's last choice (localStorage
  `us-last-room`, the affordance behind the 必选默认上次 ruling — the SERVER still
  validates).
- The 选择预约时段 dialog gains a REQUIRED 检查室 Select in the footer row (left of
  取消/确定): 确定 stays disabled until a slot AND a room are picked; the choice
  also defaults to the last choice and rides the same create body's `roomId`.
- The order list shows the assignment: the compact card carries the room inside an
  existing meta line (the four-row card-flow ruling — no fifth row, no horizontal
  scroll), the wide table gains a 检查室 column; a null room (legacy rows) renders
  '—'.
- Stacks on `registration-order-status-dict-web` (the page's post-dict chip/state
  state is this change's baseline).

## Capabilities

### New Capabilities

(none — this change modifies the existing web-us-station capability only)

### Modified Capabilities

- `web-us-station`: the Order Form gains the required room select; the Booking Slot
  Dialog gains the room pick in its confirm step; the Order List renders the room.

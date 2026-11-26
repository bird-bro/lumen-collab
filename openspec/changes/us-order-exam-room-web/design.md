# Design: us-order-exam-room-web

> Shared decisions live in `openspec/coordination/us-order-exam-room.md`. This file
> records the frontend-side how.

## D1 — The station rooms client

`listStationRooms()` joins `src/api/us-orders.ts` (the US station surface file): plain
GET `/api/v1/us/rooms` → `{id: number, name: string}[]`. No query params — the scope
is server-side (the session has no org/dep ids and must never need them). Load
failure → empty list + a station message (检查室目录加载失败); the desk stays blocked
because the submit rule independently refuses a roomless body — the two failures
compose honestly instead of one masking the other.

## D2 — Section-3 room select (the walk-in pick)

Placement: the 检查信息 section's LEFT COLUMN top row, one line above the picked-items
table — "先定在哪个房间做，再挑项目"; the green summary bar stays a pure summary (no
input controls inside it). One inline row: the label 检查室 + the Select at the
standard 32px control height (no small density — the form-control-height-parity
ruling). Options sorted as returned (id order). The REQUIRED rule attaches to submit:
检查室不能为空 refused client-side; the server's refusal surfaces verbatim like every
other A0400/业务 refusal.

Last-choice memory: localStorage key `us-last-room`, written on every successful
submit (both entry points share it), read as the initial value of BOTH pickers. A
persisted id no longer in the directory (room deleted elsewhere) silently drops — the
Select falls back to empty and the required rule applies.

## D3 — Booking dialog room select (the pre-registration pick)

Footer row, left of 取消/确定: the same option source and the same last-choice
default. 确定 gates on `dlgSlot && roomId` (the existing dlgSlot gate pattern — one
more condition, not a new mechanism). Clearing the booking does NOT clear the room —
the room is a per-order decision the operator re-picks at the walk-in path anyway;
silently reusing it for a different order kind invites wrong stamps.

## D4 — List display: trust requires visibility

Compact card: the room rides an EXISTING meta line (appended to the 登记时间/类别
line — no fifth row; the card-flow ruling forbids extra rows and any horizontal
overflow). Wide table: a 检查室 column after 检查项目 summary. Both render
`roomName ?? '—'` (legacy rows / dangling ids). Ellipsis + title on the wide cell
(room names are long); no font shrinking.

## D5 — Stacking

Builds on `registration-order-status-dict-web` (applied in the working tree): the page
already resolves chips/tags through the 1031 dict bridge and the BOOKED vocabulary is
gone. This change touches the same file — apply AFTER that pair's archive (or at
least after its web verify passes) to avoid a two-writer collision on
`us/registration/index.tsx`.

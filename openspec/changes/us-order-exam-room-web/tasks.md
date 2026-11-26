# Tasks

> Stacks AFTER registration-order-status-dict-web (the post-dict page is the
> baseline — see design D5).

## 1. Station rooms client (TDD)

- [ ] 1.1 Client test FIRST: `listStationRooms()` GETs `/api/v1/us/rooms` and returns
      the `{id, name}` rows — red
- [ ] 1.2 Implement the client in `src/api/us-orders.ts` — green

## 2. Section-3 room select (TDD)

- [ ] 2.1 Workbench test FIRST: submitting without a room refuses client-side
      (检查室不能为空, nothing posts); with a room the payload carries `roomId`; the
      last choice (localStorage `us-last-room`) pre-selects on a fresh form and is
      written back on success — red
- [ ] 2.2 Implement the section-3 top-row Select (options from the client; the
      required rule; the default/remember wiring; load failure → empty list + the
      message) — green

## 3. Booking dialog room pick (TDD)

- [ ] 3.1 Dialog test FIRST: 确定 disabled until slot AND room; confirming composes
      the booking with `roomId`; clearing the booking keeps the room choice — red
- [ ] 3.2 Implement the footer Select (same source + last-choice default; the
      `dlgSlot && roomId` gate) — green

## 4. List display (TDD)

- [ ] 4.1 List test FIRST: the compact card's meta line shows `roomName`, the wide
      table shows the 检查室 column, and a null room renders '—' — red
- [ ] 4.2 Implement both renders (card meta line + wide column with ellipsis/title) —
      green

## 5. Change-scoped regression

- [ ] 5.1 Run the page/unit suite once green — verify: `pnpm test` exit 0

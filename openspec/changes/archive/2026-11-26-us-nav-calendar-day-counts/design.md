# Design: us-nav-calendar-day-counts

## D1 — Data: the parallel per-date prefetch (no new contract)

One `pageOrders` call per displayed month day: `{applyTimeStart: <day> 00:00:00,
applyTimeEnd: <day> 23:59:59, pageSize: 1, pageNum: 1}` — the response's `total` IS
the day's count (the wide conditions are absent; the range is the only predicate).
Runs in parallel (Promise.allSettled — a failed day answers no badge, never a
partial-month abort) when the popover opens and when `navMonth` changes. A dedicated
day-level range endpoint stays the recorded future optimization (the booking-dialog
deferral precedent; the two badges count DIFFERENT things — orders vs slot remaining —
so no shared endpoint falls out of this).

## D2 — Render: the booking-dialog badge family

Cell height 26 → 34 (the badge line under the day number, mirroring the booking
dialog's 40px 余 N cells but tighter); the badge is `fontSize: 10, colorPrimary`
when count > 0, absent otherwise. The selected/today fills and the pick behavior are
untouched. Prefetch fires ONLY while the popover is open or opening (the state lives
on `navMonth`); the rail's own loads are unaffected.

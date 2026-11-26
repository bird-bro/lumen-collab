# Tasks

## 1. Badge loader + render (TDD)

- [x] 1.1 Page test FIRST: opening the date popover shows the count badge on a day
      whose prefetch answers total > 0 (the per-day call asserts its range bounds);
      days with total 0 carry no badge; picking the day still jumps the rail — red
- [x] 1.2 Implement the loader (Promise.allSettled over the displayed month's days,
      pageSize 1, total-as-count) + the badge render (26 → 34 cells, primary-tint
      count) — green

## 2. Change-scoped regression

- [x] 2.1 `pnpm test` exit 0 (the full page suite stays green)

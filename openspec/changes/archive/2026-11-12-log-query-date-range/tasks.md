# Tasks — log-query-date-range

## 1. Backend (lumen-sev — backend-first)

- [x] 1.1 TDD red: `AuthLogQueryTests` range scenarios — two-month span
      (bounds + `date_ym` month-key assertion), single bounds, unparseable /
      inverted range `A0400`, `uid`/`dateYm` regression. Green: new
      `ILogInfoService` overload
      `getPage(pageNum, pageSize, type, uid, account, beginInclusive, endExclusive)`
      + impl with month-key derivation (design D2/D4). Verify: module tests.
- [x] 1.2 `LogController.list` gains `beginDate`/`endDate` String params with
      strict `yyyy-MM-dd` parse + `A0400` validation, rewired to the new
      overload; old service overloads untouched. Verify: full `./mvnw test -q`
      exit 0.

## 2. Frontend (lumen-web — only after backend user-verified)

- [x] 2.1 `pnpm add dayjs`. TDD red: `logs-page.test.tsx` — no 操作人ID/月份
      inputs, 日期段 RangePicker present, wire call sends `beginDate`/`endDate`
      (`yyyy-MM-dd`) + page reset, preset click fills both bounds, clear sends
      neither. Green: page filter-bar rework per design D5/D6 (existing-page
      edit — no prototype; user confirms visuals on the real backend).
- [x] 2.2 Full `pnpm test` + `pnpm build` exit 0.

## 3. Close-out

- [x] 3.1 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md.

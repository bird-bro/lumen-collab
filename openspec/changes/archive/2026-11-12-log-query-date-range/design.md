# Design — log-query-date-range

Decisions below are user-ratified 2026-11-11 (session); the single-change simple
proposal form was chosen over the two-change cross-stack ceremony.

| # | Decision |
|:--|:--|
| D1 | **API additive**: new optional `beginDate`/`endDate`; `uid`/`dateYm` retained. Service gets a NEW `ILogInfoService` overload — existing overloads stay byte-identical (never-modify rule). Controller params extend the existing `list` method (rewire precedent: audit-log-unify-api task 3.2). |
| D2 | **Range semantics**: `yyyy-MM-dd`, each bound optional, AND-combined; double-closed day bounds — `action_time >= begin 00:00:00 AND action_time < end+1d 00:00:00`. Parse with `DateTimeFormatter.ISO_LOCAL_DATE` (strict `yyyy-MM-dd`; no lenient locale parsing); failures → `A0400`. |
| D3 | **Validation**: unparseable date or `beginDate > endDate` → `A0400` (same code as the non-integer-`type` precedent). |
| D4 | **Partition pruning**: derive the spanned month keys (`date_ym` IN) from the bounds, plus the `action_time` range. A bound-less side derives from/to the current month (rows are written with today's `date_ym`, so no future months exist); skip the IN-constraint when the span exceeds 12 months — the `action_time` filter alone stays correct. |
| D5 | **UI**: filter bar 类别 / 账号 / 日期段 (RangePicker, `allowClear`, presets 今日 [today,today] / 近7天 [today-6,today] / 近30天 [today-29,today] / 本月 [month-start,today]); uid + 月份 inputs removed from the page only (API keeps them per D1). `dayjs` becomes an explicit dependency (pnpm strict resolution). Backend-first gate: web implementation runs only after the api tasks are user-verified against the real backend. |
| D6 | **Column order**: the delta spec adopts the user's in-flight reorder (日期, 操作人, 类别, 标题, 描述, IP, 操作系统, 浏览器, 方法, URL) so archive-sync matches the page as edited 2026-11-11. |

## Notes

- The 类别 dropdown label bug (options used `title` where rc-select needs
  `label` → dropdown showed raw codes) was fixed as an in-spec bug fix in
  `lumen-web` on 2026-11-11 (`logs/index.tsx` options mapping +
  `logs-page.test.tsx` portal-scoped assertion, 8/8 green). The web delta's
  "Dict values render in the 类别 dropdown" scenario guards it going forward.
- `LogItem`/row shape untouched: the server row keeps `dateYm` (partition key)
  — only the QUERY parameters grow.
- Tests: sev — extend `AuthLogQueryTests` (red first): span-two-months range,
  single bounds, A0400 cases, uid/dateYm regression; web — `logs-page.test.tsx`
  (red first): filter-bar shape without uid/月份 inputs, wire call with
  `beginDate`/`endDate`, preset click populates both bounds, clear removes them.

# Tasks: web-us-station-redesign

> House frontend flow: build against the REAL `/api/v1/**` backend (no mock layer — user
> ruling 2026-11-05), the prototype is the visual contract, TDD via vitest page/DOM tests.
> The console drawer/table invariants and the antd Select/Popconfirm protocols apply.

## 1. API layer + two-state shell

- [x] 1.1 `api/us-orders.ts` widened: the create body + bookingTime, the page query + the
      wide params (status array, patientId, patientSource, cardNo, examItemName,
      applyTimeStart/End), the view + bookingTime, `statusCounts(params)`
- [x] 1.2 RED: the workbench shell renders the COMPACT state by default (rail + form) and
      the edge toggle swaps to the WIDE state (full-width table + query row) — one route,
      no navigation loss; the compact state carries no query row; the datenav hides in the
      wide state
- [x] 1.3 GREEN: the page root lift (filters/list/autorefresh shared) + the two-state shell
      per the prototype's 壹 section

## 2. Compact state — rail + form

- [x] 2.1 RED: the rail renders the day's orders as dense rows (patient composite two-line +
      state tag + row actions 登记/取消) under the shared list data; the autorefresh mini
      switch drives one shared interval
- [x] 2.2 RED: the status chips render the zero-filled counts from statusCounts, support
      multi-select toggles (default 预登记+已登记), and narrow the list through the IN-filter
- [x] 2.3 RED: the top resolve bar drives the resolution flow — exact id_no fills section 1;
      a soft match opens the 就诊人候选 dialog (RCP card list); no match opens a new archive
      (no 急诊占位 client switch — the empty-name rule)
- [x] 2.4 RED: section 1 患者信息 renders the 3×3 grid with the ruled merges (证件/身高体重/
      住院床号/妊娠 merge; 性别/年龄 do not; the pregnancy cell hides unless gender=female,
      未孕/未知 disables the weeks)
- [x] 2.5 RED: section 2 请检信息 renders the 急诊 Switch in the section title row, the
      预登记时间 picker, 临床诊断 → ext.note
- [x] 2.6 RED: section 3 检查信息 renders the common-item chips + the picked list + the
      添加项目 dialog over the setting catalog; the picked refs carry their modality tokens
- [x] 2.7 GREEN: the form composition + submit — walk-in submit registers (REGISTERED), the
      pre-registration picker posts bookingTime (BOOKED), the four backend refusals surface
      verbatim near the picker, success clears the form + refreshes the list
- [x] 2.8 GREEN: 登记 (arrival confirmation on BOOKED rows) + 取消 (reason + confirmation;
      verbatim refusals) on both states' rows

## 3. Wide state — query row + table

- [x] 3.1 RED: the wide query row renders the primary five conditions (uniform 150px,
      atomic label+control) with 更多条件 collapsing the extras (patientId/patientSource/
      cardNo/examItemName/applyTime range)
- [x] 3.2 RED: filling conditions queries the page endpoint with the composed parameters;
      the results table renders accession/identity/items/state tags/急诊/预登记时间
- [x] 3.3 GREEN: the wide table + pagination per the prototype's 壹 wide section

## 4. 我的查询 + column settings

- [x] 4.1 RED: the saved-search bar saves the current conditions (named), applies a saved
      search, sets the page default (the star full-replace via the set-default endpoint),
      and deletes; the default auto-applies on mount after the first load
- [x] 4.2 RED: column settings persist to localStorage (key `us-registration.columns`) and
      survive reload
- [x] 4.3 GREEN: `api/search-filters.ts` bindings + the bar composition

## 5. 预约设置 console page

- [x] 5.1 RED: the page lists the live policies (模态/粒度/容量/窗口 pairs) from the real
      backend under `setting:slot:list`; affordances hide without the matching
      setting:slot:{create,update,delete} perm
- [x] 5.2 RED: create/edit drawer — the dict-1024 modality SELECT (id-anchored, disabled on
      edit with the stored token), granularity/capacity defaults 30/4, the windows
      start/end pair editor (empty = the fail-closed hint per ConsoleFormHint)
- [x] 5.3 RED: delete with confirmation (soft) + the duplicate-token A0400 surfaces verbatim
      keeping the drawer open
- [x] 5.4 GREEN: `api/us-slot-configs.ts` + `pages/setting/exam-slot/index.tsx` per the
      console CRUD-page precedents

## 6. Verification

- [x] 6.1 `pnpm build` (tsc + vite) exit 0 + the full vitest run green (the antd
      Select/Popconfirm protocols; isolated rerun per the flake protocol)
- [x] 6.2 `/opsx:verify` three-layer credential — verify.md overall = PASS for the
      batch-archive gate
- [x] 6.3 Coordination doc registry updated (web-us-station-redesign → verified); the
      batch-archive gate preflights ①②③ credentials

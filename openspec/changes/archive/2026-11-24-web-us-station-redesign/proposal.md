# Proposal: web-us-station-redesign

> Cross-stack parent: `openspec/coordination/us-registration-redesign.md` (proposal ③ of ③;
> UI authority `docs/prototypes/us-registration-redesign.html` — 拍板 01-07 settled
> 2026-11-20~23, the prototype is the visual contract). Consumes ① (kernel pre-registration,
> verify PASS) and ② (us-registration-redesign-api, verify PASS 2026-11-23): every screen
> builds against the REAL backend — no mock layer (user ruling 2026-11-05).

## Why

The current `/us/registration` page is unusable as a registration-desk workbench (no list
filters beyond three conditions, cramped 560px drawer, the lookup bar detached from the
form) and knows nothing of the pre-registration substrate the backend now ships: the
derived entry status, the slot capacity, the wide query conditions, the status meter, or
the saved searches. The 拍板 01-07 redesign replaces it with the two-state EIS-form
workbench and adds the 预约设置 console page — the last leg of the feature, which also
lights up the V51-seeded 预约设置 menu leaf and satisfies the batch-archive gate.

## What Changes

- **Two-state workbench rewrite (拍板 01-B/02-B)** — `us/registration/index` rebuilt to the
  prototype's EIS form: ONE route with an edge toggle between the compact state (392px
  今日登记单 rail + the registration workbench form) and the wide-list query state
  (full-width table + the RCP∪EIS query row). The registration drawer retires; submit
  = register (the create derives REGISTERED; no 登记 step on new orders — the ②
  transitional break lands here as the designed end state).
- **Pre-registration field (拍板 02-B/06-A)** — 请检信息 gains the 预登记时间 picker; a
  picked future time posts `bookingTime` (yyyy-MM-dd HH:mm:ss) and the order enters 预登记;
  the backend's four A0400 refusals surface verbatim. The list's 登记 action remains the
  arrival confirmation for pre-registered (BOOKED) orders.
- **Wide query + status meter (拍板 03-B)** — the wide state queries ②'s full parameter set
  (status multi-select via the status chips, accession/patient-name/patient-id/
  patient-source/card-no/exam-item name/apply-time range, the 更多条件 toggle, uniform
  150px controls); the status chips carry the `/orders/status-counts` meter and
  multi-select-toggle semantics (default 预登记+已登记 per the 拍板).
- **我的查询 (拍板 07-A)** — saved searches persist backend-side through the
  `/api/v1/system/search-filters` REST (list/create/set-default/delete, uid-owned);
  column settings stay localStorage (the EIS useHeaderColumn pattern, frontend-only).
- **预约设置 console page (拍板 06-A)** — `setting/exam-slot/index` under the 检查配置
  family: the per-modality policy CRUD against `/api/v1/us/slot-configs` (list/create/
  update/soft-delete; token immutable; windows as structured {start,end} pairs) behind
  the `setting:slot:*` perms — the V51-seeded menu leaf lights up.
- **API layer** — `us-orders.ts` widens (bookingTime, the wide query params, the counts
  endpoint, the view's bookingTime); new `us-slot-configs.ts` + `search-filters.ts`
  bindings; no new backend surface (② is complete).

## Capabilities

### Modified Capabilities

- `web-us-station` — the four requirements restated as supersets: Registration Page Shell
  (two states), Patient Resolution Flow (the workbench resolve bar + dialogs), Order Form
  and Actions (the 3-section form + pre-registration + submit=register), Order List (the
  rail/wide table + chips meter + wide conditions).

### New Capabilities

- None — the 预约设置 page rides `web-system-console` (the console pages' spec home, the
  exam-setting pages precedent) as an ADDED requirement; the workstation deltas stay in
  `web-us-station`.

## Impact

- `lumen-web` — `pages/us/registration/index.tsx` rewritten (two-state shell, rail, form,
  dialogs, wide table) + the shared registration components (demographics form reuse);
  `pages/setting/exam-slot/index.tsx` new (the CRUD page); `api/us-orders.ts` widened +
  `api/us-slot-configs.ts` / `api/search-filters.ts` new; vitest page/DOM tests per the
  house harness. Menu/perms come from the backend seeds — zero route/menu code changes
  beyond the component key.
- Backend: NO changes (①② verified; the transitional break from ② resolves to the
  designed end state on ③'s landing).
- Feature gate satisfied: with ③ PASS the batch-archive gate (①+②+③) can run.

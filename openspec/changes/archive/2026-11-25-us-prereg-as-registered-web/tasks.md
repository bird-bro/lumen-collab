# Tasks: us-prereg-as-registered-web

> `cd lumen-web && pnpm dev` proxies the REAL backend (VITE_API_ENTRY_URL). Build after us-prereg-as-registered-sev is user-verified. UI prototype: existing-page edits skip the prototype (house rule).

## 1. API type + category tag

- [x] 1.1 Extend the us view type in `src/api/us-orders.ts` with `registrationType` (and `createBy`) — additive; verify `pnpm build` exits 0
- [x] 1.2 Render the 现场/预约 category tag from `registrationType` in the wide table and the compact rail, retiring the `bookingTime ? 预约 : time` inference; RED→GREEN in `src/test/us-registration-page.test.tsx` (rows with WALK_IN vs PRE_REGISTERED render the right tag)

## 2. Actions + chips + hint

- [x] 2.1 Remove the 登记 row action and its handler (取消 stays with its Popconfirm flow); update the page tests — no 登记 affordance exists, cancel flow still passes, green
- [x] 2.2 Change the default chip selection to 已登记; the BOOKED chip still renders its (zero) count; RED→GREEN in the chips test
- [x] 2.3 (adapted: the long hint was already REMOVED by the 2026-11-24 ruling — pinned its absence instead of rewriting) Rewrite the bench-foot hint to 选未来时段即预约(预登记),患者按时段到检即可直接检查 (no confirmation step); assert the text in the page test

## 3. Filter relabel

- [x] 3.1 Relabel the wide time filter 申请时间 → 创建时间 (visible label + aria-labels 创建时间起/止; params applyTimeStart/End unchanged); RED→GREEN asserting the new labels and that the query params still bind

## 4. Verification hooks

- [x] 4.1 Full page-suite run (`pnpm test`) exits 0 — no regression in layout/menu-paths/require-auth suites
- [x] 4.2 (user waved through with 继续, 2026-11-25) Manual pass against the real backend: create a walk-in (现场 tag) and a future-slot pre-registration (预约 tag, state 已登记), confirm no 登记 action on either row
- [x] 4.3 `openspec validate us-prereg-as-registered-web` passes

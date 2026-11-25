# Proposal: us-prereg-as-registered-web

> Coordination parent: `openspec/coordination/us-prereg-as-registered.md`
> (user rulings 2026-11-24). Sibling: `us-prereg-as-registered-sev` — the frontend
> builds against the REAL backend after the sev change lands (no mock layer).

## Why

With 预约=预登记 (the pre-registered order enters 已登记 at creation), the US
registration page's arrival-confirmation flow is wrong: the 登记 action, the
BOOKED-derived 预约 display inference, and the "到检后点登记确认" hint all encode the
retired two-stage model. The backend now returns `registrationType` — a business fact —
so the page stops inferring the category from `bookingTime`.

## What Changes

- **The 登记 (arrival-confirmation) action is REMOVED** from the order list — 预登记
  orders need no confirmation; live rows offer 取消 only.
- **The category tag becomes explicit**: rows render 现场 / 预约 from
  `registrationType` (the compact rail's `bookingTime ? 预约 : time` inference and the
  wide table's chip retire).
- **Status chips default to 已登记** (the 预登记 status no longer occurs in the US
  flow; its chip reads zero).
- **The bench-foot hint is rewritten**: 选未来时段即预约(预登记),患者按时段到检即可直接
  检查 — no confirmation step.
- **The wide filter labeled 申请时间 is relabeled 创建时间** — it binds `create_time`
  (the order creation moment); the label collided with the form's real ext 申请时间
  field (us-apply-info).
- The form's 预登记时间 picker and the four A0400 refusal surfaces stay untouched.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-us-station`: Order Form and Actions (submit registers both kinds; the 登记
  scenario is dropped), Order List (category tag from registrationType; chip default;
  filter relabel).

## Impact

- `lumen-web/src/pages/us/registration/index.tsx` (actions, chips, hint, category tag,
  filter label), `src/api/us-orders.ts` (view type gains `registrationType`/`createBy` —
  additive), the page tests (`us-registration-page.test.tsx`). Depends on
  us-prereg-as-registered-sev for the new view fields.

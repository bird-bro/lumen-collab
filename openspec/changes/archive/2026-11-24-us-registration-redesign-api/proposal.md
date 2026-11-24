# Proposal: us-registration-redesign-api

> Cross-stack parent: `openspec/coordination/us-registration-redesign.md` (proposal ② of ③;
> UI authority `docs/prototypes/us-registration-redesign.html`, 拍板 01-07 settled).
> Kernel-first: consumes kernel-pre-registration (① verify PASS 2026-11-23). All frontend
> work belongs to proposal ③ (`web-us-station-redesign`) — this proposal carries the ENTIRE
> backend contract surface of the feature (user ruling 2026-11-23), so ③ builds against the
> real API with no mock layer.

## Why

The kernel now owns the pre-registration substrate (①: `booking_time` slot anchor, derived
entry-status policy, per-modality slot capacity validation), but nothing can reach it. The US
create surface fixes every entry to `BOOKED` (`RegistrationOrderServiceImpl` — "entry state
FIXED BOOKED"), the list query exposes only three conditions (status/accessionNo/patientName),
no status counts, no saved searches, and the slot-capacity policy has no admin surface. The
redesigned two-state workbench (拍板 01-07) needs this entire backend contract before its
pages can build against the real backend (后端先行、禁 mock — user ruling 2026-11-05).

## What Changes

- **US V4 vendoring** — the kernel V2 pre-registration DDL (`exam_order.booking_time` +
  `exam_slot_config`) copies verbatim into the us ledger as V4 (the V1 vendoring precedent).
- **Create contract derives entry status (拍板 02-B)** — `POST /api/v1/us/orders` gains the
  optional `bookingTime`; the create composes the kernel pieces in ONE transaction (patient
  port resolve stays OUTSIDE the transaction — kernel design D8): null → `REGISTERED`
  (提交即登记), strictly-future → capacity-guarded `BOOKED` (预登记: config row FOR UPDATE →
  resolveSlotWindow → caller count → validatePreregistration → derive). Kernel A0400 refusals
  (该时段已满 / 不在开台时间窗内 / 预登记时间不能早于当前时间 / 该检查类型未开通预登记) surface
  verbatim. `RegistrationCreateResult` carries the derived `status` + `bookingTime`; the view
  carries `bookingTime`. The legacy FIXED-BOOKED entry retires — the transitional break on the
  old page (提交立即 REGISTERED; its 登记 button A0400s on new orders until ③ lands) is
  ACCEPTED (user ruling 2026-11-23).
- **Wide-table query (拍板 03-B, RCP ∪ EIS union)** — `GET /api/v1/us/orders` gains status
  multi-select, patient-id (jsonb snapshot `patId`), patient-source (snapshot), card-no
  (snapshot — requires the additive jsonb widening `PatientSnapshot.cardNo`, the
  `ExamItemRef.modality` precedent), exam-item name (fuzzy over `exam_items` jsonb), and
  apply-time range (= `create_time`). Plus a **status-count endpoint** (EIS
  ReportStatusCheckbox semantics — per-status counts under the same filters minus status).
- **预约设置 CRUD** — `lumen-us` exposes `/api/v1/us/slot-configs` (list / create / update /
  soft-delete; NO enable column — the ①-fixed DDL shape). The config rows live in the **us
  schema** (① D3 department ownership; D7's FOR UPDATE serialization requires the config row
  in the same schema as the orders), so the CRUD is served by the schema owner. Perms family
  `setting:slot:*` (the console page belongs to the 检查配置 family, proposal ③).
- **我的查询 (拍板 07-A)** — generic `search_filter` table in the system schema (uid +
  page_key + name + query jsonb + default flag) + REST `/api/v1/system/search-filters`
  (authenticated-only, uid-owned rows), reusable by every workbench list; column settings
  stay localStorage (③ frontend-only).
- **Menu seed (system V50)** — the 预约设置 C-page leaf (`setting/exam-slot/index`) + F
  buttons (`setting:slot:add/edit/delete`) hang under the EXISTING L2 「检查配置」 directory.
  NO directory rename: the coordination note's 检查设置→检查配置 rename already shipped with
  menu-settings-root (2026-11-13, current main spec) — this migration is additive-only.

## Capabilities

### Modified Capabilities

- `us-station` — Registration Surface: the create derives the entry status from
  `booking_time` (FIXED BOOKED retired; every existing scenario preserved as a superset) +
  ADDED requirements: Workbench Query Surface (wide conditions + status counts) and Slot
  Config Administration (vendored storage + CRUD + perms).
- `system-data` — Exam Setting Menu Pages: the 检查配置 group gains the 预约设置 leaf + button
  perms (existing scenarios preserved); ADDED Search Filter Table (system schema).
- `api` — ADDED Saved Search Filter Endpoints (generic authenticated uid-owned REST).

### New Capabilities

- None — the deltas ride the established capability homes (US surface in `us-station`,
  system tables/menus in `system-data`, generic REST contract in `api`), matching the
  us-station-registration-sev and menu-settings-root precedents.

## Impact

- `lumen-us` — V4 vendoring; `RegistrationCreateRequest`/`Result`/`RegistrationOrderView`
  (+`bookingTime`); `RegistrationOrderServiceImpl` composed pre-registration flow
  (TransactionTemplate around capacity dance + `registerOrder`; port resolve stays outside);
  `RegistrationOrderPageQuery` (+multi-status + jsonb conditions); status-count endpoint;
  `SlotConfig` CRUD (controller/service/mapper/dto, `ExamSlotConfigMapper` reuse); US e2e +
  unit tests.
- `lumen-core` — additive jsonb widening only: `PatientSnapshot` gains `cardNo` (additive
  key; old rows read null; the ExamItemRef.modality precedent — no kernel behavior change).
- `lumen-system` — V50 (`search_filter` table + 预约设置 menu leaf/buttons); search-filter
  domain (package `org.birdbro.lumen.search` hosted in the system jar, URL
  `/api/v1/system/search-filters`); seed tests.
- Consumers: proposal ③ builds every page against these real endpoints. Radiology/endo
  reuse the kernel capability by vendoring when their workstations are built (not this
  change).
- No changes to `lumen-feign` (no new inter-service contract: search filters are
  system-owned REST; slot config is us-owned REST) and no changes to the kernel services
  (① already shipped the policy/capacity machinery ② composes).

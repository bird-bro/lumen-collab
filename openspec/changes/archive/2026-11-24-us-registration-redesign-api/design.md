# Design: us-registration-redesign-api

> Parent coordination: `openspec/coordination/us-registration-redesign.md` (拍板 01-07
> settled); UI authority `docs/prototypes/us-registration-redesign.html` (proposal ③ renders
> it). Consumes kernel-pre-registration (①) verbatim — no kernel behavior changes.

## Context

Current lumen-us: V1 (vendored kernel registration DDL) / V2 (us_registration_ext) / V3 (US
menu seed) ledger; `RegistrationOrderServiceImpl.create` fixes EVERY entry to `BOOKED` (the
booking surface), the page query exposes status/accessionNo/patientName only, and the
registration controller carries five endpoints. The kernel ① shipped `booking_time` +
`ExamSlotConfig` + `PreRegistrationPolicy` + `ExamSlotCapacityService` (verify PASS
2026-11-23) but no US code touches them yet. System ledger head V49 → next numbers V50+.

## Goals / Non-Goals

**Goals**
- The US create composes ①'s pre-registration machinery end-to-end (derive + capacity).
- The wide-list query + status counts serve the redesigned workbench (拍板 03-B).
- The slot capacity config gets its admin CRUD (拍板 06-A) in the schema owner.
- Saved searches get their generic backend (拍板 07-A) in the system schema.
- Menu seeds land the 预约设置 leaf (additive — see D6 correction).

**Non-Goals**
- No web/pages (proposal ③). No radiology/endo adoption.
- No kernel service/state-machine changes (the additive `PatientSnapshot.cardNo` key only).
- No lumen-feign changes (no new inter-service contract).
- No changes to the 孕检 ext block (拍板 05 is UI-side; V2 ext columns already fit).
- No slot entities/reservation (v2 号池).

## Decisions

- **D1 — Composed create: TransactionTemplate around the capacity dance.** The port resolve
  stays OUTSIDE any transaction (kernel design D8 — a remote call must never hold a local
  tx); when the body carries `bookingTime`, the service wraps [policy guard → config row
  FOR UPDATE (kernel `ExamSlotConfigMapper`, wrapper `.last("FOR UPDATE")`) →
  `resolveSlotWindow` → own-schema live count (`ExamOrderMapper` on us) →
  `validatePreregistration` → derive status → `registerOrder`] in ONE `TransactionTemplate`
  execution — `registerOrder`'s REQUIRED tx joins it, so the D7 serialization holds and the
  whole registration rolls back on any refusal. Null `bookingTime` walks the unchanged
  legacy path (derive `REGISTERED`, no config machinery touched). The validation modality is
  the FIRST exam item's token (`CollUtil.getFirst` + trim-to-null — the issuer-scope
  derivation precedent ① D5; a pre-registration create with no token refuses fail-closed
  该检查类型未开通预登记). The caller count scopes the slot window by that same first-item
  modality (`exam_items->0->>'modality' = ?` — US issues single-项目 orders; a CT order must
  never consume a US slot). *Alternative rejected:*
  self-injected `@Transactional` inner method (opaque); doing the dance before
  `registerOrder` without a shared tx (loses the D7 lock + atomicity).

- **D2 — Slot CRUD lives in lumen-us, technically forced.** The config rows live in the us
  schema (① D3); a `system`-side CRUD would need a cross-schema write and the D7 FOR UPDATE
  lock would be unreachable. URL `/api/v1/us/slot-configs` (the schema owner's family);
  perms `setting:slot:*` (the console page sits in the 检查配置 family — perm strings are
  menu-bound, independent of the URL service). Create/update trims the modality token
  (`StrUtil.trim`, the number-rule CRUD precedent) but does NOT re-validate it against
  dict-1024 (the ③ UI picks from the dict; the kernel consumes tokens verbatim). Duplicate
  live modality → A0400 naming the duplicate (the live-unique index backstopped). No enable
  column — edit + soft-delete only (the ①-fixed DDL shape).

- **D3 — Search-filter domain: `org.birdbro.lumen.search` hosted in the system jar.** New
  domain package (the merge-setting-into-system hosted-domain shape): controller/service +
  impl/mapper/entity/dto, URL `/api/v1/system/search-filters`, table in the system schema.
  Authenticated-only, NO perm family (the dict-lookup authenticated-only precedent —
  personal workbench data). Set-default is full-replace: the endpoint clears the user's
  previous default for the same pageKey inside its transaction (the role_menu assign
  full-replace precedent) — the partial unique index is the backstop, not the mechanism.

- **D4 — Wide-query predicates.** `patient_snapshot` jsonb keys: `patId` exact,
  `patientSource` exact, `cardNo` exact, `name` LIKE (existing); `exam_items` fuzzy =
  per-element `EXISTS (SELECT 1 FROM jsonb_array_elements_text(exam_items) e WHERE e->>'itemName' LIKE ...)`
  — no whole-document `::text LIKE` false positives; apply-time range on `create_time`
  (the kernel carries no separate apply timestamp — the registration moment IS the apply
  moment); status multi-select via IN. MyBatis-Plus wrapper `.apply` with positional params
  (the existing patientName `.apply` precedent). US volume (v1) makes dedicated jsonb
  indexes unnecessary — the existing `idx_exam_order_status` carries the selective path.

- **D5 — Status counts: zero-filled, all six statuses.** `GET /api/v1/us/orders/status-counts`
  takes the wide filter set MINUS status, groups by status, and zero-fills every document
  state machine status — the chips always render the full meter.

- **D6 — Menu migration is additive-only (coordination correction).** The L2 「检查配置」
  directory already exists under L1 「设置」 (menu-settings-root shipped the
  检查设置→检查配置 rename + re-grouping on 2026-11-13 — the main spec is authoritative).
  V51 inserts ONLY the 预约设置 C leaf (path `exam-slot`, component `setting/exam-slot/index`,
  perms `setting:slot:list`) + the `slot` F buttons (V15 CONTENT-guard discipline) under it,
  admin auto-granted; the four existing leaves untouched. The coordination doc's rename
  language described an already-shipped migration — no rename rides ②.

- **D7 — `PatientSnapshot.cardNo` additive jsonb widening.** The card-no query condition
  needs the snapshot to carry it; the create body already holds `cardNo` (the resolution
  key) but never stored it. Additive key on the kernel snapshot shape (the
  `ExamItemRef.modality` precedent — old rows read null; writes stay Jackson-serialized).
  `buildSnapshot` sets it from the body. The snapshot shape has no dedicated kernel-spec
  requirement (the aggregate spec says "jsonb 患者快照" generically), so no kernel delta
  rides ② — the us-station create scenario pins the storage.

- **D8 — `bookingTime` payload format.** `yyyy-MM-dd HH:mm:ss` through the house
  `@JsonFormat`/Jackson (the `createTime` EntityBase pattern), parsed to `java.util.Date`;
  the kernel floors any time-of-day onto the granularity grid — the client may send an
  arbitrary in-window time (09:17 → the 09:00–09:30 slot). The response echoes it through
  the same format.

- **D9 — The transitional break is a ruling, not a bug.** ② flips the legacy FIXED-BOOKED
  entry the moment it deploys: old-page submits become REGISTERED, the old 登记 button
  A0400s on new orders (REGISTERED→REGISTERED illegal). Accepted 2026-11-23 (拍板 02-B
  strict; ②③ adjacent). No compatibility mode ships.

- **D10 — Migration numbering.** US ledger: V4 (kernel V2 vendored verbatim). System
  ledger: V50 (`search_filter` table) + V51 (预约设置 leaf + slot F buttons). Both under
  their services' explicit flyway schemas (the never-public ruling).

## Risks / Trade-offs

- [Old US page breaks between ② and ③] → accepted ruling (D9); ②③ land adjacently.
- [jsonb per-element fuzzy cost] → v1 US volume makes it a non-issue; the pattern is the
  same one the wide table already uses for patientName; GIN/expression indexes deferred
  until a real volume signal.
- [search_filter query jsonb untrusted] → stored verbatim, returned only to its owner —
  the shape contract belongs to each workbench (③); size is bounded by the request body
  limit.
- [Slot CRUD + create both touch exam_slot_config] → the CRUD runs OUTSIDE the create
  transaction (admin action); a config edit mid-create just re-reads under the FOR UPDATE
  lock (the ①-documented residual, config edits are rare).

## Migration Plan

1. US V4: the kernel V2 statements copied verbatim (booking_time + exam_slot_config + the
   two indexes) — additive; `booking_time` NULL on all existing rows, zero backfill.
2. System V50/V51: `search_filter` table + the 预约设置 leaf/buttons — additive, both
   CONTENT-guarded/idempotent (the V28/V36 seed discipline).
3. Rollback: drop the column/table/table + soft-delete the menu rows; the US create falls
   back to refuse-the-future (unconfigured fail-closed) — never silently unvalidated.

## Open Questions

- None material — the CRUD URL/perm split (D2), the package home (D3), and the menu
  placement (D6) are the questions ① flagged, now settled against the real spec tree.

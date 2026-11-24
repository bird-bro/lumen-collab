# Coordination: us-registration-redesign

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "us-registration-redesign"`.
> UI authority: `docs/prototypes/us-registration-redesign.html` (104 DOM checks green; all 拍板点 settled 2026-11-20~23).

## Why
The current `/us/registration` page is unusable as a registration-desk workbench (no list filters, cramped 560px drawer,
lookup bar detached from the form). Redesign referencing RCP `/rcp/register/page` + EIS ultrasound register workbench
(`register/check` → RadiationRegisterAndAppointment) landed a two-state workbench: compact registration state (392px rail +
form) ↔ wide-list query state (full-width table + RCP∪EIS query conditions). Pre-registration (预约=预登记, per user:
e.g. prenatal checkups booked to a day+slot) is kernel-level and department-neutral — radiology/endo reuse it, so the
mechanism is a standalone kernel proposal consumed by the US proposal.

## Shared Design
Single source of truth for cross-stack contracts (per-stack proposals reference this file, do not duplicate):

- **Status semantics (拍板 02-B, user 2026-11-23)**: submit = register in one step. Create derives status from
  `booking_time` in one transaction: now/immediate → `REGISTERED` (现场单); future slot → `BOOKED` (预登记态).
  `BOOKED` display label changes 预约 → 预登记; the list 登记 action remains the arrival confirmation for pre-registered
  orders (BOOKED → REGISTERED, unchanged kernel state machine).
- **booking_time**: `exam_order.booking_time timestamp` — new kernel column (kernel vendored DDL source; `booking_no`
  already exists and stays unused for walk-in orders).
- **Slot capacity (拍板 06-A + minimal static capacity)**: per-modality slot config — granularity (default 30 min),
  capacity per slot (default 4), open time windows (e.g. 08:00–12:00 / 14:00–17:00). Pre-registration submit validates
  the slot count against capacity; full slots are refused with a named error code. No slot entities / reservation /
  locking / cross-day schedules (EIS BookingNumber is v2 territory).
- **Wide-table query conditions (拍板 03-B, RCP ∪ EIS union)**: beyond existing status/accessionNo/patientName —
  patient-id (jsonb snapshot), patient-source (snapshot), exam-item name (fuzzy, exam_items jsonb), card-no (snapshot),
  apply-time range, status multi-select; plus a status-count endpoint (EIS ReportStatusCheckbox counts).
- **我的查询 (拍板 07-A)**: saved search filters persisted backend-side — generic `search_filter` table in the system
  schema (uid + page_key + name + query jsonb + default flag) + generic REST, reusable by every workbench list.
  Column settings stay localStorage (EIS useHeaderColumn pattern, frontend-only).
- **UI authority**: prototype — two states via one edge toggle; compact head (datenav + count chips + autorefresh);
  wide head (chips + autorefresh, datenav hidden — time owned by the apply-time range, shared date state);
  form sections 1患者信息 (3×3 with merged cells) / 2请检信息 (emergency switch in section title, pregnancy cell
  gender-conditional, pre-registration time, clinical diagnosis as ext.note) / 3检查信息 (common-item chips + picked
  list + add-dialog); registration drawer retired.

## Decisions
- 拍板 01 A 单页工作台 — wide-list state inside the same route kills the EIS two-page split - 2026-11-20
- 拍板 02 B 提交即登记 — 预约=预登记 semantics; status derived from booking_time in one transaction - 2026-11-23
- 拍板 03 B RCP∪EIS query conditions + status counts — one backend proposal - 2026-11-23
- 拍板 04 A common-item chips + picked list + add-dialog (exam-item catalog `isCommon` + US modality filter) - 2026-11-20
- 拍板 05 A clinical info = pregnancy status/weeks/clinical-diagnosis only (ext.note carries 临床诊断) - 2026-11-20
- 拍板 06 A single-page pre-registration + minimal static capacity; kernel-level, department-neutral - 2026-11-23
- 拍板 07 A 我的查询 backend-persisted (system search_filter); column settings localStorage - 2026-11-23
- Merge-cell rule: value-type adjacent fields merge (证件/身高体重/住院床号/妊娠), interactive ones do not (性别/年龄) - 2026-11-20
- 急诊占位 switch retired: emergency order with empty name = placeholder archive (server judges by name=null) - 2026-11-23
- Pregnancy cell hidden unless gender=female; 未孕/未知 disables weeks - 2026-11-20
- Compact state has NO query row (vertical space belongs to the list); all querying lives in the wide state - 2026-11-23
- Status chips kept (not a dropdown): counts are the desk workload meter; multi-select toggle semantics,
  default 预登记+已登记 - 2026-11-23
- Wide-table query controls: uniform fixed width 150px (selects too), atomic label+control pairs, single flex-wrap
  flow row with 更多条件 toggle (RCP PlusSearch flow) - 2026-11-23
- Slot-capacity config page menu placement (user correction to real-product convention): **系统管理 → 检查配置 → 预约设置**
  — per-modality policy belongs to the exam-config family, not the US workstation; perm family `setting:slot:*`,
  component key `setting/exam-slot/index`; the kernel proposal itself carries NO page (library-only) - 2026-11-23
  RESOLVED + CORRECTED (② recon 2026-11-23): the 检查设置→检查配置 rename ALREADY shipped with menu-settings-root
  (2026-11-13; the L2 「检查配置」 sits under L1 「设置」 per the main spec) — ②'s menu migration is ADDITIVE-ONLY
  (the new leaf 预约设置 hangs under the existing 检查配置 group, no rename migration).

## Change Registry
| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | kernel-pre-registration | verified (2026-11-23; archive deferred to the batch-archive gate) | PASS (11/11 scenarios, L1+L3 green; two environmental incidents isolated-green + recorded) |
| backend | us-registration-redesign-api (② — all backend, user ruling 2026-11-23) | verified (2026-11-23; archive deferred to the batch-archive gate with ①) | PASS (27/27 scenarios, L1+L3 green; W-grant drift + net-drop incidents isolated-green + recorded; one missed repin caught by the reactor and fixed) |
| frontend | web-us-station-redesign (③ — the workbench + 预约设置 page; prototype = UI authority) | verified (2026-11-23) | PASS (23/23 scenarios, L1 build + L3 476/476 green; the batch-archive gate SATISFIED — ①②③ all PASS) |

## Gate
Verify each stack independently. When ALL registered changes have an acceptable verify credential
(`PASS`, or user-confirmed `PASS_WITH_ISSUES`), preflight all credentials and batch-archive them.
Dependency order: kernel-pre-registration first (booking_time DDL + capacity service), then ②∥③
(patient→core→us precedent).

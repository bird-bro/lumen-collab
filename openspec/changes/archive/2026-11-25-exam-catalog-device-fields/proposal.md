# Proposal: exam-catalog-device-fields

> User request 2026-11-25: for the radiology workstation, the registered exam
> order's identity (patId, accession number) and catalog facts (检查部位 / 检查项目)
> go to the modality device via DICOM Worklist (MWL). Some devices do not support
> Chinese, so the catalog's three tables gain DEVICE-FACING fields (Latin/ASCII)
> maintained alongside the Chinese originals. Absorbs the V53 demo-seed todo: the
> seed lands WITH the device values so the radiology demo can exercise MWL on day
> one.

## Why

DICOM MWL maps catalog facts to device-visible attributes: the item's `code`
(US-ABD-01 …) already serves as the Procedure Code Value, but the device shows the
Scheduled Procedure Step Description and Body Part Examined as TEXT — both Chinese
today. Devices without Chinese font/charset support render garbage, so the catalog
must carry Latin/ASCII device fields maintained by the administrators, and the
future MWL composer (lumen-integration) reads them by itemId at send time
(id-anchored — the order flow itself stays untouched).

## What Changes

- **Model (exam-setting, one migration V53 — additive ALTERs + the demo seed)**:
  - `exam_item.device_desc` varchar(128) NULL — the Latin procedure description
    (Scheduled Procedure Step Description / Procedure Code Meaning);
  - `exam_body.device_token` varchar(32) NULL — the DICOM Body Part Examined
    defined term (CHEST / ABDOMEN / LSPINE …, the device-recognized token);
  - `exam_body.device_name` varchar(64) NULL — the Latin body name;
  - `exam_method.device_desc` varchar(128) NULL — the Latin method text
    (the link table `exam_item_method` carries NO text — the method dictionary
    owns it).
  - All nullable: existing real rows keep NULL until an administrator fills them;
    no backfill pressure.
- **Admin UI (the three setting pages)**: 检查项目 / 检查部位 / 检查方法 edit forms
  gain the device-field inputs (and the table/list echo shows them) — the
  CRUD/VO/API payloads carry the fields verbatim.
- **Demo seed (V53 rides the same migration)**: the hand-applied shared-DB catalog
  solidifies — 34 `exam_body` + 46 `exam_item` + 58 `exam_item_method` rows (the
  V52 idiom: explicit ids, `ON CONFLICT (id) DO NOTHING`, `setval` tails) WITH
  drafted Latin device values for every row (I draft, the user reviews the table
  before the seed embeds).
- **Seed test**: `ExamCatalogDemoSeedTests` (the V52 shape) — per-modality live
  counts + `(modality, code)` spot rows + no dangling body refs.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `exam-setting`: Exam Body Columns / Exam Method Columns / Exam Item Columns
  (MODIFIED — each gains its device-facing column(s) in the ruled column set).
- `web-system-console`: ADDED Requirement (Device-Facing Field Maintenance) — the
  three catalog pages expose and persist the device fields in their forms/echoes.

## Impact

- `lumen-sev/lumen-system` (setting domain): V53 migration (ALTERs + seed),
  `ExamItem/ExamBody/ExamMethod` entities + their create/update VOs and page VOs,
  the seed test. The registration/order flow is UNTOUCHED (MWL composition is
  future lumen-integration work; it will read the catalog by itemId).
- `lumen-web`: the three setting pages' forms/tables (`setting/exam-item`,
  `setting/exam-body`, `setting/exam-method`) gain the device-field inputs and
  echoes.
- The shared test DB: applying V53 is a no-op for the catalog rows it already
  carries EXCEPT the device columns (ALTER adds them NULL; the seed's ON CONFLICT
  DO NOTHING does NOT backfill device values onto existing rows — the demo rows'
  device values land via a targeted UPDATE in the same migration, see design D4).
- Out of scope: the MWL composer itself (lumen-integration, future); the patient
  name Latin transliteration (patient domain, separate ruling); the stray DR
  residue row (remark='1').

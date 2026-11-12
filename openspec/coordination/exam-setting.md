# exam-setting — Coordination (Cross-Stack Feature)

> Feature: the console grows the 检查设置 domain — a standalone `lumen-setting`
> service owning the hospital-wide exam catalog (检查项目 / 部位分组 / 检查部位 /
> 检查方法, with billing + performance-score weights) and the report-template
> knowledge base (公共/个人 two scopes). Reference: RCP `/setting/*` pages
> (rcp_web `src/views/setting/**`, backend `birdbro-setting`), re-modeled per the
> rulings below. Origin: user request 2026-11-12 ("你去看下rcp项目中的/setting/*
> ... 看完后我们在头脑风暴的展开设计"), rulings settled same-day in the brainstorm
> (see Decisions).

## Why

The exam workflow kernel (booking → registration → exam → report) has no dictionary
layer yet: opening an order needs exam items, items need body-part grouping, reports
need phrase templates. RCP proves the shape but carries known debts (body/group
single-tree pile-up, comma-joined method ids with denormalized name columns,
oid-scoped platform rows). lumen re-models the domain as a standalone hospital-wide
service so us/endo inherit the same catalog when their workstations land.

## Shared Design

- **New service** `lumen-setting` (package `org.birdbro.lumen.setting`), executable,
  added to the aggregator; nginx entry `/api/v1/setting/**`; no MQ events in v1; no
  `lumen-api/setting-api` Feign contract until a consumer appears (add-only later).
- **New schema `setting`** + its own DB role (GRANTs confined to own schema + the
  public shared tables). Data spec enumeration grows six → seven schemas.
- **Six tables** (serial Integer PK + EntityBase audit tail, house style):
  - `exam_body_group` — 部位分组, flat: modality, name, sort, enable.
  - `exam_body` — 检查部位: modality, group_id, up_id (nullable, in-group anatomy
    level), name, code, sort, enable.
  - `exam_method` — 检查方法: modality, name, code, sort, enable.
  - `exam_item` — 检查项目: modality, exam (name), code, body_id, price,
    rate (折扣), tag (诊断计费数量), score_rep, score_aud (performance-score
    weights, nullable = excluded), is_common, sort, enable, remark.
  - `exam_item_method` — item↔method join (unique exam_id+method_id); item update
    rewrites it in the same transaction.
  - `rep_temp` — report-template knowledge base, one tree: type (1 公共 / 2 个人),
    owner_uid (个人 NOT NULL / 公共 NULL), modality, flag (0 目录 / 1 模板), up_id,
    name, finding, impression.
- **Deliberate deviations from RCP** (design debts not ported):
  - NO denormalized `groupId` on items, NO `methodValue`/`bodyValue` name strings —
    joins inside the same schema; renames never strand stale copies.
  - NO synthetic group root (`BODY_GROUP_UP_ID=100000`): groups are their own table.
  - Modality vocabulary REUSES public dict catalog **1021 检查类型** (its remark
    already designates it "新增模态直接在此维护"; values CT/MR/US/ENDO). The change
    seeds the missing **DR** row (1021005) — radiology is the v1 platform.
- **API surface** (`lumen-setting`, envelope per `specs/api`, verb style per the
  OrgDeviceController precedent — kebab plural resources, `GET /page|/list|/{id}`,
  `POST`, `PUT /{id}`, `DELETE /{id}`):
  - `/api/v1/setting/exam-items` — page (modality/groupId/keyword/is_common filters),
    detail, create, update, delete.
  - `/api/v1/setting/exam-body-groups` — list by modality, create, update, delete.
  - `/api/v1/setting/exam-bodies` — tree by modality (+group), create, update, delete.
  - `/api/v1/setting/exam-methods` — page, list, create, update, delete.
  - `/api/v1/setting/rep-temps` — tree by modality+type, file page, create, update,
    delete (个人 rows: owner-only writes; 公共 rows: perms-gated).
- **Delete guards (service layer)**: group with bodies → refuse; body with items (or
  child bodies) → refuse; method referenced by any item → refuse; all soft-delete.
- **Menu seeds** ride lumen-system migrations (menu_info is system-owned), V15/V27
  CONTENT-guard + setval discipline: L2 group 「检查设置」 under 系统管理 (icon
  medicine-box) with four C pages — 检查项目 `setting:item:list` / 检查部位
  `setting:body:list` (left column manages groups in-page; no separate menu) /
  检查方法 `setting:method:list` / 报告模板 `setting:temp:list` (公共/私有 tabs) —
  plus F button nodes (新建/编辑/删除 ×4) and admin auto-grant.
- **Console pages** (web change): search bar + table + Drawer forms per the existing
  console patterns; UI prototype first, user confirms before implementation.

## Decisions (all 2026-11-12 unless noted)

1. Standalone service `lumen-setting` — NOT inside `lumen-exam-core` (kernel is a
   no-controller library; per-department copies would split the hospital catalog)
   and NOT named `lumen-core` (collides with framework/common semantics). User
   proposed the module itself; name confirmed after `lumen-catalog` comparison.
2. Hospital-wide single catalog + modality column — no campus/department isolation
   dimension (RCP's oid+modality double scoping drops the oid axis; single tenant).
3. 部位/分组 split tables (user picked Option A) — group (business classification,
   flat) vs body (anatomy concept, in-group hierarchy); kills the RCP
   upId=100000 synthetic-root ambiguity.
4. Billing + performance fields stay: price / rate / tag / score_rep / score_aud —
   in-hospital performance scoring needs the score weights (user ruling).
5. Report templates are IN scope (user: "必要功能"); two scopes only (公共/个人),
   `type` column leaves room for a future 科室 scope without a migration.
6. Print templates deferred (empty shell in RCP too; no consumer yet).
7. Item↔platform mapping (项目对照) deferred to the integration domain — when it
   lands it is a campus/HIS-instance-keyed mapping table (one item → N HIS rows),
   NOT columns on exam_item; multi-campus multi-HIS was the driving scenario.
8. Modality vocabulary = the shared 检查类型 dict catalog, resolved BY UNIQUE NAME
   (`uk_dict_catal_name`), never a hardcoded id — the id flip-flopped 1021↔1027 across
   sessions DURING apply (V26 seeded 1021; a V27 draft made 1027; the parallel session
   then settled 1021 with the 与设备类型同值集 set). Value = the modality string, set =
   DR/CT/MR/DSA/US/ENDO; the setting test base idempotently ensures + heals it.
   Follow-up for the owning change: settle the catalog id once and stick to it.
9. 检查部位 menu hosts group management in-page (left column); no separate
   body-group menu. 报告模板 is ONE page with 公共/私有 tabs (RCP split two menus).

## Change Registry

| Change | Stack | Scope |
|:--|:--|:--|
| `exam-setting-api` | backend | lumen-setting module + schema + 6 tables; lumen-system migrations (menu seeds + dict 1021 DR row); endpoints; tests |
| `exam-setting-web` | frontend | 4 console pages under 检查设置 (prototype → confirm → build) |

Gate: verify each stack independently; batch-archive only when both carry verify.md
overall PASS. Frontend starts only after the backend API is user-verified
(frontend-backend-first, no mock layer). Cross-domain ban unchanged: the backend
change owns ALL seeds (menu/dict live in system migrations — same stack).

## Spec Delta Map

- `exam-setting-api`: NEW `exam-setting` (schema + tables + endpoints + guards);
  deltas `service-topology` (module set), `data` (schema enumeration), `system-data`
  (menu pages / button perms / modality vocabulary).
- `exam-setting-web`: delta `web-system-console` (检查设置 group + four pages).

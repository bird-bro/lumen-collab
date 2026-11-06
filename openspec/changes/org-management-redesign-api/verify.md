<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: org-management-redesign-api
  date: 2026-11-06
  overall: PASS_WITH_ISSUES   # PASS | PASS_WITH_ISSUES | FAIL
  stacks:
    backend:
      L1: PASS                 # ./mvnw compile -q exit 0 (JDK 11)
      L2: "26/27"              # implemented/total scenarios
      L3: PASS                 # ./mvnw test -q exit 0; 23 classes / 89 tests / 0 fail / 0 err / 0 skip
---

# 验证结果: org-management-redesign-api

> 生成时间: 2026-11-06 · 总体: **PASS_WITH_ISSUES**
> 受影响 stack: backend (`lumen-sev/`) only - 前端属 org-management-redesign-web,本 change 不涉及。

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` exit 0 (JAVA_HOME=jdk-11) |
| L2 规格对齐 | 26/27 已实现 | 1 PARTIAL(见下),均非源码缺陷 |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit 0 · 23 类 / 89 项 / 0 失败 / 0 错误 / 0 跳过 |

L3 运行在含本 change 全部实现的树上(AuthOrgDepTests 现 6 项、AuthOrgCrudTests 3 项)。
共享测试库处于 V18,`openspec validate --specs` 12 passed / 0 failed。

## 场景对齐详情 — api delta (19/19)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Controlled tree query | ✓ | OrgServiceImpl.tree → OrgTreeService; AuthOrgCrudTests:76-83 (server-derived type=1/level=2) |
| Campus creation | ✓ | OrgServiceImpl:81-90; AuthOrgCrudTests:67-74 |
| Second hospital root rejected | ✓ | OrgServiceImpl:73-77; AuthOrgCrudTests:46-49 (A0400) |
| Campus cannot contain a child org | ✓ | OrgServiceImpl:82-86; AuthOrgCrudTests:51-54 (A0400) |
| Type and parent immutable | ✓ | OrgServiceImpl:108-115; AuthOrgCrudTests:90-97 |
| Organization delete guard | ✓ | OrgServiceImpl:139-157; AuthOrgCrudTests:103-117 |
| Existing list WITHOUT campus filter | ✓ | OrgDepServiceImpl:98-106; AuthOrgDepTests.disabledCampus…(unfiltered `/list` asserts radiology present) |
| Existing list WITH campus filter | ✓ | AuthOrgDepTests:44-54 (seeded radiology/us/endo under default campus) |
| Page returns all campuses | ✓ | OrgDepServiceImpl:109-124; AuthOrgDepTests:129-133 (total=2 across campuses) |
| Page filters a campus | ✓ | AuthOrgDepTests:136-142 |
| Page filters keyword + status | ✓ | AuthOrgDepTests:145-159 |
| Department belongs to a campus | ✓ | OrgDepServiceImpl:52-65 + requireLiveCampus; AuthOrgDepTests:72-73 |
| Hospital owner rejected | ✓ | OrgDepServiceImpl:132-135; AuthOrgDepTests:56-61 (A0400) |
| Disabled campus cannot receive a department | ✓ | OrgDepServiceImpl:140-143; AuthOrgDepTests.disabledCampus… (create→A0400 + reassign→A0400) |
| Department code immutable | ✓ | OrgDepServiceImpl:78-81; AuthOrgDepTests:100-103 |
| Duplicate code within a campus | ✓ | rejectDuplicateCode:147-158; AuthOrgDepTests:75-78 (A0712) |
| Same code across campuses | ✓ | AuthOrgDepTests:65-84 |
| Delete hides the department and keeps the row | ✓ | OrgDepServiceImpl:91-95; AuthOrgDepTests.departmentDelete…(deleted=1, gone from /list + /page) |
| A deleted department's code is reusable | ✓ | AuthOrgDepTests.departmentDelete…(re-create same code after delete) |

## 场景对齐详情 — auth-rbac delta (7/8)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Fresh on-premise deployment | ⚠ PARTIAL | 见"发现的问题" - 迁移逻辑正确,但共享服务器无法重放真正的全新库 |
| Existing departments remain visible | ✓ | AuthSeedMigrationTests:58-70 (总院区/is_default=1); AuthOrgDepTests:49-54 |
| Root proliferation blocked | ✓ | OrgServiceImpl:73-77; AuthOrgCrudTests:46-49 |
| Type and parent cannot be rewritten | ✓ | OrgServiceImpl:108-115; AuthOrgCrudTests:90-97 |
| Dependent organization cannot be deleted | ✓ | OrgServiceImpl:139-157; AuthOrgCrudTests:103-117 |
| Two campuses have the same department | ✓ | AuthOrgDepTests:65-84 |
| Hospital cannot own a department | ✓ | OrgDepServiceImpl:132-135; AuthOrgDepTests:56-61 |
| Legacy department migration preserves routing identity | ✓ | V16 step 5 (ids/codes stable) + live-DB read (id 1/2/3 codes radiology/us/endo, org_id→274) |

## 发现的问题

⚠ PARTIAL: "Fresh on-premise deployment" (auth-rbac)
- 期望: 全新库跑完迁移后存在一个 first-run 医院根(Option B 占位)+ 一个默认院区。
- 实际: 该行为由 V2 seed + V16 规范化 + V17/V18 重建共同达成,在**已增量迁移**的共享
  `system` schema 上无法证明"一次干净重放"也成立。尝试用 scratch schema 重放被否决:
  V3/V4/V13 显式写 `public.dict_catal` / `public.log_info` / `public.dict_city`,
  在共享服务器上另建 schema 重放会与活的 public 参照表冲突,复现不出全新安装。
- 缓解证据(非闭环): 直接读实机 V16 后的库态确认 `org_info id=1 → HOSPITAL/未命名医院`、
  默认院区 `CAMPUS-1/总院区 is_default=1`、三 seed 科室 `org_id=274` 且 code 不变 ——
  与场景断言的最终形态逐条一致。真正的重放证明需要一次性隔离数据库(CI 级),留待有独立
  PG 环境时补,或按迁移契约书面接受。
- 关联既有风险(a6dcd72 commit body 已记): V17/V18 手工抄列,全新库重放正是唯一能暴露
  "抄漏列"的场景 —— 这也是本条不敢标 IMPLEMENTED 的原因。

### 流程偏离声明(透明记录)
本 change 审计初查到时,"Disabled campus…" 与 "Existing list WITHOUT filter…" 两场景为
PARTIAL(守卫代码存在,但无测试触发该分支)。本轮以**新增测试方法**
(AuthOrgDepTests.disabledCampusCannotOwnDepartmentsAndUnfilteredListSpansAllCampuses)
闭合,未改任何 `main/` 源码 —— 属对 verify "read/execute only" 守则的有限偏离,按 TDD
补齐缺失覆盖,故如实标注。L3 89 项已包含该新测试。

## 下一步
overall = PASS_WITH_ISSUES。/opsx:archive 会先要求确认。建议: 接受该 PARTIAL 为
"迁移契约最终形态已实机核对,干净重放证明受共享环境所限",archive 时记入遗留清单;
或由用户裁定是否等一个独立 PG 环境再做严格重放。

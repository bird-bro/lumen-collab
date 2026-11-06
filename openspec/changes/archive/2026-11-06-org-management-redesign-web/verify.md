<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: org-management-redesign-web
  date: 2026-11-06
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS                 # pnpm build exit 0 (tsc clean)
      L2: "19/19"              # implemented/total scenarios
      L3: PASS                 # pnpm test 21 files / 204 tests / 0 fail (29 of them this change's)
---

# 验证结果: org-management-redesign-web

> 生成时间: 2026-11-06 · 总体: **PASS**
> 受影响 stack: lumen-web only。真后端走查(web delta 全场景过 Vite 代理)由用户确认完成
> (task 4.2, 2026-11-06)。

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `pnpm build` exit 0(tsc 严格模式 0 错误) |
| L2 规格对齐 | 19/19 已实现 | 见下表 |
| L3 测试 | ✓ PASS | `pnpm test` 21 文件 / 204 项 / 0 失败;本 change 贡献 29 项页面测试 + 8 项客户端契约测试 |

环境备注:当前工作树包含并行会话 button-perm-enforcement-web 的在途门控
(canCreate/canUpdate/canDelete 细分,未提交),与本 change 的页面测试完全兼容
(204 = 本 change 的 192 + 其 12 项新测试)。本 change 自身在已提交的 9f7ad2c 上
同为全绿(192/192,提交时验证)。

## 场景对齐详情 — Hospital Profile and Campus Table Management (4/4)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Hospital and campuses render | ✓ | orgs/index.tsx:324(医院根派生)+ :464(默认院区 Tag);orgs-page.test.tsx 渲染断言 |
| Campus CRUD round-trip | ✓ | createOrg/updateOrg/deleteOrg 全走 typed client + 成功后 orgTree() 重查;orgs-page.test.tsx CRUD 三测 |
| Root creation is not a normal action | ✓ | 新页面无任何 新建根机构/新建机构 入口;orgs-page.test.tsx 断言 queryByText 均为 null |
| Organization rejection surfaces | ✓ | ApiError → message + drawer 保持打开 + 表格不变;orgs-page.test.tsx 拒绝两测 |

## 场景对齐详情 — First-run Organization Profile Completion (2/2)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| First-run state renders | ✓ | isFirstRunBootstrap(orgs/index.tsx:73,code=HOSPITAL∧name=未命名医院)→ Alert :246;无根创建控件 |
| First-run profile is completed | ✓ | 编辑医院表单提交后刷新,提示消失;orgs-page.test.tsx first-run 完成测 |

## 场景对齐详情 — Campus Profile Management (3/3)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Create campus profile | ✓ | 表单收集 code/name/region/address/contact/status 全字段;orgs-page.test.tsx 完整 profile 提交测 |
| Code is required | ✓ | rules required '请输入机构代码';空提交不调 createOrg;orgs-page.test.tsx |
| Default campus remains unique | ✓ | 设为默认 → updateOrg(id,{isDefault:1}) → 刷新后唯一 badge;orgs-page.test.tsx 设默认测 |

## 场景对齐详情 — Department Campus Filter Tree (3/3)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Departments of a selected campus | ✓ | 选院区 → pageDep({orgId});deps/index.tsx:154 + deps-page.test.tsx 选中测(结果仅 1 行) |
| Hospital selection means all campuses | ✓ | 全院区视图请求**不含 orgId 键**(deps/index.tsx:138 条件展开);跨院区行显示院区名 |
| Tree is not a department editor | ✓ | 树只渲染医院/院区(fixture 无科室);OrgTree 不传 actions;deps-page.test.tsx 树断言 |

## 场景对齐详情 — Department Table Management (4/4)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Department table is paginated | ✓ | 服务端分页 pageDep + ConsolePagination(total 来自响应);deps-page.test.tsx 共 42 条测 |
| Keyword and status filtering | ✓ | keyword/status 进请求且 reset 到第 1 页;deps-page.test.tsx 两测 |
| Department create campus context | ✓ | 院区过滤预填 owner(deps/index.tsx:187);医院根视图必选(required rule) |
| Hospital cannot own a department | ✓ | 对话框院区 Select 只列**启用**院区(enabledCampuses :100-104);提交前 required 挡空 |

## 场景对齐详情 — Department Code Handling (3/3)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Same code across campuses | ✓ | all 视图两条同码行并存,院区列区分;选中单院区仅 1 行;deps-page.test.tsx |
| Code cannot be edited after creation | ✓ | edit 表单 code disabled;PUT body 不含 code(UpdateDepBody 类型层排除);deps-page.test.tsx |
| Duplicate code in one campus | ✓ | A0712 → form.setFields 落 code 字段 + drawer 保持打开;deps-page.test.tsx |

## 发现的问题

无阻塞项。两条记录:
1. L2 的运行时证据来自 api seam 的单元测试(`vi.mock('@/api/*')`,lumen-web/CLAUDE.md
   允许的测试 seam) + 用户完成的真后端走查(task 4.2);运行时页面无任何 mock。
2. 权限门控当前树上是页级(system:org:list / system:dep:list);button 级细分由并行的
   button-perm-enforcement-web change 负责,不属于本 change 的 WHEN/THEN。

## 下一步
overall = PASS。可执行 `/opsx:archive org-management-redesign-web`(归档时同步
web-system-console 主规格)。

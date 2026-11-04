<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: web-foundation-auth-console
  date: 2026-11-04
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "55/55"
      L3: PASS
    backend:
      L1: SKIP
      L2: "n/a"
      L3: SKIP
---

# 验证结果: web-foundation-auth-console

> 生成时间: 2026-11-04 · 总体: **PASS**(L1 ✓ · L2 55/55 · L3 ✓)
> 范围: 仅 lumen-web 栈(本变更零后端代码,跨域禁令);api 增量为契约文档同步,
> 其场景同时以 lumen-system 既有实现面 + dev mock 为证据。

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `pnpm build`(tsc strict ×2 + vite build)退出 0 |
| L2 规格对齐 | 55/55 已实现 | 0 PARTIAL · 0 NOT_IMPLEMENTED |
| L3 测试 | ✓ PASS | 19 文件 / 155 用例全绿(基线 0 回归) |

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | SKIP | 本变更未触碰 lumen-sev(前端 + 规格文档) |
| L2 规格对齐 | n/a | api 增量场景以下方双栏证据核验(前端 client + mock;后端为已归档实现面) |
| L3 测试 | SKIP | 同上 |

## 场景对齐详情(55/55)

### web-design-system(9/9)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Fonts load offline | ✓ 已实现 | `lumen-web/src/theme/fonts.css:9-25`(三条相对路径 @font-face,文件内 0 个 `http` 引用);走查无外部字体请求 |
| Bundled font set | ✓ 已实现 | `lumen-web/src/theme/fonts/`(恰 3 个 TTF,b2 报告 cmp 字节校验);rg 0 MSYH |
| Base size applied | ✓ 已实现 | `tokens.ts:39`(fontSize 18)+ `tokens.test.ts` 断言 |
| Primary color resolves | ✓ 已实现 | `tokens.ts:36`(colorPrimary #3178fc)+ `:55`(siderBg #0B2540)+ tokens.test 断言;非红 |
| Hex discipline holds | ✓ 已实现 | `hex-scan.test.ts:59`(src 全扫描,金丝雀变异已证明会咬人) |
| Shell regions | ✓ 已实现 | `BasicLayout.tsx:51-55`(RAIL 68/HEADER 60/SIDER 160/PADDING 10)+ `layout.test.tsx:130` + b7 走查实测 |
| Header composition | ✓ 已实现 | `BasicLayout.tsx`(双行名/机构 + 50px 头像,无 caret,Popover 退出)+ layout.test |
| Menu hierarchy display | ✓ 已实现 | `BasicLayout.tsx:181`(Menu type:'group' 分组标题)+ 叶子平铺;mock 分组 path null(`_fixtures.ts:213-226`) |
| Compact list pages | ✓ 已实现 | `console-shell.tsx`(ConsolePanel/ToolRow 无标题块)+ 四页消费 |

### web-auth-flow(8/8)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Successful login | ✓ 已实现 | `pages/login/index.tsx:65-67`(setSession → firstGrantedRoute)+ `login-page.test.tsx:88` |
| Wrong password | ✓ 已实现 | `login/index.tsx:70`(message.error)+ `login-page.test.tsx:111`(留登录页,零会话落库) |
| Enter submits | ✓ 已实现 | `login-page.test.tsx:128` |
| Unauthenticated access | ✓ 已实现 | `components/RequireAuth.tsx:17` + `require-auth.test.tsx:99-103`(零请求)+ b7 走查 #1 |
| Expired token mid-session | ✓ 已实现 | `api/client.ts:125-126`(203 → onSessionExpired,不看 HTTP 状态)+ `require-auth.test.tsx:106-114` |
| Menus follow /me | ✓ 已实现 | `BasicLayout`(store 菜单树渲染导轨/侧栏,NAV_ITEMS 已删)+ `layout.test.tsx` + `menu-paths.test.ts:98-149` |
| Button gating | ✓ 已实现 | `utils/perm.ts:27-33` + 各页门控测试(users/roles/dicts/orgs hide 断言) |
| Logout returns to login | ✓ 已实现 | `BasicLayout.tsx:160`(退出登录 → clear → /login)+ b7 走查 #10(localStorage 清空) |

### web-system-console(14/14)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Tree renders with seeded root | ✓ 已实现 | `pages/system/orgs/index.tsx` + `orgs-page.test.tsx:100` |
| Org CRUD round-trip | ✓ 已实现 | `orgs-page.test.tsx:118-190`(create/PUT/Popconfirm 删除) |
| Departments of a selected org | ✓ 已实现 | `deps/index.tsx` + `deps-page.test.tsx:105-131`(listDeps(orgId)) |
| Department CRUD round-trip | ✓ 已实现 | `deps-page.test.tsx:149-231` |
| Paged user list | ✓ 已实现 | `users/index.tsx`(裸数组客户端分页)+ `users-page.test.tsx:126` |
| Duplicate username feedback | ✓ 已实现 | `users/index.tsx:186`(A0111 落表单)+ `users-page.test.tsx:208`(弹窗不关) |
| Password and enable actions | ✓ 已实现 | `users/index.tsx:208,226` + `users-page.test.tsx:232-303`(回显/越界恢复) |
| Role CRUD round-trip | ✓ 已实现 | `roles/index.tsx:204` + `roles-page.test.tsx`(create/edit/Popconfirm 删除) |
| Menu assignment is full-replace | ✓ 已实现 | `roles/index.tsx:269`(assignRoleMenus + withAncestors)+ `roles-page.test.tsx:179-210`(保存/重开回显/A0400) |
| Menu tree renders | ✓ 已实现 | `menus/index.tsx:29-30`(三级 + perms 标注)+ `menus-page.test.tsx` |
| Catalog list and duplicate feedback | ✓ 已实现 | `dicts/index.tsx` + `dicts-page.test.tsx:100-125`(A0714 落表单) |
| Details ordered by sort | ✓ 已实现 | `dicts/index.tsx:120,133` + `dicts-page.test.tsx:131` |
| Filtered log query | ✓ 已实现 | `logs/index.tsx:51-56`(type/uid/dateYm)+ `logs-page.test.tsx:83` |
| Read-only surface | ✓ 已实现 | `logs-page.test.tsx`(无操作列)+ `menus-page.test.tsx`(只读断言) |

### api(14/14 — 契约同步:前端 client/mock + lumen-system 已归档实现面)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Paged user list(裸数组) | ✓ 已实现 | `api/users.ts:10` + 后端 `UserInfoController.java:45` + mock/users.mock |
| Create user | ✓ 已实现 | `api/users.ts:15` + 冒烟 `data:2` + 后端 POST |
| Duplicate username | ✓ 已实现 | mock A0111 逐字(`users-page.test.tsx:208`)+ 后端异常文案 |
| Password reset and enable toggle | ✓ 已实现 | `api/users.ts:20-28`(newPassword 字段对齐 `ResetPasswordRequest.java:21`)|
| Org tree query | ✓ 已实现 | `api/orgs.ts:10` + 后端 `OrgController.java:44` + 冒烟种子根 |
| Org mutation verbs | ✓ 已实现 | `api/orgs.ts:15-25` + 冒烟 200/true |
| Org tree query(deps)| ✓ 已实现 | `api/org-deps.ts:13` + 后端 `OrgDepController.java:46` |
| Org mutation verbs(deps)| ✓ 已实现 | `api/org-deps.ts:18-28` + 冒烟 A0712 逐字 |
| Department list by org | ✓ 已实现 | 冒烟 `?orgId=1` 三种子科室 |
| Duplicate role name rejected | ✓ 已实现 | mock `roles.mock.ts:57` A0714 逐字 + 后端 `RoleController.java:40` |
| Delete cascades unbind | ✓ 已实现 | 后端 RoleServiceImpl 级联(已归档 auth-rbac)+ `api/roles.ts:30-33` |
| Role-menu full-replace assignment | ✓ 已实现 | `api/roles.ts:39-44` + 冒烟 echo [1,13] + `roles-page.test.tsx:179-196` |
| Role list (gap to implement) | ✓ 已实现 | 按 scenario 自身条款:dev mock 供数(`roles.mock.ts` IPage + menuIds 回显);后端缺口已挂账跟进变更 |
| Menu tree query | ✓ 已实现 | `api/menus.ts:9` + 后端 `MenuController.java:27` + 冒烟三级 perms |

### web-app-shell(10/10)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Theme is applied | ✓ 已实现 | `tokens.ts:36,39,55`(单蓝家族 + 18px)+ tokens.test |
| A component needs a color | ✓ 已实现 | hex-scan.test 常绿(全 src 扫描) |
| Adding a new department | ✓ 已实现 | `router.tsx` 懒加载科室路由(b5b 原样保留) |
| Locating an API client | ✓ 已实现 | `src/api/*.ts` 每资源一文件 |
| Locating a platform page | ✓ 已实现 | `src/pages/login/` + `src/pages/system/**` |
| Envelope type is consumed | ✓ 已实现 | `api/envelope.ts` + `entity-id.ts`(number,ID Serialization) |
| An endpoint is not yet specified | ✓ 已实现 | b3 路径门:24/24 mock URL 全部文档化(逐条对照表) |
| Console runs without backend | ✓ 已实现 | b7 走查 10/10 全程仅 mock(无后端进程) |
| Undocumented endpoint refused | ✓ 已实现 | 同上 b3 门(未文档化路径零存在) |
| Seed parity | ✓ 已实现 | `mock/_fixtures.ts:137,283-330`(admin/admin123 uid 1、示例医院、三科室、common_status,b3 种子对照表) |

## 发现的问题

无 PARTIAL / NOT_IMPLEMENTED。(3 条规格文本长度 WARNING 为表达性提示,非行为缺口。)

## 下一步

- **PASS** → `/opsx:archive web-foundation-auth-console`
- 挂账移交(独立后续变更):后端 `roles/list` 实现;V2 种子菜单三级重构;profile org 字段;
  用户编辑/删除端点(如需);mock 假-token 开关;woff2 子集化;暗色主题

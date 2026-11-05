<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: frontend-backend-first
  date: 2026-11-05
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "5/5"
      L3: PASS
    backend:
      L1: SKIP
      L2: "-"
      L3: SKIP
---

# 验证结果: frontend-backend-first

> 生成时间: 2026-11-05 · 总体: **PASS**

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `pnpm build` 成功（严格 TS，1.72s；chunk 体积警告为既有非失败项） |
| L2 规格对齐 | 5/5 已实现 | delta 收敛后 5 场景全部 IMPLEMENTED（见下） |
| L3 测试 | ✓ PASS | `pnpm test`：20 文件 / 158 用例全绿 |

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | SKIP | 本 change 未触及任何后端代码（纯前端 + openspec + 文档） |
| L3 测试 | SKIP | 同上 |

## 场景对齐详情

**MODIFIED Module Layout（3 场景）**

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Adding a new department | ✓ 已实现 | lumen-web/src/router.tsx:36-38（三科室 lazy 路由模块）；vite.config.ts:32（chunkFileNames 按部门目录派生 chunk，构建产物 assets/pages/<department>/ 实证）；src/pages/{radiology,ultrasound,endoscopy}/index.tsx 显式未实现占位态，无独立部署物 |
| Locating an API client | ✓ 已实现 | src/api/roles.ts:14-16 等逐资源类型化 client（method+path+信封断言于 src/test/api-clients.test.ts）；页面不内嵌传输逻辑（src/pages/system/orgs/index.tsx:25 注释与实现一致：traffic goes through the typed @/api/orgs client） |
| Locating a platform page | ✓ 已实现 | src/pages/login/、src/pages/system/（7 个控制台页）与科室模块同级；router.tsx:29-35 同一 lazy 模式与 typed client seam |

**ADDED Stub and Demo Data Boundaries（2 场景）**

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Test stubs stay at the module seam | ✓ 已实现 | src/test/users-page.test.tsx:33-34、src/test/roles-page.test.tsx:18-19（vi.mock('@/api/...') 模块 seam）；src/test/no-dev-mock.test.ts 保证 mock 层不可回归，seam 打桩是唯一打桩形态；158 用例全绿即打桩与运行时请求路径无关的回归证据 |
| Static demo page boundary | ✓ 已实现 | 边界条款已入本 delta + lumen-web/CLAUDE.md:19-24（Backend-First 章节：demo 数据不经 src/api/、须可识别为演示）；仓库现无演示页，无违反实例 |

## 附注：主 spec 吸收的 requirement（不计入 5/5）

原 ADDED "Dev Server Serves Only Real Backend" 已随并行 web-foundation-auth-console 归档
（80c03c9/f09d3c8）同步进主 spec（三场景：渲染真数据库数据 / 入口不可达故障外露 /
VITE_API_ENTRY_URL 直连预览重定向），语义与本 change 等价。其运行时实证（本会话采集，
详见 evidence.md）：

- A 态：穿 dev server(:1590→8081) 与直连后端响应逐字节一致（profile/4 menus/perms）
- B 态：入口不可达 → HTTP 500 空体（proxy ECONNREFUSED），页面侧 transport `B0001`
  （src/api/client.ts:28,109,116），零假数据
- mock 层物理移除实证：`mock/` 9 文件已删（commit 80c03c9）、vite.config.ts:13 `plugins: [react()]`
  无 mock 插件、`/api/v1` 走 proxy（vite.config.ts:53）

## Issues

无——总体 PASS。

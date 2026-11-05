# 实施证据（frontend-backend-first，2026-11-05）

> 会话内采集的验证记录，供 /opsx:verify 与 archive 引用。时间线均为本地 2026-11-05 上午。

## TDD 循环（Batch 1）

- **RED 确认**（09:47:05）：`pnpm vitest run src/test/no-dev-mock.test.ts` → `Tests 3 failed (3)`
  （mock/ 在场 + vite.config 挂载插件 + package.json 含 devDependency，三项断言全部如预期失败）
- **GREEN 确认**（09:50:37）：删除与摘除完成后 → `Tests 3 passed (3)`

## 任务 2.1 全量回归

- `pnpm test`（vitest）：`Test Files 20 passed (20)` / `Tests 158 passed (158)`（8.71s）
  —— 含并行会话未提交的 tokens/dicts 测试改动，同样全绿
- 注释级修正后复跑：`20 passed (20)` / `158 passed (158)`

## 任务 2.2 dev 链路双态实证

**B 态（后端入口不可达）**：独立 dev server（port 1590，默认 proxy 目标 localhost:80 无监听）：

```
POST http://localhost:1590/api/v1/system/login  →  HTTP 500（vite proxy ECONNREFUSED，空体）
```

—— 无任何 mock 信封/假数据返回，请求如实失败（页面侧表现为 transport `B0001`）。

**A 态（真后端可达）**：本机 8081 运行真实 lumen-system（用户并行 dev server 的
`VITE_API_ENTRY_URL=http://localhost:8081` 暴露了该目标）。重启 1590 指向 8081 后对比：

```
DIRECT8081: {"uid":1,"username":"admin","name":"系统管理员","enable":1,
             "mustChangePassword":1,"roles":["系统管理员"]} | menus:4 | perms:[system:user:list, radiology:report:audit]
VIA1590:    （与 DIRECT8081 逐字节一致；两者各持独立真 JWT，login_user_key 为随机 UUID = 真 TokenService 会话指针形态）
```

—— 页面数据 = 后端响应，无中间层改写。

**旁证（并行会话的在跑 dev server :1573）**：同一登录请求返回真 JWT + 种子菜单树 —— 该
server 已随 vite 配置热重启加载了无 mock 配置（若旧 mock 配置仍在，auth.mock 会拦截并返回
固定假 token）。**注意：长驻 dev server 依赖 vite 配置热重启；保险做法是重启一次以确认。**

## 任务 2.3 残留扫描

- `grep -rln "mockDevServer\|_fixtures\|mock-only\|\.mock\.ts\|mock/" lumen-web --include=*.ts --include=*.tsx`（排除 node_modules）
  → 唯一命中 = `src/test/no-dev-mock.test.ts`（守卫自身断言字符串）
- `vi.mock`/`mockResolvedValue`/`fetchMock.mock.calls` 为 vitest 测试用语，按 spec 豁免
- 清理项：`src/api/types.ts`（9 处）、`src/api/roles.ts`（2 处）、`src/api/index.ts`（1 处）、
  `src/api/org-deps.ts`（1 处）、`src/pages/system/roles/index.tsx`（1 处）、
  `src/pages/system/orgs/index.tsx`（1 处）、`src/test/api-clients.test.ts`（2 处）、
  `src/test/orgs-page.test.tsx`（1 处）、`src/test/menu-paths.test.ts`（1 处）
  —— 全部为指向已删除 `mock/_fixtures.ts` 的陈旧注释/ "mock-only" 标注，改为指向后端种子迁移或如实标注

## 任务 4.1 validate

- `openspec validate frontend-backend-first` → `Change 'frontend-backend-first' is valid`（零警告；
  早期 WARNING"requirement 文本超 500 字符"已通过拆分为两个 requirement 消除）

## 任务 3.3 说明

- `lumen-web/README.md` 不存在（任务为条件式"如有"）；仓库内剩余 `vite-plugin-mock` 字样全部位于
  **历史记录性文档**（`docs/proposals/web-foundation-auth-console.zh.md`、
  `docs/proposals/init-project-skeleton.zh.md`、`openspec/coordination/init-project-skeleton.md`），
  它们描述当时状态，不属清理对象
- 活规则文档（根/前端 CLAUDE.md + 两份 AGENTS.md 镜像）grep 确认：仅剩裁决否定句（"no mock layer"）与测试打桩用语

## spec 场景 ↔ 证据映射

| 场景（web-app-shell delta） | 证据 |
|:--|:--|
| Dev server without mock interception | A 态 VIA1590 == DIRECT8081 |
| Backend absent | B 态 HTTP 500 空体 |
| Mock layer removed | guard 3/3 GREEN + 2.3 零残留 |
| Test stubs stay at the module seam | 158 用例全绿；vi.mock 仅在 src/test |
| Static demo page boundary | 无演示页新增；边界条款已入 spec 与四份 agent 文档 |

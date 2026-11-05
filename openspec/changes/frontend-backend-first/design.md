# Design

## Context

lumen-web 当前 dev 链路：`vite.config.ts:9` 挂载 `vite-plugin-mock-dev-server`（v2.7.1），`mock/` 目录 9 个文件（`_fixtures.ts` + auth/menus/users/roles/orgs/org-deps/dicts/logs）覆盖全部已实现端点，fixtures 与后端种子迁移逐字段对齐。`src/api/client.ts` 为真 fetch 传输层（信封解包、token 注入、`203` 会话过期回调），与 mock 层无耦合。测试套件（18 个文件）在 API 模块 seam 打桩（`vi.mock('@/api/...')`，design D9），不依赖 `mock/` 目录。用户裁决与边界见 proposal.md Why。

## Goals / Non-Goals

**Goals:**
- dev server 请求路径上物理不存在 mock——删除而非门控（用户硬约束："一定要确保……不能使用 mock 的假数据"）
- 文档三层（根 CLAUDE.md / lumen-web CLAUDE.md / AGENTS.md 镜像）与裁决一致，后续 AI 会话不会再按旧约定生成 mock
- 保留既有资产：真传输层、类型化 client、vitest 套件、静态原型实践

**Non-Goals:**
- 不改 `src/api/` 任何代码（client/clients/types 与 mock 移除正交）
- 不引入"mock 开关/模式门控"（OPS 的 `dev-local` 模式被裁决否定——门控仍是假数据通道）
- 不处理静态演示页的具体实现（只定边界：不经 api client、可识别为演示）
- 不动后端与 openspec 其他 spec

## Decisions

1. **删除而非禁用**（裁决直译）：`mock/` 目录整体删除 + `vite.config.ts` 移除插件挂载与 import + `package.json` 移除 `vite-plugin-mock-dev-server` devDependency。备选"enabled 开关门控"（OPS 形制）被否——门控下默认 dev 仍是 mock，正是要根除的状态。
2. **验证以 grep + 运行为证**：`grep -r "mock" lumen-web/`（排除 node_modules、测试 vi.mock、文档叙述）应无 dev-server mock 残留；`pnpm dev` 起服务后确认 `/api/v1` 走 proxy（vite 终端无 mock 拦截日志、请求打到 `VITE_API_ENTRY_URL`）。
3. **测试打桩不动**：`vi.mock('@/api/...')` 是模块 seam 的测试替身，运行时请求路径不存在；与 `mock/` 目录无 import 关系，删除后 `pnpm test` 应原样通过（作为回归证据）。
4. **文档改三处、一次到位**：根 `CLAUDE.md` 工作流第 3 步改为后端先行串行（后端实现 → 用户验 API → 前端连真数据；静态原型仅新页面）；`lumen-web/CLAUDE.md` "Mock-First" 章节重写为 "Backend-First"（含静态演示页边界与"报页面正常=真后端数据"纪律）；`AGENTS.md` 同步镜像。英文为主（auto-loaded 文件英文-only 约定）。

## Risks / Trade-offs

- [删除 mock 后部分尚未实现的后端端点页面无法联调] → 这是有意行为：后端先行裁决下，前端页面开发以"后端端点已实现"为前置；未实现期间的视觉工作走静态演示页
- [现有 dev 习惯断裂（此前 `pnpm dev` 单独即可看页面）] → `pnpm dev` 现在要求后端入口可达（docker compose 或本地服务）；CLAUDE.md 写明启动顺序
- [grep 校验可能误伤（`vi.mock`、`mockResolvedValue`、文档叙述都含 "mock" 字样）] → 校验命令限定路径与语义：只查 `mock/` 目录存在性、vite.config 插件挂载、`mock-only`/`_fixtures` 引用；不追求零 "mock" 字符串
- [静态演示页被误当真页面] → 边界入 spec：数据不经 `src/api/`、可识别为演示；演示页走独立路由/HTML

## Migration Plan

单 commit 原子切换（删目录 + 改配置 + 改文档 + spec delta 同批）：不存在 mock/无 mock 混跑态。回滚 = revert 该 commit（mock/ 文件在 git 历史完整可恢复）。

## Open Questions

（无——边界均经用户 2026-11-05 拍板：测试打桩保留、静态硬编码演示数据放行、dev 零 mock 为硬约束）

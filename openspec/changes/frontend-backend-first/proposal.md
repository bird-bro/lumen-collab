# Proposal

## Why

用户裁决（2026-11-05，源自历史项目教训）：开发流程**后端先行**；前端页面数据必须全部来自真实后端 API——页面显示 mock 假数据会掩盖后端 bug，导致无法联调验证。当前 lumen-web 的 dev mock 层（`vite-plugin-mock-dev-server` 永开 + `mock/` 目录 9 个文件 + fixtures 与后端种子 SQL 逐字段对齐）正是该风险源头：dev 态即使真后端在跑，页面仍被 mock 静默遮蔽，且视觉/网络面板均不可分辨。

## What Changes

- **BREAKING**（开发流程语义）：删除 lumen-web 全部 dev-server mock——`mock/` 目录（9 文件）、`vite.config.ts` 的 `mockDevServerPlugin` 挂载、`package.json` 对应 devDependency
- `pnpm dev` 物理上只能请求真后端：`/api/v1` 代理至 `VITE_API_ENTRY_URL ?? http://localhost:80`（nginx 统一入口）；后端未启动时页面请求如实失败，**无任何假数据回退**
- 文档与约定同步：根 `CLAUDE.md` 开发流程第 3 步（"Frontend Agent (parallel): mock from spec" → 后端先行串行流）、`lumen-web/CLAUDE.md` "Mock-First" 章节重写为 Backend-First、根 `AGENTS.md` 镜像同步
- **保留**（用户批准 2026-11-05）：单元测试的 `vi.mock` API 模块打桩（不出现在任何页面）；静态演示页/硬编码演示数据（独立于 api client、与真页面物理隔离，仅用于看页面效果）

## Capabilities

### New Capabilities

（无——不引入新系统能力）

### Modified Capabilities

- `web-app-shell`：Module Layout requirement 移除 "the mock seam" 表述；新增 "Dev Server Serves Only Real Backend" requirement（dev server 无任何 mock 拦截、仓库不携带 mock 层、代理指向真实统一入口）

## Impact

- **代码**：`lumen-web/mock/`（删除）、`lumen-web/vite.config.ts`、`lumen-web/package.json`
- **文档**：`CLAUDE.md`、`AGENTS.md`、`lumen-web/CLAUDE.md`
- **Spec**：`openspec/specs/web-app-shell`（1 处修改 + 1 处新增）
- **工作流**：后续跨栈 change 从"前端并行 + mock 从 spec"改为"后端先行 → 用户验 API → 前端对真数据开发"（如 workstation-context 的 chip 直连真 `/me`）
- **不受影响**：`src/api/*` 真实传输层与类型化 client、vitest 测试套件（模块级打桩与 `mock/` 目录无依赖关系）、后端任何模块

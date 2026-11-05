# frontend-backend-first 变更中文说明

> **免责声明**：本文档为面向人的中文导读，**不具约束力**；`openspec/` 下的英文 artifacts
> （proposal / specs / design / tasks）才是唯一事实来源。两者不一致时以英文版为准。

## 一、这个变更解决什么问题

用户裁决（2026-11-05）：**开发流程后端先行，前端页面数据必须全部来自真实后端 API**。
此前项目踩过的坑是：页面显示前端 mock 的假数据，导致无法判断后端接口是否有 bug。

lumen-web 当前状态恰好复刻了该坑的全部条件：

- `vite-plugin-mock-dev-server` 在 `pnpm dev` 下**永远开启**，无任何开关
- `mock/` 目录 9 个文件覆盖全部已实现端点，且 fixtures **有意与后端种子 SQL 逐字段对齐**
  ——页面内容与真后端一模一样，视觉上完全不可分辨
- 参考项目 OPS（ops_web）虽有同类插件但只留 2 个演示 mock，且提供 `dev:local` 模式关
  mock 联调真后台；lumen-web 走得比 OPS 更远，必须拉回

## 二、改什么（文件路径与角色）

| 路径 | 动作 | 角色 |
|:--|:--|:--|
| `lumen-web/mock/`（9 文件） | **删除** | dev mock 层本体 |
| `lumen-web/vite.config.ts` | 移除插件挂载与 import | 请求路径上不再有拦截点 |
| `lumen-web/package.json` + 锁文件 | 移除 devDependency | 依赖层清干净 |
| 根 `CLAUDE.md` / `AGENTS.md` | 重写工作流第 3 步 | "后端先行"成文，AI 会话不再按旧约定生成 mock |
| `lumen-web/CLAUDE.md` | "Mock-First" → "Backend-First" | 前端 agent 的行为准则 |
| `openspec/specs/web-app-shell`（delta） | 1 处修改 + 1 处新增 | "Dev Server Serves Only Real Backend" 成为可验证契约 |

**明确保留**（用户批准）：

1. 单元测试的 `vi.mock('@/api/...')` 模块打桩——测试数据不出现在任何页面
2. 静态演示页/硬编码演示数据——看页面效果用，独立于 `src/api/`，与真页面物理隔离

## 三、流程变化

```
旧: 后端 Agent ∥ 前端 Agent(mock 从 spec) 并行 → 前端页面上是假数据
新: 后端 Agent 先实现 → 用户验 API → 前端 Agent 连真后端开发
    （要看视觉效果 → 静态演示页硬编码，不冒充接口）
```

## 四、下一步行动建议

1. `opsx:apply frontend-backend-first` 按任务清单实施（删除 → 回归验证 → 文档同步）
2. 实施后本地开发习惯变化：`pnpm dev` 的前置是后端入口可达
   （`VITE_API_ENTRY_URL ?? http://localhost:80`，即 nginx 统一入口 / docker compose）
3. 后续 change 排序（本会话已议定）：`workstation-context`（后端 auth-rbac 增量先行，
   前端工作站 chip 直连真 `/me`）→ `patient-domain`（患者域后端）

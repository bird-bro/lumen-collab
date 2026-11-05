# Execution Contract

> LoopForge workflow bridge. No implementation without an approved contract.
> guard.sh enforces this: proposing->applying transition checks contract existence + freshness.

## Intent Lock

- **Change name**: frontend-backend-first
- **Problem to solve**: dev-server mock 层（vite-plugin-mock-dev-server + mock/ 9 文件，fixtures=种子数据）在页面展示假数据、遮蔽后端 bug；用户裁决 2026-11-05 后端先行、前端零 mock（物理移除，非门控）。
- **In scope**: 删 `lumen-web/mock/`；`vite.config.ts` 摘插件；`package.json` 清 devDependency；mock 防回归 guard 测试；回归/双态实证/残留扫描；根 CLAUDE.md + AGENTS.md + lumen-web/CLAUDE.md 后端先行成文；web-app-shell spec delta 对齐。
- **Out of scope**: `src/api/**` 任何代码；后端模块；mock 门控/模式开关（被否）；静态演示页的具体实现（仅边界入 spec）；`vi.mock` 测试打桩的改动（保留）。

## Approved Behavior

- **Approved requirements summary**: web-app-shell delta — MODIFIED Module Layout（去 "mock seam" 表述）；ADDED "Dev Server Serves Only Real Backend"（仅代理真后端、仓库无 mock 层、后端缺失如实失败 B0001）；ADDED "Stub and Demo Data Boundaries"（测试打桩不进运行时请求路径；静态演示数据不经 `src/api/` 且可识别为演示）。
- **Key scenarios**: dev 直连真后端 / 后端缺失如实失败 / mock 层不存在 / 测试打桩止于模块 seam / 演示页边界。
- **Acceptance checks**: `openspec validate` PASS；`pnpm build` + `pnpm test` 全绿；grep 无 mockDevServer/_fixtures 残留；dev 双态实证记录。

## Design Constraints

- **Architecture constraints**: 删除而非门控（OPS dev-local 形制被裁决否定）；`pnpm dev` 行为唯一合法形态 = proxy 到 `VITE_API_ENTRY_URL ?? http://localhost:80`。
- **Interface constraints**: 不改 `src/api/client.ts`、类型化 client、envelope 语义；不改后端。
- **Dependency constraints**: 仅移除 `vite-plugin-mock-dev-server`（2.7.1），不新增任何依赖。
- **Data constraints**: 无 schema/数据变更。

## Task Batches

### Batch 1
- **Goal**: mock 层物理移除（tasks 1.1–1.3）
- **Input**: tasks.md 组1；guard 测试先行（RED：mock 存在时断言失败）
- **Output**: `mock/` 删除、vite.config 无插件、lockfile 干净；guard 测试 GREEN
- **Completion criteria**: L1 `pnpm build` PASS；guard 测试通过；tasks 1.1–1.3 勾选

### Batch 2
- **Goal**: 回归与物理保证实证（tasks 2.1–2.3）
- **Input**: Batch 1 产出
- **Output**: 全量单测绿（155+ cases）；dev 双态实证记录（真后端可达/不可达）；残留扫描零命中
- **Completion criteria**: tasks 2.1–2.3 勾选，证据落 change 目录或会话记录

### Batch 3
- **Goal**: 后端先行成文（tasks 3.1–3.3）
- **Input**: design.md 决策 4
- **Output**: 根 CLAUDE.md / AGENTS.md 工作流第 3 步重写；lumen-web/CLAUDE.md Backend-First 章节
- **Completion criteria**: 三文件无 mock 指导词，镜像语义一致

### Batch 4
- **Goal**: spec 对齐收尾（tasks 4.1–4.2）
- **Input**: Batch 2 证据
- **Output**: `openspec validate` PASS；四场景证据核对表
- **Completion criteria**: tasks 4.1–4.2 勾选

## Test Obligations

- **Behaviors requiring failing-test-first**: guard 测试（no-dev-mock）——先写先跑确认 RED（mock 在场时失败），Batch 1 删除后转 GREEN。测试文件：`lumen-web/src/test/no-dev-mock.test.ts`，断言 (a) `mock/` 目录不存在 (b) `vite.config.ts` 不含 `vite-plugin-mock-dev-server`/`mockDevServerPlugin` (c) `package.json` 无该 devDependency。
- **Required edge cases**: 后端不可达 = transport `B0001` 如实失败（现有 api-client.test.ts 已覆盖 envelope 语义，不新增）。
- **Regression-sensitive areas**: 全量 Vitest 套件（155+ cases）；`pnpm build` 严格 TS。

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 全部任务为机械删除 + 命令验证 + 文档重写，tasks.md 已给出逐条验证命令；零设计自由度、零跨模块集成，子代理调度只增协调开销与漂移风险（SDD 模型表：mechanical 档本就够，inline 更省）。loop-state execution_mode 随本 change 切为 Batch Inline。

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: Batch 1 后（删除完整性 + guard 测试 GREEN）；Batch 3 后（三文档语义一致）；全部完成后 code-review diff 对照 spec WHEN/THEN。
- **Blocking categories**: spec 漂移；mock 残留（任何形式）；文档与裁决矛盾。

## Escalation Rules

- **When to rewind to proposing**: 出现新范围（如用户要保留某 mock 供演示）；关键假设失效（如插件与 vite 7 强耦合导致构建断）。
- **When NOT to continue**: intent lock 与 proposal 范围不再匹配。

# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: modality-dict-id-anchor-web
- **Problem to solve**:
4 个 `/setting` 页（检查项目/检查部位/检查方法/报告模板）的列表加载被
`dictItemsByName('检查类型')` 门控——该目录名在 V27 统一后不存在，按名端点契约返回
`[]`（不报错）且空结果进会话缓存，`modality` 门永不打开，页面零 API 调用，与
web-system-console 主规格的页面行为要求相悖。

4 个 /setting 页的字典解析由按名「检查类型」（不存在的目录名 → 空 [] 成功缓存）切到 items-by-id id 1024，恢复页面取数；单测 mock 同步；呈现零变化。
- **In scope**:
- `MODALITY_DICT_ID = 1024` 常量
- 4 个 setting 页 swap 到 `dictItemsById`
- 4 个测试文件 mock 同步
- 集成验证（pnpm test + build）
- **Out of scope**:
- users/room/device 存量按名消费者迁移（coordination debt，另行提案）
- 空词表页面降级行为重设计
- users-page 测试（mock 缝在 @/api/dicts，不在本变更面）

## Approved Behavior

<!-- Reviewed 2026-11-20: 两个场景可测（wire 可观察:items-by-id 调用;改名韧性 = id 锚的结构性质） -->
- `specs/web-system-console/spec.md`
5:### Requirement: Modality Catalog Resolution by Id
11:#### Scenario: Pages load against the id anchor
17:#### Scenario: Catalog rename is invisible to the pages


## Design Constraints

- **Architecture/Decisions**:
- **D1 常量形状**：`MODALITY_DICT_ID = 1024` 放 `dict-items.ts`，镜像
  `NUMBER_TYPE_DICT_ID`；页面不再持有模态目录名字符串（aria-label/文案与目录名解耦，
  「检查类型」作为 UI 标签保留）。
- **D2 mock 缝**：4 个页面测试 mock `@/utils/dict-items` 的 `dictItemsByName` → 改
  `dictItemsById(1024)`，断言同步。users-page 测试 mock 的是 `@/api/dicts`
  （按名「设备类型」），不在本变更面内。
- **D3 门控语义不动**：`if (modality)` 级联门保留——字典加载失败仍不拉列表
  （错误已 toast）；只换解析源。
- **Technical constraints**:
- pnpm dev 直连真后端（禁 mock 层，2026-11-05 裁决）；mock 仅允许在测试缝
- rep-template 同文件叠有 rep-temp-normal-range 未提交改动（apply 先读盘上最新状态）
- antd 5 组件优先；TS strict

## Task Batches

<!-- Batch 1 = 组 1（常量 + 页面 swap）；Batch 2 = 组 2（mock 同步）；Batch 3 = 组 3（集成验证）。顺序依赖成立 -->
5:- [ ] 1.1 `src/utils/dict-items.ts` 新增 `MODALITY_DICT_ID = 1024`（含裁决注释）；verify: `pnpm build` 类型通过
6:- [ ] 1.2 exam-item/exam-body/exam-method/rep-template 四页 `dictItemsByName(DICT_MODALITY)` → `dictItemsById(MODALITY_DICT_ID)`，移除失效名字常量；verify: grep 无 `'检查类型'` 目录名字符串残留于运行时代码 + `pnpm dev` 页面发出 items-by-id 请求且列表加载
10:- [ ] 2.1 exam-item/exam-body/exam-method/rep-temp 四个页面测试的 dict mock 从 by-name 改 `dictItemsById(1024)`，断言同步；verify: 四套件绿
14:- [ ] 3.1 `pnpm test` 全量相关套件 + `pnpm build`，日志重定向文件；verify: exit 0，路径记录

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: 2.1 先把 mock 改为 `dictItemsById(1024)`（RED：页面仍按 by-name 调用）→ 1.2 页面切换后 GREEN
- **Required edge cases**: 字典加载失败 → 门控不开 + toast（既有语义不回归）；会话缓存 id 键去重
- **Regression-sensitive areas**: rep-temp-page 测试（rep-temp-normal-range 正在改同文件）；dict-items.test.ts 的 util 契约

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 4 个 tracked 任务，同栈单模块，无风险指标；>3 → Batch Inline

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 
- **Blocking categories**: 

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

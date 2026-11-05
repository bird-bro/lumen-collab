# dict-model-spec-sync 提案简报（中文导读）

> **免责声明**：本文是 `openspec/changes/dict-model-spec-sync/` 的非权威中文导读，
> 帮助快速理解提案意图与流程；英文 OpenSpec 工件（proposal/specs/design/tasks）才是
> 唯一权威来源，两者不一致时以英文工件为准。

## 一、提案解释（为什么做）

2026-11-05 用户对字典模型做了三次裁决并已全部落库：真实字典从 rcp-test 复制入库
（V6）、`common_status` 重锚到 id 1000（V7）、明细模型精简（V9：删 `ca_code`/`label`/
`initial`，`back_1/back_2` 改名 `tag/flag`，`value` 成为唯一文本字段，唯一约束改为
`(ca_id, value)`）。**代码与共享测试库已经是新模型（65 个后端测试全绿），但
`openspec/specs/system-data/spec.md` 还停留在旧模型**——双锚定、label、back1/back2、
最小种子集。本提案不做任何代码改动，只把规格同步回现实，防止后续 change 按旧规格
把删掉的列再实现回来。

## 二、涉及文件与角色

| 文件 | 角色 |
|:--|:--|
| `openspec/changes/dict-model-spec-sync/proposal.md` | 提案：动机/变更点/能力边界/影响面 |
| `openspec/changes/dict-model-spec-sync/specs/system-data/spec.md` | **核心 delta**：重写 Dict Catalog / Dict Detail / Dict Seed Data 三个需求 |
| `openspec/changes/dict-model-spec-sync/design.md` | 决策记录：D1 规格追赶现实（sync-not-revert）；D2 场景只写可观察行为 |
| `openspec/changes/dict-model-spec-sync/tasks.md` | 实施清单：delta 复核 → L2 验证（映射既有测试）→ 中文简报 → verify → archive |
| `openspec/specs/system-data/spec.md` | 被同步的主规格（archive 时由 delta 覆写对应需求） |
| 既有测试 `AuthSystemDataMigrationTests` / `AuthDictCrudTests` / `AuthDataLayerTests` | delta 场景的现成 L2 证据，零新增测试 |

**明确不做**：菜单/IA 相关规格（今天没有任何 spec 枚举菜单种子，留给排队的
`workstation-context` change）；任何代码/迁移/测试改动。

## 三、下一步动作建议

1. 审阅本提案工件（重点是 delta 三个重写需求的措辞）。
2. 确认后运行 `/opsx:apply`——执行任务清单（复核 → 跑测试取证 → verify → archive 同步主规格）。
3. 之后按队列推进 `workstation-context`（后端 /me 先行）与 `patient-domain` 提案。

## 四、流程规划

propose（本步）→ 用户确认 DP-3 批准执行契约 → apply（纯文档任务，Batch Inline）
→ verify（L1 不适用/L2 场景映射/L3 测试套件）→ archive（delta 同步进主规格，
change 生命周期结束）。

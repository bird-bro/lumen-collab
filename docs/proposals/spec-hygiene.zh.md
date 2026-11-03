# spec-hygiene 提案导读（非权威）

> 本文档是 `openspec/changes/spec-hygiene/` 的中文导读，**非权威**——权威以
> `openspec/changes/spec-hygiene/` 内的 proposal/specs/design/tasks 为准。

## 提案说明

auth-rbac-foundation 归档日（2026-11-03）的两项平台级裁定，使主规格有四处需求措辞过时：

1. **framework/common 可演进**（原"逐字节镜像 + 仅五项适配"）——服务拓扑规格仍锁定旧语义，
   而适配清单已有第 6 项（`UserHeaderBridgeFilter` 上移 + 匿名路径配置化）。
2. **配置本地化、无 Nacos**（随访 fu-launch 同形）——环境契约规格仍要求提供/对接 Nacos。

本 change **零代码**，只把四处需求的措辞对齐现实：改为"OPS 血统共享平台层、加法式演进、
适配清单按裁定增长"与"外部供给收敛为 PostgreSQL + Redis、连接走 env 变量或 gitignored
profile"。

## 文件路径与角色

| 路径 | 角色 |
|:--|:--|
| `openspec/changes/spec-hygiene/proposal.md` | 提案（Why / What / Capabilities） |
| `openspec/changes/spec-hygiene/specs/service-topology/spec.md` | delta：2 个 MODIFIED 需求（平台层可演进 + 适配清单增长） |
| `openspec/changes/spec-hygiene/specs/environment-contract/spec.md` | delta：2 个 MODIFIED 需求（外部服务收敛 + 连接配置） |
| `openspec/changes/spec-hygiene/design.md` | D1：纯措辞变更，标准 delta 纪律（拷贝整块再改） |
| `openspec/changes/spec-hygiene/tasks.md` | 3 项写作任务 + 2 项校验/归档任务 |

## 下一步动作建议

1. `/opsx:apply spec-hygiene`（实际无事可做——任务 1.x 已随提案完成，勾选即可）
2. `/opsx:verify spec-hygiene` → `/opsx:archive spec-hygiene`（归档同步即落地主规格）
3. 归档后全规格 grep 复查（tasks 2.2）：无"Nacos 配置中心"残留措辞、verbatim-only 语义清零

## 流程规划

propose（本文）→ apply（勾任务）→ verify → archive（delta 同步进主规格）——预计一个
会话内完成，无阻塞项。

# rename-auth-to-system 提案导读（非权威）

> 本文档是 `openspec/changes/rename-auth-to-system/` 的中文导读，**非权威**——权威以
> `openspec/changes/rename-auth-to-system/` 内的 proposal/specs/design/tasks 为准。

## 提案说明

`lumen-auth` 实质已是系统管理服务（用户/角色/菜单/机构/科室/字典/日志 + 登录），"auth"
命名脱节。用户裁定（2026-11-03，思路 2）：**更名 lumen-system**——此刻前端未启动、
无任何 API 消费方，是零成本窗口期。四个子决策：路径全改 `/api/v1/system/**`、
schema `auth`→`system`、角色模板 `lumen_system_role`、dict 数据层迁入服务五层而
log 写路径留守 framework（`LogAspect` 切面约束，OPS 同款不对称）。

## 文件路径与角色

| 路径 | 动作 |
|:--|:--|
| `lumen-sev/lumen-auth/` → `lumen-sev/lumen-system/` | git mv 模块（历史保留） |
| 包根 `org.birdbro.lumen.auth` → `...system`；`AuthApplication`→`SystemApplication` | 全量包/类更名 |
| `framework/dict/**` → `lumen-system` 五层 | dict 归位（16 文件迁移） |
| 控制器路径 + `security.permit-all` + 测试 URL | `/api/v1/auth/**`→`/api/v1/system/**` |
| 测试服务器 DB | `ALTER SCHEMA auth RENAME TO system`（元数据操作） |
| 规格 delta ×6 | api / auth-rbac / service-topology / system-data / data / environment-contract |

## 下一步动作建议

1. **先 `/opsx:verify system-data` → archive**（顺序约束：system-data 的 delta 仍是
   auth 路径，必须先落地主规格，本 change 的 delta 再 re-pin 到 system 路径）
2. 批准本 change 执行合同 → `/opsx:apply rename-auth-to-system`
3. `lumen_sev` 文档与 naming.md 随 2.2 任务同步

## 流程规划

propose（本文）→ DP-3 → apply（DB → 模块 → 路径/dict → 测试 → 文档）→ verify → archive。

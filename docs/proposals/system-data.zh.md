# system-data 提案导读（非权威）

> 本文档是 `openspec/changes/system-data/` 的中文导读，**非权威**——权威以
> `openspec/changes/system-data/` 内的 proposal/specs/design/tasks 为准。

## 提案说明

平台两类**跨服务共享系统数据**的落地方案（用户裁定 2026-11-03：表放共享库 `public`
schema；Java 侧独立于 auth、与 log 的 framework 归属对称）：

1. **字典**：`public.dict_catal`/`dict_detail`（随访验证的形状：caId+caCode 双锚点、
   label/value/color/css），从 OPS `manage/dict` 加法式拷入 `framework/dict/`，管理端点
   归 lumen-auth；种子 `common_status` 起步。
2. **日志**：拷贝件机器全套在 `framework/log` 但实体指着不存在的 `ice.` schema——本次
   去前缀（**适配 (7)**，随访先例）+ `V3` 建 `public.log_info`/`log_sign` + `@Log` 接线
   管理写端点 + 分页查询端点。

## 文件路径与角色

| 路径 | 角色 |
|:--|:--|
| `lumen-sev/lumen-auth/src/main/resources/db/migration/V3__system_data.sql` | 四张 public 表 + 种子（新建） |
| `lumen-sev/framework/log/entity/*.java` | 去 `ice.` 前缀（适配 (7)，改 2 文件） |
| `lumen-sev/framework/dict/**` | 加法式拷入（新建，entity/mapper/service/助手） |
| `lumen-sev/lumen-auth/.../controller/Dict*Controller` `LogController` | 管理端点（新建） |
| `lumen-sev/lumen-auth/src/main/resources/application.yml` | `currentSchema=auth,public` |
| `docs/deployment/pg-init.sql` | public 系统表 GRANT 模板增补 |

## 下一步动作建议

1. 用户批准 execution-contract（DP-3）→ guard `proposing→applying` → `/opsx:apply`
2. 归档顺序：**system-data 先于 spec-hygiene**（两者的 service-topology delta 已写成
   同一最终文本，实际顺序无关，但先 system-data 语义最顺）
3. spec-hygiene 随后归档即完成全部规格对齐

## 流程规划

propose（本文）→ DP-3 批准 → apply（迁移 → framework → 端点 → 测试闭环）→ verify →
archive → spec-hygiene 收尾。

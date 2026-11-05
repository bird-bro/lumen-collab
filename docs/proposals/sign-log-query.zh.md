# sign-log-query 提案简报（中文导读）

> **免责声明**：本文是 `openspec/changes/sign-log-query/` 的非权威中文导读；英文
> OpenSpec 工件（proposal/specs/design/tasks/execution-contract）才是唯一权威来源。

## 一、提案解释（为什么做）

两件事叠加成一个 change：

1. **登录日志是暗数据**：`log_sign` 表每次登录/登出都在写（system-data task 3.4），
   但没有查询端点、没有页面——控制台只有操作日志一页。随访参照系（权限模式参照）
   就是两页：操作日志 + 登录日志。
2. **日志表该分区了**：OPS 参照库 rcp-test 实测（2026-11-05，postgres MCP）：
   `log_info`/`log_sign` 是 `RANGE (date_ym)` 分区表，按年子分区
   （`FROM (202500) TO (202599)` 式），目的按年 detach 归档（年 5 万行/22MB，纯运维
   诉求非性能）。lumen 的 `date_ym` 列和索引本来就有，分区键现成。

用户拍板（2026-11-05）：加 default 分区兜底（命名 `log_info_def`/`log_sign_def`，对
rcp-test 的唯一偏离——参照库没有 default，跨年忘预建会写失败丢日志）；**两个页面**
而非 Tabs（列集/过滤/权限边界本来就是两个东西，见 design D6 分析）。

## 二、方案一句话

V13 迁移把两张日志表逐表单事务"建新分区父表→搬数据→换名"（复合主键 `(id, date_ym)`，
预建 2026/2027 + `*_def` 兜底）→ V14 种"登录日志"菜单（安全组 16 下，权限串
`system:signlog:list`）→ `LogController` 加 `GET /system/logs/sign`（**方法级**
@PreAuthorize 覆盖类级，两页权限互相独立；响应剔除 token 列）→ 前端新增只读
登录日志页。全部加法，既有 `GET /system/logs` 契约和写入链路零改动。

## 三、涉及文件与角色

| 文件 | 角色 |
|:--|:--|
| `lumen-system/.../db/migration/V13__log_tables_partitioned.sql`（新） | 分区改造：copy-swap、复合 PK、2026/2027/def 子分区、序列 OWNED BY 重挂 + setval |
| `lumen-system/.../db/migration/V14__sign_log_menu.sql`（新） | 菜单 17 行 + role 1 授权 + setval（V5/V8 幂等纪律） |
| `framework/.../log/service/ILogSignService.java` + Impl | 加法 `getPage(...)`：account/uid/dateYm 过滤、deleted=0、select 剔除 token、actionTime 倒序 |
| `lumen-system/.../controller/LogController.java` | 新增 `@GetMapping("/sign")` 方法（不动现有方法），方法级 @PreAuthorize |
| 新测试 `AuthSignLogQueryTests` + 权限矩阵用例 | RED→GREEN：分页/过滤/无 token；signlog 权限双向 pin |
| `lumen-web/src/api/types.ts`、`src/api/logs.ts` | `LogSignItem`（无 token 字段）+ `listSignLogs()` |
| `lumen-web/src/pages/system/logs/sign.tsx`（新） | 登录日志页：console-shell 同款外观，过滤 账号/操作人ID/月份 |
| `lumen-web/src/router.tsx`、`src/routes/menu-paths.ts` | 路由 `/system/logs/sign` + 菜单键映射 |
| specs delta | system-data（System Log Tables 改写 + Sign Log Query Endpoint 新增）+ web-system-console（Sign-in Log Query 新增） |

## 四、明确不做

自动预建分区（pg_partman/定时任务，未来 ops change）；日志保留/归档策略；操作日志
页与 `GET /system/logs` 的任何改动；按钮级权限粒度；复制的 `getLogSignPage(LogRequest)`
XML 行为（非控制台调用方不受影响）。

## 五、下一步

DP-3 审阅执行契约 → `/opsx:apply`（TDD 全流程，SDD 模式，5 批次 10 任务）→
用户对真后端验收登录日志页 → `/opsx:verify` → `/opsx:archive`。

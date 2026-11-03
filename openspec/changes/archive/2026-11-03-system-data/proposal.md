# Proposal: system-data

## Why

平台有两类**跨服务共享的系统数据**没有着落，且其中一半处于"通电未接表"状态：

1. **字典（dict）**：下拉/渲染需要的 label↔value 系统数据。OPS（`birdbro-manage` 的
   `org.birdbro.manage.dict`，27 文件）与随访（`fu-biz` 的 `org.birdbro.dict`）都有成熟
   血统；lumen 完全没有。
2. **日志（log）**：拷贝件的日志机器**全套已在** `framework/log/`（`LogInfoEntity`/
   `LogSignEntity`/mapper/service）+ `LogAspect` + `AsyncFactory`/`AsyncManager`，但
   实体硬编码 `ice.log_info`/`ice.log_sign`——`ice` 是 OPS 自家 schema，lumen 库中不
   存在；lumen-auth 的 V1 也没有 log 表。机制一旦触发即 `relation does not exist`。

用户裁定（2026-11-03）：**dict/log 表放共享库的 `public` schema**；Java 侧**独立于
auth**——log 本就在 framework，dict 以同款对称性加法式拷入 `framework/dict`；管理端点
归 lumen-auth（系统管理服务）。

## What Changes

- **V3__system_data.sql**（lumen-auth Flyway）：`public.dict_catal` / `public.dict_detail`
  / `public.log_info` / `public.log_sign` 四表（随访形状，Integer id，log 无审计块——纯
  追加写）+ 最小字典种子（显式 id + setval，V2 同款纪律）+ `pg-init.sql` 增补 public 表
  对六个服务角色的 GRANT 模板
- **适配 (7)**：`framework/log` 两实体去掉 `ice.` 前缀（随访同款）；lumen-auth 连接串
  `currentSchema=auth,public`（未限定表名先搜 auth 再落 public；Flyway 历史仍在 auth）
- **`framework/dict/` 加法式拷入**（OPS manage/dict 四层：entity/mapper/service/pojo，
  随访已验证的血统形状）+ label↔value 翻译助手
- **lumen-auth 管理端点**：字典目录/明细 CRUD（`/api/v1/auth/dicts/**`）+ 日志分页查询
  （`/api/v1/auth/logs`）+ 管理写端点接 `@Log` 操作日志
- **登录事件接线**：`AuthServiceImpl` 登录成功/失败经 `AsyncFactory.recordForSign` 写
  `log_sign`——与拷贝件已接线的登出（`LogoutSuccessHandlerImpl`）对称，审计链完整
- 纯增量：`dict_city`（省市区）缓办——消费方（患者地址表单）随 lumen-patient change
  到来，种子需从 OPS 导出，属数据工程；届时一并落地

## Capabilities

### New Capabilities

- `system-data`: 平台共享系统数据——字典目录/明细管理与种子、操作日志表与查询端点、
  public schema 落位与共享授权。

### Modified Capabilities

- `data`: Schema-per-Service Isolation 增加精确例外——`public` schema 中的平台共享系统
  表（dict/log）按设计对所有服务角色授权；服务私有 schema 间仍互不可见。
- `service-topology`: Recorded Adaptations 清单六 → 七（适配 (7)：去 `ice.` 前缀，
  未限定表名经 `search_path=auth,public` 解析）。

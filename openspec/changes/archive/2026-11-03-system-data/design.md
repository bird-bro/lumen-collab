# Design

## Context

OPS/随访两代血统都验证过 dict/log 的形状：`dict_catal`/`dict_detail`（EntityBase 审计块 +
caId/caCode 双锚点冗余）与 `log_info`/`log_sign`（追加写、dateYm 年月键、无审计块）。
lumen 的拷贝件 log 机器全套在 `framework/log` + `LogAspect`/`AsyncFactory`/`AsyncManager`
但实体硬编码不存在的 `ice.log_info`/`ice.log_sign`；dict 完全缺失。用户裁定（2026-11-03）：
表放共享库 `public` schema；Java 侧独立于 auth，与 log 的 framework 归属对称。

## Goals / Non-Goals

- Goals: 四张 public 表 + 种子；framework/dict 加法式拷入；ice. 前缀去除（适配 (7)）；
  lumen-auth 管理端点；@Log 接线让 log_info 活起来；登录事件接线与登出对称
- Non-Goals: `dict_city`（省市区——消费方随 lumen-patient change 到来，种子需从 OPS
  导出属数据工程，届时一并落地）；log 的删除/归档策略

## Decisions

### D1 - public schema + search_path 解析（用户裁定）
四表建在 `public`，DDL 显式 `public.` 限定（与 Flyway 历史 schema 无歧义）；lumen-auth
连接串改 `currentSchema=auth,public` → 未限定实体名先搜 auth 再落 public（随访"去前缀"
同款，但落点为共享 public）。其余服务将来连接串同构 `currentSchema=<own>,public`。

### D2 - 包路径对称：framework/dict，不开新模块（用户裁定独立于 auth）
log 机器本就在 `framework/log`；dict 以同款对称性加法式拷入 `framework/dict/`（OPS
manage/dict 的 entity/mapper/service/pojo 四层，27 文件选必要子集：entity 3 + mapper 3 +
service 3 + pojo 精简——`DictMenu`/`DictCity` 不进 v1）。翻译助手
`DictLabelHelper`/`DictReverseHelper` 随包进 `framework/dict/`。管理控制器归 lumen-auth
（系统管理服务，对应 OPS manage/launch 角色）。`@MapperScan("org.birdbro.**.mapper")`
自动覆盖新 mapper。

### D3 - 迁移归属：lumen-auth V3，公共表一次性创建
`V3__system_data.sql`：四张 `public.` 表 + `common_status` 种子（显式 id + setval，V2
纪律）。其余服务 Flyway 不携带此迁移（public 表全库一份）。`pg-init.sql` 增补第六节：
public 系统表对六个角色的 GRANT 模板（与角色块同款注释风格，DBA 部署时执行）。

### D4 - @Log 与 sign 事件接线范围
lumen-auth 全部管理写端点（orgs/org-deps/users/roles/dicts 的 create/update/delete）
标注拷贝件 `@Log`；操作日志经 LogAspect → AsyncManager 异步落 `public.log_info`（操作者
身份经 UserHeaderBridgeFilter 桥接）。**登录事件接线**（用户裁定 2026-11-03）：登录
成功/失败分支经 `AsyncFactory.recordForSign` 写 `log_sign`——登出已由拷贝件
`LogoutSuccessHandlerImpl` 接线，V3 建表后对称生效，审计链完整（有登出必有登录）。

## Risks / Trade-offs

- **角色隔离条款出现设计内例外** → data delta 已把例外写精确（public 系统表授权 vs 服务
  私有 schema 互拒），归档时落地主规格
- **异步日志写共享测试服务器** → log_info 行随测试累积于测试库（与七表测试数据同性质，
  属测试库用途）
- **LogAspect 在本 train 首次实测** → 拷贝件在 OPS/随访生产验证过；lumen 侧先以单端点
  接线验证（tasks 4.4），异常则收窄范围并记 debt

## Open Questions

无——public 落点、包归属、迁移归属均经用户裁定（2026-11-03）。

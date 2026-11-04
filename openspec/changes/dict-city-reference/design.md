# Design

## Context

省市区参照数据在随访库 `followup.public.dict_city` 现成存在（3751 行，13 列）。用户裁定
（2026-11-03）：dict 表放 public、纯后端字典（无前端增删改页面）。数据已导出为
`data/rows.sql`（随本 change 提交，来源可追溯）。

## Goals / Non-Goals

- Goals: public.dict_city 建表 + 3751 行种子（V4）+ 只读查询端点（tree/list）+
  实体/只读服务归位 lumen-system 五层
- Non-Goals: 增删改端点；@Log（只读）；dict_city 管理页面；名称国际化；merge 语义

## Decisions

### D1 - 数据源与种子纪律
种子 = 随访库导出快照（`data/rows.sql`，3751 行，导出于 2026-11-03）→ V4 内分批
INSERT（每 300 行一批）+ 显式 id + setval 推进 bigint 序列。行数 checksum 入迁移测试
（总 3751 / L1×35 / L2×373 / L3×3342）。

### D2 - 表形状照随访 DDL（13 列全 nullable 除 id）
`id bigint`（**Long 实体**——静态参照数据的血统保真偏离，设计决策记录）+ 12 业务列
varchar/int 型照导出 DDL；索引照搬：PK(id) + idx(up_id) + idx(code)；**无审计块**。
实体不继承 EntityBase（无审计列）。

### D3 - 只读端点，无写路径
`GET /api/v1/system/dicts/city/tree`（TreeUtils 按 upId=0 装配，复用机构树同款机制）+
`GET /api/v1/system/dicts/city/list?level=&upId=`。无 POST/PUT/DELETE——静态参照数据
的更新走后续种子迁移（V5+），不暴露写端点。@Log 不接（只读）。

### D4 - 顺序依赖
V4 追加于 V3 之后（V3 checksum 冻结）；本 change 不触碰 dict_catal/dict_detail/
log 表。

## Risks / Trade-offs

- **3751 行种子进 Flyway** → 迁移文件 ~400KB，一次性成本；行数 checksum 测试防漂移
- **Long id 偏离 Integer 约定** → 设计决策记录（静态参照数据血统保真）；业务实体仍守
  Integer 约定

## Open Questions

无——数据源/形状/只读边界均经用户裁定。

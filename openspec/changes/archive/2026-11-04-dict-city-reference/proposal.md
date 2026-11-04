# Proposal: dict-city-reference

## Why

省市区是患者地址/机构归属的基础参照数据。数据已在血统库中现成存在（随访库
`followup.public.dict_city`，**3751 行**：L0 中国 ×1 · L1 省级 ×35 · L2 市级 ×373 ·
L3 区县 ×3342，13 列含 pinyin/lng/lat/initial），随 lumen-patient 落地的原计划提前
至此——患者/控制台的地址级联就绪即用。

用户裁定（2026-11-03）：dict 表全部放共享库 `public` schema；**本功能为纯后端字典**
——不做前端增删改页面（静态参照数据，种子即数据源），仅提供只读查询端点。

## What Changes

- **V4__dict_city.sql**（lumen-system Flyway）：`public.dict_city` 建表（13 列照随访
  DDL：`bigint id`、up_id/code/name/abbreviation/level/city_code/zip_code/merger/
  pinyin/lng/lat/initial，**无审计块**——静态参照数据；索引 up_id + code）+ **3751 行
  种子**（显式 id，分批 INSERT，来源数据快照随 change 提交 `data/rows.sql`）+ setval
- **只读查询端点**（lumen-system）：`GET /api/v1/system/dicts/city/tree`（upId=0 树装配）
  与 `GET /api/v1/system/dicts/city/list`（level/upId 过滤）——**无任何增删改端点**
- **实体/查询服务**：`DictCity` 实体（**Long id**——血统保真：静态参照数据沿用随访
  Long，偏离 Integer 约定记为设计决策）+ 只读 mapper/service
- 规格同步：system-data 能力 ADDED "City Reference Data" 需求

## Capabilities

### Modified Capabilities

- `system-data`: 新增 City Reference Data 需求（public.dict_city、只读查询端点、
  血统种子 3751 行）。

## Non-Goals

- 无增删改端点（静态参照数据，用户裁定：不涉及前端增删改页面）；数据更新走后续种子迁移
- `dict_city` 不进 swagger 之外的管理页面；不接 `@Log`（只读）
- 不做名称/编码的国际化

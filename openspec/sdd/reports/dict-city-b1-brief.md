# Batch 1 Brief — dict-city-reference（全部 5 任务）

## 环境铁律
JAVA_HOME 显式导出 + 绝对路径 cd + echo MVN_EXIT。**V3/V1/V2 迁移文件零触碰**（V3 已
应用到共享测试库, checksum 冻结——只新增 V4）。

## 数据资产（已就位）
- 种子快照：`openspec/changes/dict-city-reference/data/rows.sql` —— 3751 行、每 300 行
  一个批量 INSERT 段（`;` 分隔），13 列序：id, up_id, code, name, abbreviation, level,
  city_code, zip_code, merger, pinyin, lng, lat, initial。**直接把该文件内容作为 V4 的
  INSERT 主体**（文件首行的 `-- rows: 3751` 注释去掉，改为你的迁移头注释）。
- 血统 DDL（照抄列型）：id **bigint**（PK, Long 实体）、up_id int、其余 11 列 varchar/
  smallint；**无审计块**（实体不继承 EntityBase）；索引：PK(id) + idx(up_id) + idx(code)。

## 任务 1.1 — `V4__dict_city.sql`（lumen-system/src/main/resources/db/migration/）
1. `CREATE TABLE public.dict_city (...)` 13 列（照血统 DDL）+ 三索引
2. 种子 INSERT：data/rows.sql 内容分批嵌入（显式 id）
3. `SELECT setval(pg_get_serial_sequence('public.dict_city','id'), (SELECT MAX(id) FROM public.dict_city))`
   —— 若 serial 序列不存在则先 `CREATE SEQUENCE` 或用 `bigint GENERATED`… **照 V2 的
   setval 手法**：建表用 `id bigint NOT NULL` + 手动 `CREATE SEQUENCE public.dict_city_id_seq`
   + `ALTER TABLE ... ALTER COLUMN id SET DEFAULT nextval(...)`（serial 与显式 id 的组合
   照 V1/V2 模式）
4. 迁移测试 `AuthDictCityMigrationTests`（照 AuthSystemDataMigrationTests 风格）：
   行数 checksum——总 3751 / L1×35 / L2×373 / L3×3342 / L0×1（table_schema='public'）
   + 13 列 information_schema 断言 + 中国根（up_id=0, id=100000）

## 任务 1.2 — 数据层（lumen-system 五层）
- `entity/DictCityEntity.java`：`@TableName("dict_city")`（无 schema 限定），
  **`private Long id` + `@TableId(value="id", type=IdType.AUTO)`**，12 字段（upId, code,
  name, abbreviation, level, cityCode, zipCode, merger, pinyin, lng, lat, initial），
  **不继承 EntityBase**，`implements Serializable`；`@Data`；`@author birdbro`
- `mapper/DictCityMapper.java`：BaseMapper + `@Mapper`
- `service/IDictCityService.java` + `impl/DictCityServiceImpl.java`（只读）：
  `List<DictCityEntity> tree()`（全量查后 TreeUtils 按 upId=0 装配——参照
  `OrgTreeService`/`MenuTreeService` 的 TreeUtils 用法，rootParentValue=0）
  `List<DictCityEntity> list(Integer level, Integer upId)`（可空过滤，level/upId 双可选）

## 任务 2.1 — `CityController`（只读）
- `GET /api/v1/system/dicts/city/tree` → tree()
- `GET /api/v1/system/dicts/city/list?level=&upId=` → list(level, upId)
- **无 POST/PUT/DELETE**；`@Api(tags = "省市区字典")` + `@ApiOperation`（swagger 规格不
  能空）；返回 envelope 照现有控制器

## 任务 2.2 — 测试
- 迁移 checksum 测试（1.1 的 Verify）
- 端点测试：tree 含中国根+北京市嵌套；list?upId=110100 只回区县；list?level=1 只回省级
- 全量回归（既有 50+3=53 全绿）

## 任务 3.1 — 文档
- `docs/proposals/dict-city-reference.zh.md` 中文导读（非权威声明头）

## 完成标准与提交
1. RED/GREEN（checksum 测试先红——V4 未建时）+ 全量绿 MVN_EXIT=0
2. commit：`✨ feat(sev): dict-city reference data - public table + 3751-row seed + read-only query (dict-city-reference)`
   （含 openspec/changes/dict-city-reference/data/rows.sql 一并提交——数据快照随 change）
3. 报告：`openspec/sdd/reports/dict-city-b1-report.md`（RED/GREEN、checksum 证据、偏差）

## 不要做
- 不写增删改端点；不接 @Log；不动 framework（dict_city 全在服务内）；不动
  dict_catal/dict_detail/log 表；V1-V3 零触碰

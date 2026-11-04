# Tasks

> 验收命令一律 `cd lumen-sev && ./mvnw ...`（JAVA_HOME 显式导出）。

## 1. 迁移与数据层

- [ ] 1.1 `V4__dict_city.sql`：`public.dict_city` 建表（13 列照随访 DDL，无审计块，
  PK(id)+idx(up_id)+idx(code)）+ `data/rows.sql` 的 3751 行分批 INSERT + setval。
  Verify: 迁移测试断言行数 checksum（总 3751 / L1×35 / L2×373 / L3×3342）。
- [ ] 1.2 `DictCity` 实体（Long id, 13 字段, 不继承 EntityBase）+ 只读 mapper +
  `IDictCityService`（tree 装配 / list 过滤）。Verify: 数据层往返测试。

## 2. 端点与闭环

- [ ] 2.1 `CityController` 只读端点：`GET /api/v1/system/dicts/city/tree` +
  `GET /api/v1/system/dicts/city/list?level=&upId=`（无写端点）。Verify: 先红后绿。
- [ ] 2.2 全量回归 + swagger 冒烟（dicts/city 端点出现在规格中）。Verify: MVN_EXIT=0。

## 3. 文档

- [ ] 3.1 `docs/proposals/dict-city-reference.zh.md` 中文导读。

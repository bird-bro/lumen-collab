# dict-city-reference 提案导读（非权威）

> 本文档是 `openspec/changes/dict-city-reference/` 的中文导读，**非权威**——权威以
> `openspec/changes/dict-city-reference/` 内的 proposal/specs/design/tasks 为准。

## 提案说明

省市区是患者地址/机构归属的基础参照数据。数据在随访库 `followup.public.dict_city`
中现成存在（**3751 行**：L0 中国 ×1 · L1 省级 ×35 · L2 市级 ×373 · L3 区县 ×3342，
13 列含 pinyin/lng/lat/initial），本提案把它提前落地为 lumen-system 的字典能力：

1. **V4 迁移**：`public.dict_city` 建表（13 列照随访 DDL；`bigint id` 是记录在案的
   **血统保真偏离**——静态参照数据沿用随访 Long 主键，偏离 lumen Integer 约定；
   **无审计块**，实体不继承 EntityBase）+ **3751 行全量种子**（显式 id，13 个批量
   INSERT，来源快照随 change 提交）+ setval 推进序列（V2 手法：手工序列 + OWNED BY，
   `pg_get_serial_sequence` 可解析）。
2. **只读查询端点**（用户裁定 2026-11-03：纯后端字典，不做前端增删改页面）：
   `GET /api/v1/system/dicts/city/tree`（TreeUtils 按 upId=0 装配，中国根，复用机构树
   同款机制）与 `GET /api/v1/system/dicts/city/list?level=&upId=`（双可选过滤）——
   **无任何增删改端点、不接 @Log**；数据更新只走后续种子迁移（V5+）。
3. **行数 checksum 入迁移测试**（`AuthDictCityMigrationTests`）：总 3751 / L0×1 /
   L1×35 / L2×373 / L3×3342 + 13 列形状断言 + 中国根断言 + 序列推进断言，防种子漂移。

## 文件路径与角色

| 路径 | 角色 |
|:--|:--|
| `openspec/changes/dict-city-reference/data/rows.sql` | 随访导出快照（3751 行，2026-11-03），V4 种子的数据来源（随 change 提交） |
| `lumen-sev/lumen-system/src/main/resources/db/migration/V4__dict_city.sql` | 建表（13 列 + PK/双索引 + 手工序列）+ 13 批量种子 INSERT + setval（新建） |
| `lumen-sev/lumen-system/.../entity/DictCityEntity.java` | 13 字段实体（Long id，不继承 EntityBase，children 查询态字段）（新建） |
| `lumen-sev/lumen-system/.../mapper/DictCityMapper.java` | 只读 BaseMapper（新建） |
| `lumen-sev/lumen-system/.../service/IDictCityService.java` + `impl/DictCityServiceImpl.java` | tree 装配 / list 过滤（只读，新建） |
| `lumen-sev/lumen-system/.../controller/CityController.java` | `GET /tree` + `GET /list`（无写端点，新建） |
| `lumen-sev/lumen-system/src/test/java/.../AuthDictCityMigrationTests.java` | 迁移 checksum 测试（新建） |
| `lumen-sev/lumen-system/src/test/java/.../AuthDictCityTests.java` | 数据层往返 + 端点 + 只读证明测试（新建） |
| `docs/proposals/dict-city-reference.zh.md` | 本导读（非权威） |

## 下一步动作建议

1. `lumen-patient` 地址级联（患者建档/编辑）直接消费 `/api/v1/system/dicts/city/**`
   ——tree 做级联面板，`list?upId=` 做逐级加载，`list?level=` 做省级首屏。
2. 数据修订（如行政区划调整）走新种子迁移（V5+），禁止直改库/加写端点。
3. `/opsx:verify` 校验 WHEN/THEN → `/opsx:archive` 归档本 change。

## 流程规划

propose（已完成）→ 批准 execution-contract → apply（V4 迁移 → 数据层 → 只读端点 →
测试闭环 → 中文导读）→ verify → archive。

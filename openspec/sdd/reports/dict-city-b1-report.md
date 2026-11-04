# Batch 1 Report — dict-city-reference（全部 5 任务）

**Status: DONE** · Commit `032221c` on `dev`（17 files, +8549）

## 任务闭环

| 任务 | 交付 | 证据 |
|:--|:--|:--|
| 1.1 V4 迁移 | `V4__dict_city.sql`（3838 行）：13 列建表 + PK/idx(up_id)/idx(code) + 手工序列（nextval default + OWNED BY）+ 13 批量种子 INSERT（12×300 + 1×151 显式 id）+ setval | Flyway 实跑 "Successfully applied 1 migration ... v4" |
| 1.2 数据层 | `DictCityEntity`（Long id，不继承 EntityBase，children 查询态）+ `DictCityMapper`（只读 BaseMapper）+ `IDictCityService`/`DictCityServiceImpl`（tree 装配 / list 双可选过滤） | 数据层往返随 AuthDictCityTests 服务级用例验证 |
| 2.1 只读端点 | `CityController`：`GET /tree` + `GET /list?level=&upId=`；无 POST/PUT/DELETE、无 @Log | 405 拒绝 + checksum 不变（测试证明） |
| 2.2 测试回归 | `AuthDictCityMigrationTests`（6）+ `AuthDictCityTests`（6，含 swagger OAS 冒烟） | 全量 **65/0/0，MVN_EXIT=0**（基线 53 + 新增 12） |
| 3.1 文档 | `docs/proposals/dict-city-reference.zh.md`（非权威导读） | 随 commit |

## RED / GREEN

1. **迁移 RED**：`AuthDictCityMigrationTests` 先行 → 6 run，3 failures + 3 errors，根因
   `relation "public.dict_city" does not exist`（V4 未建）。
2. **迁移 GREEN**：V4 落地 → 6/0/0。
3. **端点 RED**：`AuthDictCityTests` 先行 → 编译失败（`找不到符号 IDictCityService`），
   MVN_EXIT=1。
4. **端点 GREEN**：数据层 + controller 落地 → 6/0/0；全量回归 65/0/0。

## Checksum 证据（AuthDictCityMigrationTests，共享测试库实测）

- 总行数 **3751** ✓ · L0×**1** ✓ · L1×**35** ✓ · L2×**373** ✓ · L3×**3342** ✓
- 13 列 information_schema 逐列按序断言 + data_type 逐列断言（id bigint / up_id integer /
  level smallint / 其余 character varying）✓
- 索引 `dict_city_pkey` / `idx_dict_city_up_id` / `idx_dict_city_code` ✓
- 中国根：id=100000、up_id=0、code='0'、level=0、pinyin='China'，且为唯一根 ✓
- 序列推进：`pg_get_serial_sequence` 可解析且 last_value ≥ MAX(id)=900000 ✓
- 端点：tree 中国→北京→北京市→东城区四级嵌套、北京市 16 区、level=1 恰 35 省、
  POST/DELETE 405 envelope 且表行数冻结 3751、swagger `/v3/api-docs` 含两 GET 无写操作 ✓

## 偏差与裁定记录（均已在代码注释/报告留痕）

1. **快照机械修正（1 处）**：`rows.sql` 末批终止符是"尾随逗号 + 独立 `;` 行"——原样嵌入会
   生成 `VALUES (...),(...),;` 非法 SQL（实测 PG `syntax error at end of input`，Flyway 回滚）。
   V4 中末行改为以 `;` 收尾（内部 12 处批边界保持逐字不动），已在 V4 头注释声明。
2. **实体 `upId` 为 Long**（brief 服务签名写 Integer）：`TreeUtils.buildTree` 要求 id/parentId
   同一 K 类型且 `equals` 可判等（Long≠Integer），`Long` 实体是树装配成立的前提；DDL 仍
   `up_id integer` 照 brief。list(Integer, Integer) 签名照 brief 不变。
3. **序列补 `OWNED BY`**：手工 CREATE SEQUENCE + SET DEFAULT 若不 OWN BY 列，
   `pg_get_serial_sequence` 返回 NULL → setval 静默空转（首插将撞种子 id）。补 OWNED BY
   即完成 serial 等价组合（V1 语义），brief 的 setval 行因此真实生效。
4. **setval 用 V2 原式**（含 `COALESCE(MAX(id),1)`）——brief 明示"照 V2 手法"。
5. **`data/.gitignore`（`!*.sql`）**：用户全局 gitignore `*.sql` 吞掉 rows.sql；照
   `db/migration/.gitignore` 先例加目录级否定，使快照随 change 入库。
6. **实体 Lombok 仅 `@Data`**（brief 明示；未加 OPS 三件套）——静态只读实体无构造器需求。

## 边界遵守

V1-V3 零触碰（diff 仅新增 V4）；framework/common/dict_catal/dict_detail/log 表零触碰；
无写端点、无 @Log；`.env-test.local` / `application-dev.yml` 未入库；他人 in-flight 文件
（SystemApplication.java、AuthUserAgentResilienceTests.java、loop-state.yaml 等）未staged。

## 遗留

无阻塞。后续数据修订走 V5+ 种子迁移（design D3）；患者地址级联可直接消费
`/api/v1/system/dicts/city/**`。

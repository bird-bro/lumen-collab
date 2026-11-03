# Batch 1 Report — system-data（tasks 1.1, 1.2, 2.1, 2.2）

Status: DONE_WITH_CONCERNS（全部任务完成、套件全绿；2 条关注项需 Batch 2 裁定，见“偏差记录” F/G）

## 实现了什么

1. **任务 1.1 — `V3__system_data.sql`**（TDD：先 RED 后 GREEN）
   - 四表全部显式 `CREATE TABLE public.<name>`（Flyway 历史仍在 auth schema）。
   - `dict_catal` / `dict_detail`：`id serial PRIMARY KEY` + 业务列 + EntityBase 五列审计尾块
     （deleted / create_by / create_time / update_by / update_time，照抄 V1 顺序）；部分唯一索引
     `uk_dict_catal_code (code) WHERE deleted = 0`、`uk_dict_detail_ca_code_value (ca_code, value)
     WHERE deleted = 0`；`idx_dict_detail_ca_id`。
   - `log_info` / `log_sign`：追加写、无审计块（无 create_by/update_by），列与拷贝件实体字段
     一一对应（camelCase→snake_case，26 列 / 18 列）；`deleted integer NOT NULL DEFAULT 0`
     （理由见偏差记录 C）；`idx_log_info_date_ym`/`idx_log_info_uid`、`idx_log_sign_date_ym`/
     `idx_log_sign_uid`。
   - 种子：`common_status` 目录（id=1，enable=1，sort=0）+ 启用/停用两条明细（id 1/2，ca_id=1 +
     ca_code='common_status' 双锚点，code/value 按 lumen 1/0 约定）+ setval 尾块（V2 手法）。
2. **任务 1.2 — `docs/deployment/pg-init.sql`**：纯追加“六、public 共享系统表授权”注释模板节
   （与角色块同款注释态），GRANT SELECT/INSERT/UPDATE/DELETE ON 四表 TO 六角色，附 PG15+
   的 `GRANT USAGE ON SCHEMA public` 提示行。既有行零改动。
3. **任务 2.1 — 去 `ice.` 前缀 + 连接串**：两实体 `@TableName` 去 `ice.`；**两个 mapper XML**
   （`LogInfoMapper.xml`/`LogSignMapper.xml`，各 2 处 FROM/INSERT INTO）同样去 `ice.`（协调者
   中途确认，见偏差记录 A）；`application-dev.yml`（gitignored）与 `.example` 的 URL 改
   `currentSchema=auth,public`；`AuthIntegrationTestBase` 的 @DynamicPropertySource URL 同改。
4. **任务 2.2 — `framework/dict/` 加法式拷入**（16 个全新文件，OPS manage/dict 血统）：
   entity 2 + mapper 2 + service 2 + impl 2 + pojo 6（Catal/Detail 的 Create/Modify/Query）+
   `DictLabelHelper`/`DictReverseHelper`。`DictMenu`/`DictCity`/Menu pojo 未拷。包名
   `org.birdbro.framework.dict.*`；`@TableName` 不带 schema 限定；`@MapperScan("org.birdbro.**.mapper")`
   与 `scanBasePackages="org.birdbro"` 自动覆盖新 mapper/@Component。framework 现有文件除
   两实体 @TableName 与两 XML（均为“去 ice.”适配）外一行未改。

## RED + GREEN 证据

- **RED**（V3 未写时先跑全量 `./mvnw test -q`，MVN_EXIT=1）：
  `Tests run: 41, Failures: 5, Errors: 2`——7 个失败/错误**全部**在新类
  `AuthSystemDataMigrationTests`（既有 34 全绿）：
  - `fourSystemTablesExistInPublicSchema`: `public.dict_catal must exist ... expected: <1> but was: <0>`
  - 种子断言: `BadSqlGrammarException ... PSQLException: ERROR: relation "public.dict_catal"
    does not exist`（dict_detail 同）
- **GREEN**（V3 + 2.1/2.2 全部落位后再跑，MVN_EXIT=0）：
  `./mvnw compile -q` exit 0；surefire 汇总 **41 tests / 0 failures / 0 errors**（既有 34 +
  新增 7），`AuthSystemDataMigrationTests: tests=7 failures=0 errors=0`——V3 已在团队测试服务
  器真实落表（information_schema/pg_indexes 断言通过）+ common_status 种子可查。

## 文件清单（commit `88806a7`，25 files, +1130/−10）

新增：
- `lumen-sev/lumen-auth/src/main/resources/db/migration/V3__system_data.sql`
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthSystemDataMigrationTests.java`
- `lumen-sev/framework/src/main/java/org/birdbro/framework/dict/`：`DictCatalEntity.java`、
  `DictDetailEntity.java`、`DictCatalMapper.java`、`DictDetailMapper.java`、
  `IDictCatalService.java`、`IDictDetailService.java`、`DictCatalServiceImpl.java`、
  `DictDetailServiceImpl.java`、`pojo/request/{CatalCreate,CatalModify,CatalQuery,
  DetailCreate,DetailModify,DetailQuery}.java`、`DictLabelHelper.java`、`DictReverseHelper.java`

修改（均只含本批任务行）：
- `lumen-sev/framework/src/main/java/org/birdbro/framework/log/entity/LogInfoEntity.java`
  （@TableName）、`LogSignEntity.java`（@TableName）
- `lumen-sev/framework/src/main/resources/mapper/LogInfoMapper.xml`、`LogSignMapper.xml`
  （各去 2 处 `ice.`，见偏差记录 A）
- `lumen-sev/lumen-auth/src/main/resources/application-dev.yml.example`、
  `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthIntegrationTestBase.java`
  （currentSchema=auth,public）
- `docs/deployment/pg-init.sql`（第六节追加）

未提交（gitignored，本机已同步改 `currentSchema=auth,public`）：
`lumen-sev/lumen-auth/src/main/resources/application-dev.yml`（`git check-ignore` 验证未入暂存）。

## 自审发现

- 列数逐字段核对：log_info 26 列 / log_sign 18 列 / dict 两表与实体+EntityBase 完全对齐。
- dict 表列序断言（id 打头 + 审计块收尾）与 V3 DDL 一致，测试通过即证明。
- `currentSchema=auth,public` 下既有 34 测试全绿 → auth 语义未被 public 打扰（auth 首位）；
  Flyway 历史仍在 auth（`spring.flyway.schemas=auth` 未动）。
- **V3 已对共享测试库生效**：此后任何对 V3 的行修改都会触发 Flyway checksum 冲突——
  Batch 2+ 只能追加 V4，不得回改 V3。
- 翻译助手 javadoc/注释按拷贝件原样保留（仅包名/导入适配）；代码行零逻辑改动。

## 偏差记录

- **A. mapper XML 也去了 `ice.`（超出 brief 字面“两处 @TableName”）**：`insertInfo`/`insertSign`/
  两个分页 SELECT 是拷贝件真实读写路径，硬编码 `ice.log_info`/`ice.log_sign`——只改 @TableName
  时这些语句仍会 `relation does not exist`。XML 位于 `framework/src/main/resources/mapper/`
  （不在 brief 禁改的 `framework/log` Java 包内），改动即本批“去 ice. 前缀”同款适配。**协调者
  中途确认执行**；适配 (7) 文件清单据此扩展为：两实体 + 两 mapper XML。
- **B. dict_detail DDL 以实体为准**（brief 已授权）：`back1/back2` → **`back_1`/`back_2`**
   （实体显式 `@TableField`）；brief 列清单漏掉的实体字段 **`remark varchar(255)`、
  `initial varchar(64)`** 已补列。
- **C. log 表 `deleted` 用 `integer NOT NULL DEFAULT 0` 而非 boolean**：实体字段虽是 Boolean，
  但拷贝 XML 是裸 SQL `WHERE deleted = 0`、MyBatis-Plus 逻辑删除按配置值内联 0/1——boolean
  列会双双报错（`operator does not exist: boolean = integer`）。读路径 PG JDBC getBoolean
  对 int 列 0/1 正常转 false/true（fu 生产同构验证）。
- **D. setval 仅推进两张 dict 表**：log 表无种子，`setval(seq, COALESCE(MAX(id),1)=1)` 会把
  空表序列跳过 id=1；V2 手法的目的是“越过显式种子 id”，log 表无碰撞风险故保持原生序列
  （注释已写明理由）。
- **E. 翻译助手保留 hutool `StrUtil`**：framework 现有代码已按传递依赖使用 hutool
  （`AsyncFactory` 的 ObjUtil、`LogSignServiceImpl` 的 BeanUtil），保留即最少改动；未新增任何
  pom 依赖。
- **F.（关注项，Batch 2 需裁定）enable 语义冲突**：拷贝的 `DictDetailServiceImpl`
  `mapByCatalIds`/`valueMapByCatalId` 以 `EnableConstants.TRUE = 11` 过滤启用明细，而本批 DDL/
  种子按 brief + lumen 约定用 **1/0**（`enable DEFAULT 1`、种子 1/0）。若 Batch 2 端点复用这两
  个方法，种子明细（enable=1）将查不到——需裁定：写端按 11/10（血统）或改造过滤（lumen 约定）。
  本批未改拷贝件逻辑，仅登记。
- **G.（关注项，Batch 2 参照）DetailQuery 血统分歧**：OPS 原件按 `value` 模糊查、随访按
  `label` 模糊查（impl `getPage` 同步分歧）。本批按 brief 血统源拷 OPS（value）。Batch 2
  “明细按 caCode 列表”端点如需 label 检索可再加法式扩展。
- **H. pg-init 增补了一行 brief 未点名的注释行**：`GRANT USAGE ON SCHEMA public`（PG15+ 访问
  public 表必需），同节注释态，不影响既有行。

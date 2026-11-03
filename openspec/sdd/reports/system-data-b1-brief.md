# Batch 1 Brief — system-data（tasks 1.1, 1.2, 2.1, 2.2）

## 环境铁律（违反即失败）
- 每条 mvn 命令前必须：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`
  （前台 shell 丢失 JAVA_HOME → Lombok 在新 JDK 崩溃 → 几百个假"找不到符号"）
- 一切命令显式 `cd`（绝对路径 `/Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev`），
  不要依赖会话 cwd
- 测试命令 `./mvnw test -q`（连接团队测试服务器，凭据在 gitignored `lumen-sev/.env-test.local`，
  已配置）；`MVN_EXIT` 必须显式回显
- 不要动 `openspec/specs/`（只读共享事实源）；不要改拷贝件中任务外的任何行

## 任务 1.1 — `V3__system_data.sql`（先写 RED 测试，再写迁移）

**RED**：先建 `lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthSystemDataMigrationTests.java`
（模仿 `AuthSchemaMigrationTests` 的 JdbcTemplate 风格，继承 `AuthIntegrationTestBase`），
断言：`public` schema 中存在 `dict_catal`/`dict_detail`/`log_info`/`log_sign` 四表
（information_schema 查 `table_schema='public'`）+ `common_status` 种子目录与其明细可查。
跑一次确认 RED（四表不存在）。**注意：跑 RED 前不要建 V3**。

**GREEN**：写 `lumen-sev/lumen-auth/src/main/resources/db/migration/V3__system_data.sql`：

- 四表全部 `CREATE TABLE public.<name>`（显式限定；Flyway 历史仍落 auth schema）
- **dict_catal / dict_detail**：完全对照随访血统（实体字段见下），`id serial PRIMARY KEY` +
  业务列 + EntityBase 审计块（照抄 V1 的五列尾块：`deleted integer NOT NULL DEFAULT 0,
  create_by integer, create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, update_by
  integer, update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP`）
  - dict_catal：`code varchar(64) NOT NULL`（部分唯一索引 `uk_dict_catal_code ON (code)
    WHERE deleted = 0`）、`name varchar(64) NOT NULL`、`enable integer NOT NULL DEFAULT 1`、
    `sort integer NOT NULL DEFAULT 0`、`remark varchar(255)`、`cls integer`、`initial varchar(64)`
  - dict_detail：`ca_id integer NOT NULL`、`ca_code varchar(64) NOT NULL`、`code integer`、
    `value varchar(128)`、`label varchar(128) NOT NULL`、`color varchar(32)`、`css varchar(128)`、
    `sort integer NOT NULL DEFAULT 0`、`enable integer NOT NULL DEFAULT 1`、
    `back1 varchar(128)`、`back2 varchar(128)`；索引 `idx_dict_detail_ca_id` + 部分唯一
    `uk_dict_detail_ca_code_value ON (ca_code, value) WHERE deleted = 0`（先按此写；若拷贝
    实体发现与上述列冲突，以实体为准并在报告里记录偏差）
- **log_info / log_sign**：**无审计块**（追加写；实体不继承 EntityBase——列必须与拷贝件
  实体字段一一对应，camelCase→snake_case：dateYm→date_ym、actionTime→action_time，其余
  单词列同名。**动手前先读** `framework/src/main/java/org/birdbro/framework/log/entity/
  LogInfoEntity.java` 与 `LogSignEntity.java` 的字段清单，以实体为准）。log_info 索引：
  `idx_log_info_date_ym`、`idx_log_info_uid`；log_sign 同理
- 种子：`public.dict_catal` id=1 `('common_status','通用状态',1,0)` 风格 + 明细两条
  （启用/停用，显式 id）+ **setval 尾块**（照抄 V2 的 setval 手法，对新表逐个推进序列）
- 文件头注释风格照 V1/V2（英文注释）

## 任务 1.2 — `docs/deployment/pg-init.sql` 增补

文件末尾追加一节（注释风格与既有角色块一致，中文注释）：第六节"public 共享系统表授权"——
GRANT SELECT/INSERT/UPDATE/DELETE ON public.dict_catal, public.dict_detail, public.log_info,
public.log_sign TO 六个角色（lumen_auth_role / lumen_patient_role / lumen_radiology_role /
lumen_us_role / lumen_endo_role / lumen_integration_role）。保持与角色块相同的"注释模板"
形态（该文件角色块即为注释态，DBA 部署时启用）。**纯追加，不改既有行。**

## 任务 2.1 — 去 `ice.` 前缀 + 连接串

1. `framework/src/main/java/org/birdbro/framework/log/entity/LogInfoEntity.java`:
   `@TableName("ice.log_info")` → `@TableName("log_info")`
2. 同目录 `LogSignEntity.java`: `@TableName("ice.log_sign")` → `@TableName("log_sign")`
3. `lumen-auth/src/main/resources/application.yml` 不含连接串（无需改）；改
   `lumen-auth/src/main/resources/application-dev.yml`（gitignored，本机存在）与
   `application-dev.yml.example`：URL 中 `currentSchema=auth` → `currentSchema=auth,public`
4. `lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthIntegrationTestBase.java` 中
   @DynamicPropertySource 注入的 datasource URL 若含 `currentSchema=auth` → 改
   `auth,public`（先 grep 确认实际写法再改）
5. `lumen-sev/.env-test.local` 不动（URL 由测试基类拼装）

## 任务 2.2 — `framework/dict/` 加法式拷入

血统源（只读参照）：`/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/birdbro-manage/src/main/java/org/birdbro/manage/dict/`
（27 文件）与随访验证形状 `/Users/birdbro/work/01-JF/00-gitlab/随访/fu_sev/fu-biz/src/main/java/org/birdbro/dict/`。

拷入到 `lumen-sev/framework/src/main/java/org/birdbro/framework/dict/`，**只拷**：
- `entity/DictCatalEntity.java`、`entity/DictDetailEntity.java`（**不拷** DictMenu/DictCity）
- `mapper/DictCatalMapper.java`、`mapper/DictDetailMapper.java`
- `service/IDictCatalService.java`、`IDictDetailService.java` + `service/impl/` 两实现
- 实体/mapper/service 签名引用到的 pojo（Catal/Detail 的 Create/Modify/Query 子集；
  引用多少拷多少，引用不到的不拷）
- 翻译助手（随访在 `common/fu`：DictLabelHelper/DictReverseHelper——先读内容，剥离
  fu 专属依赖后放入 `framework/dict/`；如依赖过重可最小化实现并在报告记录）

拷贝适配规则：
- 包名 `org.birdbro.manage.dict`（或 fu 的 `org.birdbro.dict`）→ `org.birdbro.framework.dict`
- `@TableName("dict_catal")`/`("dict_detail")` **不带 schema 限定**（search_path 解析）
- 实体继承 `EntityBase` 保持（随访形状）；类头 `@author birdbro` 保留
- 若 OPS 原件有 `ice.` 或 manage 专属依赖（如引用 birdbro-manage 其他类），剥离并在报告记录
- **加法式纪律**：framework 内现有文件一行都不改

## 完成标准与提交

1. RED 证据（迁移测试先红）→ V3 → GREEN
2. `./mvnw compile -q` exit 0；`./mvnw test -q` 全绿（既有 34 + 新增迁移/数据层测试），
   报告 MVN_EXIT 与 surefire 计数
3. git add + 单条 commit：
   `✨ feat(sev): system-data batch 1 - public tables + framework dict copy (system-data 1.1-2.2)`
4. 报告写 `openspec/sdd/reports/system-data-b1-report.md`（实现了什么 / RED+GREEN 证据 /
   文件清单 / 自审发现 / 偏差记录）

## 不要做
- 不写控制器/端点（Batch 2）；不接 `@Log`/`recordForSign`；不动 `dict_city`/`DictMenu`
- 不修改 `framework/log` 除两处 @TableName 外的任何行；不改 openspec/specs/

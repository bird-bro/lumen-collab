# Batch 2 Brief — rename-auth-to-system（tasks 2.1, 2.2, 3.1, 3.2, 3.3）

## 环境铁律（同前批）
JAVA_HOME 显式导出 + 绝对路径 cd + echo MVN_EXIT。**原子级联批**：git mv → 包根 →
路径 → dict 归位必须一气呵成——中间态编译不过属预期，以最终 L1+L3 绿为门。

## 前置状态（Batch 1 已交付）
- 测试服务器 schema 已是 `system`（flyway 历史随迁）；配置基座（bootstrap/测试基座
  URL/dev 文件+模板/application.yml flyway.schemas）已 system 化；pg-init 已更名；
  测试 SQL 的 `auth.` schema 前缀已扫成 `system.`
- 模块与包**尚未**更名（本批的事）

## Batch 1 复核交接（并入 2.1/2.2 一起做）
1. `AuthTestServerBootstrap.java:32-34`：`AUTH_SCHEMA` 常量名（值已是 system）→ 改名
   `SYSTEM_SCHEMA`？不——常量名保留 AUTH_SCHEMA 反而错位：改为 `SYSTEM_SCHEMA` 并同步
   `[auth-test-bootstrap]` 日志标签 → `[system-test-bootstrap]`，javadoc "lumen-auth
   service schema" → "lumen-system service schema"
2. **类名/文件名对齐**：`AuthOrgCrudTests.java:37` 声明的类名还是 `AuthUnitCrudTests`、
   `AuthOrgDepTests.java:38` 还是 `AuthDepartmentTests`（cdc58d2 只改了文件名）——
   类名改为与文件名一致（AuthOrgCrudTests/AuthOrgDepTests）
3. 冻结迁移文件里的 "auth" 注释字样：checksum 纪律**禁止编辑**——仅报告记录

## 任务 2.1 — 模块与包更名（原子）
1. `git mv lumen-sev/lumen-auth lumen-sev/lumen-system`
2. 包目录：main+test 下 `org/birdbro/lumen/auth/` → `org/birdbro/lumen/system/`
3. 全量替换：package 声明、import、全限定引用 `org.birdbro.lumen.auth.` → `...system`
4. `AuthApplication` → `SystemApplication`（类名 + 文件名 + javadoc）
5. aggregator `<module>lumen-auth</module>` → `<module>lumen-system</module>`；
   模块 pom artifactId/name → `lumen-system`；`spring.application.name` → `lumen-system`
6. Verify: `./mvnw compile -q` 绿——编译错误即残留清单，逐个清零

## 任务 2.2 — 文档 re-pin
- `.claude/rules/naming.md`：包根示例 `{auth,patient,...}` → `{system,patient,...}`
- `lumen-sev/CLAUDE.md`：模块清单 lumen-auth → lumen-system、端口表描述、auth-rbac
  之外的所有 lumen-auth 字样
- `openspec/verify.config.yaml`：backend 注释里 lumen-auth 测试语义字样
- `openspec/coordination/init-project-skeleton.md`：端口表 + `lumen_auth_role` 字样
  （若 Batch 1 后仍有残留）
- `docs/proposals/system-data.zh.md` 等活文档的 lumen-auth 字样顺手更名
  （**openspec/changes/archive/** 与 **openspec/specs/** 的历史/已归档内容不动——规格
  re-pin 是六份 delta 的归档职责）

## 任务 3.1 — 路径更名
- 控制器 `@RequestMapping("/api/v1/auth/...")` → `/api/v1/system/...`（7 个控制器）
- `security.permit-all`: `/api/v1/auth/login` → `/api/v1/system/login`
- 测试 URL 常量/字符串：`/api/v1/auth/` → `/api/v1/system/`（全部测试类）
- Verify: `rg -n "/api/v1/auth" lumen-sev/lumen-system/src` = 0

## 任务 3.2 — dict 归位（framework/dict → 服务五层）
- `framework/src/main/java/org/birdbro/framework/dict/` 16 文件迁入
  `lumen-system/src/main/java/org/birdbro/lumen/system/`：
  entity→`entity/`、mapper→`mapper/`、service(+impl)→`service/`(+impl)、pojo→`dto/`、
  DictLabelHelper/DictReverseHelper→`service/`
- 包名 `org.birdbro.framework.dict` → `org.birdbro.lumen.system.*`（对应层）
- **mvc 路径注意**：DictCatalController/DictDetailController 已在 lumen-system（Batch 2
  前就在），它们的 import 从 framework.dict 改为服务内包
- Verify: `rg -n "framework.dict" lumen-sev` = 0；`./mvnw compile -q` 绿

## 任务 3.3 — gitignore 路径
- `.gitignore`：`lumen-sev/lumen-auth/src/main/resources/application-dev.yml` →
  `lumen-system` 路径。Verify: git check-ignore 命中新路径。

## 完成标准与提交
1. L1 `./mvnw compile -q` 绿 + L3 全量 `./mvnw test -q` 绿（MVN_EXIT=0，50+ 全数通过）
2. `rg -n "lumen-auth|org.birdbro.lumen.auth|/api/v1/auth|framework.dict" lumen-sev`
   复查=0（注释性历史记录除外，报告列明）；`git status` 确认 application-dev.yml
   未入库
3. commit：`🔧 refactor(sev): rename lumen-auth -> lumen-system + /api/v1/system paths + dict relocation (rename-auth-to-system 2.1-3.3)`
4. 报告：`openspec/sdd/reports/rename-b2-report.md`（级联步骤、清扫清单、偏差）

## 不要做
- 不动 openspec/specs/（规格 re-pin 是六份 delta 的归档职责）；不动
  openspec/changes/archive/；不跑 ALTER SCHEMA（Batch 1 已完成）；不改 DictMenu/
  DictCity 范围决策

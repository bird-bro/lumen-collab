# Batch 2 Report — rename-auth-to-system（tasks 2.1, 2.2, 3.1, 3.2, 3.3）

> Status: DONE · Commit: `898382c` · 2026-11-03 · Executor: backend implementer agent

## 摘要

原子级联一次完成：`git mv lumen-auth → lumen-system`（模块/包根/入口类，历史 `--follow`
可追溯）→ 全量 package/import/路径清扫 → dict 16 文件从 `framework/dict` 归位服务五层
（log 写路径留守 framework，仅 4 处 javadoc 措辞 re-pin）→ 文档 re-pin → gitignore 路径。
Batch 1 复核交接 3 项并入完成。L1 `./mvnw clean compile -q` 绿（L1_EXIT=0）；L3 全量
`./mvnw test -q` MVN_EXIT=0（15 测试类 50/50，0 Failures / 0 Errors / 0 Skipped）。

## 1. 级联步骤（2.1）

1. `git mv lumen-sev/lumen-auth lumen-sev/lumen-system`（目录整体改名，target/logs 随迁后经
   `./mvnw clean` 清除陈旧产物）
2. `git mv` 包目录：main+test 下 `org/birdbro/lumen/auth/` → `org/birdbro/lumen/system/`
3. `git mv AuthApplication.java → SystemApplication.java` + 类名/`SpringApplication.run`
   引用/`MenuInfoMapper` javadoc 引用
4. 全量替换（perl 批扫，`git ls-files` 圈定范围）：package 声明、import、全限定引用
   `org.birdbro.lumen.auth.` → `...system`；`/api/v1/auth` → `/api/v1/system`；
   字面量 `lumen-auth` → `lumen-system`（模块 src 全部非冻结文件）
5. aggregator `<module>lumen-auth</module>` → `<module>lumen-system</module>`；模块 pom
   artifactId → `lumen-system`（`<name>${project.artifactId}</name>` 自动跟随）；
   `spring.application.name: lumen-system` + `logging.file.name: ./logs/lumen-system.log`

## 2. 路径清扫（3.1）

- 9 个控制器 `@RequestMapping("/api/v1/auth...")` → `/api/v1/system/...`（Auth/DictCatal/
  DictDetail/Log/Menu/Org/OrgDep/Role/UserInfo，brief 预估 7 个，实扫 9 处映射——
  MenuController 与 LogController 也在列）+ 各控制器 javadoc 内路径叙述
- `security.permit-all`: `/api/v1/auth/login` → `/api/v1/system/login`；注释 `/auth/me` →
  `/system/me`
- 全部测试类 URL 常量（LOGIN_URL / ORGS_URL / ORG_DEPS_URL 等）随批扫更新
- Verify: `rg -n "/api/v1/auth" lumen-sev` = 0（规格/归档目录未扫，属归档职责）

## 3. dict 归位（3.2）——16 文件 `git mv` 全数 R100 级追踪

| framework/dict | lumen-system 五层 |
|:--|:--|
| entity/DictCatalEntity, DictDetailEntity | `entity/` |
| mapper/DictCatalMapper, DictDetailMapper | `mapper/` |
| pojo/request/ Catal*3 + Detail*3 | `dto/` |
| service/IDictCatalService, IDictDetailService | `service/` |
| service/impl/DictCatalServiceImpl, DictDetailServiceImpl | `service/impl/` |
| DictLabelHelper, DictReverseHelper | `service/` |

包名映射：`framework.dict.entity/mapper/service(.impl)` → `lumen.system.同层`；
`framework.dict.pojo.request` → `lumen.system.dto`；helpers → `lumen.system.service`。
消费方（DictCatal/DictDetailController、DictCatal/DictDetailManageService(+Impl)、
AuthDataLayerTests）import 同批改指服务内包；`@MapperScan("org.birdbro.**.mapper")` 自动覆盖。
**log 写路径留守 framework**：LogAspect/AsyncFactory/ILogInfoService/entities/XMLs/
RequestContextConfig 零移动；framework 全树 `rg "framework.dict"` = 0，空目录已删。
framework 本批仅 4 个文件的**注释行** re-pin（SecurityAnonymousProperties、ILogInfoService、
SecurityBeanConfig、UserHeaderBridgeFilter 的 javadoc 路径/服务名示例），随后按 trap 协议
`./mvnw -pl lumen-bom,framework,common install -DskipTests` INSTALL_EXIT=0。

## 4. 文档 re-pin（2.2）

- `.claude/rules/naming.md`：包根示例 `{auth,...}` → `{system,...}`；应用类示例
  `org.birdbro.lumen.auth.AuthApplication` → `org.birdbro.lumen.system.SystemApplication`
- `lumen-sev/CLAUDE.md`：模块清单行 + schema 清单（`auth` → `system`，Batch 1 后的事实对齐，
  括注更名记录）；`auth-rbac-foundation` 字样按历史记录保留
- `lumen-sev/AGENTS.md`（brief 未列，Codex 镜像一致性顺手对齐）：同上两处
- `openspec/verify.config.yaml`：backend test 注释 lumen-auth → lumen-system
- `openspec/coordination/init-project-skeleton.md`：端口表 `lumen-auth | 8081` → lumen-system
  （`lumen_auth_role` Batch 1 已清零，无残留）；其余行（"UserHeaderBridgeFilter relocated
  from lumen-auth" 等）为历史决策记录，保留
- `docs/proposals/system-data.zh.md`：路径表 3 行 lumen-auth → lumen-system +
  `currentSchema=auth,public` → `system,public`（Batch 1 后事实）；第 24 行
  `framework/dict/**` 描述行未动（dict 归位叙事属本 change 归档/5.1 职责，报告列明）

## 5. Batch 1 复核交接（3 项）

1. `AuthTestServerBootstrap`：`AUTH_SCHEMA` → `SYSTEM_SCHEMA`（含 AuthIntegrationTestBase
   两处引用）；`[auth-test-bootstrap]` → `[system-test-bootstrap]` ×3；javadoc
   "lumen-auth service schema" → "lumen-system service schema"；异常消息同步
2. `AuthOrgCrudTests.java:37` `class AuthUnitCrudTests` → `AuthOrgCrudTests`；
   `AuthOrgDepTests.java:38` `class AuthDepartmentTests` → `AuthOrgDepTests`
3. 冻结迁移注释（V1:1 / V2:1 "lumen-auth" 字样）：**未编辑**，列为本报告允许残留

## 6. gitignore（3.3）

`.gitignore:21` → `lumen-sev/lumen-system/src/main/resources/application-dev.yml`；
`git check-ignore -v` 命中新路径。真实 dev 文件在盘未入库；仅模板 `.example` 随模块更名
（R100）。`.env-test.local` 未出现在任何暂存/提交（git show --name-only 复核）。

## 7. 偏差与说明

1. **提交曾含未清扫内容，已 amend 修复（同提交号序列内）**：首commit `ff671e1` 误将
   `git mv` 暂存的**原内容**（旧 package 声明）入库而批扫只落在工作区（111 files 仅
   +19/-19 异常暴露）。发现后 `git add lumen-sev/lumen-system` + `--amend` → `898382c`
   （+357/-357，R89%-R99% 相似度）。HEAD 内容复核：`git show HEAD:...SystemApplication.java`
   首行 `package org.birdbro.lumen.system;`；`git grep` 对 HEAD 复扫 = 仅 §5/§8 允许残留。
   测试即是对该工作区内容跑的（工作区 == HEAD），结果有效。
2. **`Auth*` 测试类族名保留**（含 `AuthApplicationTests`）：brief 仅要求对齐两处错位类名；
   整族 Auth* 前缀是测试族命名而非入口类引用（无 `AuthApplication.class` 符号引用），
   单改一个反而破坏族一致性。`AuthApplicationTests` 内部已更新：app-name 断言
   `"lumen-system"`（场景 4）+ javadoc 措辞。
3. **framework 4 文件 javadoc re-pin**：verify 残留清单要求 `/api/v1/auth|lumen-auth` 在
   lumen-sev 清零，framework 注释示例不在豁免清单——注释级改动 + install 协议已走。
   编译产物不变。
4. **首commit后陈旧运行时日志清理**：`lumen-sev/logs/lumen-auth.log`、
   `lumen-system/logs/lumen-auth.log`（均 gitignored）已删；新日志 lumen-system.log 由
   本批测试跑写入。
5. **全量计数 50**：与 Batch 1 一致（framework/common 无测试类，15 类全在 lumen-system）。
6. **git mv 历史验证**：`git log --follow SystemApplication.java` 追溯至 `6634be5`
   （init-backend-skeleton 3.1 lumen-auth service shell）。

## 8. 残留清单（`rg "lumen-auth|org.birdbro.lumen.auth|/api/v1/auth|framework.dict" lumen-sev`）

仅剩 4 处，全部属允许类别：

| 位置 | 类别 |
|:--|:--|
| `lumen-system/.../db/migration/V1__auth_schema.sql:1`、`V2__seed.sql:1` | 冻结迁移注释（checksum 纪律，交接项 3） |
| `lumen-sev/CLAUDE.md:25`、`AGENTS.md:25` "renamed from lumen-auth, 2026-11-03" | 本批刻意写入的更名出处标注（历史记录） |

文档侧已知未动项：`docs/proposals/web-foundation-auth-console.zh.md`（并行 change 的
未跟踪文件，不属本批）、`docs/proposals/init-project-skeleton.zh.md`（已完成 change 的
历史提案记录）、`openspec/changes/archive/` 与 `openspec/specs/`（规格 re-pin 是六份
delta 的归档职责，brief 明令不动）。

## 9. 完成标准对照

- [x] L1 `./mvnw clean compile -q` 绿（L1_EXIT=0）
- [x] L3 全量 `./mvnw test -q` MVN_EXIT=0（50/50：1+1+6+3+5+1+3+4+4+4+3+1+5+2+7）
- [x] 残留复查 = §8 清单（4 处，全部允许类别）；`/api/v1/auth`、`org.birdbro.lumen.auth`、
  `framework.dict` 在 lumen-sev 源码清零
- [x] commit `898382c`（111 files, +357/-357；git mv 全数 rename 追踪）；`.env-test.local` /
  `application-dev.yml` 未入库（check-ignore + git show 复核）
- [x] Batch 1 交接 3 项完成（§5）

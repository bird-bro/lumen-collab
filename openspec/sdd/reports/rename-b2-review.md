# Batch 2 Review — rename-auth-to-system（tasks 2.1, 2.2, 3.1, 3.2, 3.3）

> Verdict: **Approved** · Reviewer: code-reviewer agent · 2026-11-03 · Commit `898382c` (base `8d73970`)
> Severity: Critical 0 · Important 0 · Minor 2

## 1. Rename integrity（git mv 纪律）

- `git diff 8d73970..898382c --name-status -M`：**99 个 rename（R072–R100）+ 12 个 M，
  0 个 A/D** — 无 delete+recreate 模式，全数 rename 追踪。
- `git log --follow SystemApplication.java` 追溯至 `6634be5`（init-backend-skeleton 3.1
  lumen-auth service shell），与 brief 声明一致。
- main+test 下 `org/birdbro/lumen/auth/` 目录不复存在（find 0 命中）；包根唯一目录
  `org/birdbro/lumen/system/`。

## 2. 残留清扫（rg `lumen-auth|org\.birdbro\.lumen\.auth|/api/v1/auth|framework\.dict` lumen-sev）

恰 **4 处命中，全部属报告 §8 允许类别**，无未申报残留：

| 位置 | 类别 |
|:--|:--|
| `V1__auth_schema.sql:1` / `V2__seed.sql:1` | 冻结迁移注释（checksum 纪律）✔ |
| `lumen-sev/CLAUDE.md:25` / `AGENTS.md:25` "renamed from lumen-auth, 2026-11-03" | 刻意出处标注 ✔ |

`openspec/specs/`、`openspec/changes/archive/`、本 change 六份 delta 在该区间 diff 为
**0 文件** — 规格 re-pin 正确留给归档职责。

## 3. Dict 归位 + framework 纪律

- 16 文件全数在 lumen-system 五层实测到位：entity 2 / mapper 2 / dto 6（Catal*3 +
  Detail*3）/ service 4（IDictCatalService、IDictDetailService、DictLabelHelper、
  DictReverseHelper）/ service/impl 2；包名 `org.birdbro.lumen.system.*` 对应层 ✔。
- `framework/.../dict/` 目录不存在；framework 全树 `framework.dict` 引用 = 0。
- framework 区间 diff 恰 **4 文件且全部注释级**（ILogInfoService 1 行 javadoc、
  SecurityAnonymousProperties / SecurityBeanConfig / UserHeaderBridgeFilter javadoc）——
  LogAspect / AsyncFactory / log entity+mapper+XML 零触碰；全量测试绿即证实
  ILogInfoService 注入链（LogAspect → LogInfoServiceImpl）编译与装配完好。

## 4. 路径 / 身份

- 9 控制器 `@RequestMapping` 全部 `/api/v1/system/**`（Auth/DictCatal/DictDetail/Log/
  Menu/Org/OrgDep/Role/UserInfo）；`application.yml:55` permit-all = `/api/v1/system/login`；
  lumen-sev `/api/v1/auth` = 0；测试 URL 常量（LOGIN_URL/ORGS_URL 等）随扫 ✔。
- `AuthController` 保留原名仅路径变更 ✔；`SystemApplication` 在服务根包
  （scanBasePackages="org.birdbro" + `@MapperScan("org.birdbro.**.mapper")`）✔；
  `spring.application.name: lumen-system`（application.yml:14）+ `logging.file.name:
  ./logs/lumen-system.log`（:72-73）；aggregator `<module>lumen-system</module>`（pom:15）
  + 模块 artifactId `lumen-system` ✔。

## 5. 文档 re-pin / gitignore

naming.md（包根示例 + 应用类示例）、CLAUDE.md/AGENTS.md（模块清单 + schema 清单，含
更名出处括注）、verify.config.yaml 注释、init-project-skeleton.md 端口表（lumen-system |
8081）、docs/proposals/system-data.zh.md 路径表 — 全部实证 re-pin ✔。
`.gitignore:21` 新路径 `git check-ignore -v` 命中 ✔；`.env-test.local` /
application-dev.yml 未入库。

## 6. 测试独立复跑

`export JAVA_HOME=$(…jdk-11…) && cd lumen-sev && ./mvnw test -q` → **MVN_EXIT=0**；
surefire 汇总（15 个测试类）：**Tests run: 50, Failures: 0, Errors: 0, Skipped: 0** —
与报告一致。交接项实证：`AuthOrgCrudTests.java:37` 声明 `class AuthOrgCrudTests`、
`AuthOrgDepTests.java:38` 声明 `class AuthOrgDepTests`（文件名/类名已对齐）；
`AuthTestServerBootstrap` `SYSTEM_SCHEMA` 常量（:35）+ `[system-test-bootstrap]` 标签 ×3 +
`AuthIntegrationTestBase` 两处引用 ✔；`AuthApplicationTests:44` 断言 app-name
`"lumen-system"`（contract 场景 4）。

## 7. Amend 事故独立证实（报告 §7.1）

- reflog：`ff671e1 HEAD@{1} commit` → `898382c HEAD@{0} commit (amend)`；
  `git branch/tag --contains ff671e1` = 空 — 仅 reflog 可达，分支历史无引用。
- ff671e1 实为半清扫态：111 文件仅 +19/-19，其 `SystemApplication.java` 首行仍是
  `package org.birdbro.lumen.auth;`；898382c 为完整态 +357/-357。
- HEAD 抽查 3 文件（SystemApplication / AuthController / DictCatalEntity）+ aggregator
  pom 全部彻底 system 化；本次复核测试即跑在与 HEAD 一致的工作树（lumen-sev 干净），
  第 6 节结果对 amended 内容有效。

## Findings

- **Minor**（6 处 javadoc 旧入口类名）：`lumen-system/.../mapper/{UserInfoMapper:10,
  RoleInfoMapper:15, RoleMenuMapper:15, OrgInfoMapper:10, UserRoleMapper:10,
  OrgDepMapper:10}` 注释仍写 "AuthApplication's {@code @MapperScan(...)}" — 入口类已是
  SystemApplication。注释级、零功能影响，且在批次 rg 复查模式之外（模式不含裸
  `AuthApplication`）；报告 §1 仅列 MenuInfoMapper javadoc 已改，同类措辞漏扫 6 处。
  建议并入 5.1/归档前顺手改（6 行注释），或登记 spec-hygiene。
- **Minor**（已申报偏差，接受）：`Auth*` 测试族名保留（含 `AuthApplicationTests`）—
  报告 §7.2 论证成立：族命名一致性优先，无 `AuthApplication.class` 符号引用（复核
  rg 仅注释级命中，见上条），单改一个反破坏族一致。接受，无需处理。

## 结论

Batch 2（模块/包/入口类原子级联 + 路径清扫 + dict 归位 + 文档 re-pin + gitignore）与
contract D1/D2 映射表及场景 1-4/7/8 的代码侧要求一致：git mv 历史可追溯、清扫恰 4 处
允许残留、framework 加法式纪律守住（4 处注释级）、amend 事故自愈完整。50/50 全绿独立
复跑证实。**Approved** — 唯一遗留为 6 处注释级旧入口类名（Minor），不阻塞后续批次。

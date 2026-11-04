# Tasks

> 验收命令一律 `cd lumen-sev && ./mvnw ...`（JAVA_HOME 显式导出）；测试服务器
> `.env-test.local` 已配置。每批 L1+L3 全绿为门。

## 1. DB 与配置基座

- [x] 1.1 测试服务器执行 `ALTER SCHEMA auth RENAME TO system`（已改名则幂等跳过）；
  `AuthTestServerBootstrap` 建 `system` schema；`AuthIntegrationTestBase` 动态 URL、
  `application-dev.yml` + 模板 `currentSchema=system,public`。
- [x] 1.2 `pg-init.sql` 全文：schema `auth`→`system`、角色 `lumen_auth_role`→
  `lumen_system_role`、GRANT 段同步。Verify: grep 无旧字样。

## 2. 模块与包更名

- [x] 2.1 `git mv lumen-auth lumen-system`；aggregator `<module>`；pom
  artifactId/name；包目录 `org/birdbro/lumen/auth`→`.../system`；`AuthApplication`→
  `SystemApplication`（类名+文件名）；`spring.application.name`；全量 import/package
  声明替换。Verify: `./mvnw compile -q` 绿。
- [x] 2.2 文档 re-pin：naming.md 包根示例、lumen-sev/CLAUDE.md 模块清单与端口、
  verify.config.yaml 注释、协调文档端口表/角色字样。Verify: grep 无 `lumen-auth`
  残留（openspec 归档目录的历史记录除外）。

## 3. 路径与 dict 归位

- [x] 3.1 控制器路径 `/api/v1/auth/**`→`/api/v1/system/**`（含 `security.permit-all`
  与全部测试 URL 常量）。Verify: grep `/api/v1/auth` 仅剩规格历史记录。
- [x] 3.2 `framework/dict/**` 16 文件迁入 `lumen-system` 五层（entity/mapper/service/
  dto + 助手）；framework 编译不再含 dict。Verify: L1 绿。
- [x] 3.3 `.gitignore` 中 application-dev.yml 路径更新。Verify: git check-ignore。

## 4. 测试闭环

- [x] 4.1 全量测试 URL/schema/包名更新后 L3 全绿（50+）。Verify: MVN_EXIT=0。
- [x] 4.2 swagger 冒烟：`/api/v1/system/login` 200 + token，`/v3/api-docs` 路径全
  system 化。Verify: 实测记录。

## 5. 文档

- [x] 5.1 `docs/proposals/rename-auth-to-system.zh.md` 中文导读。

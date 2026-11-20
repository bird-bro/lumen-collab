# Tasks: rename-lumen-api-feign

## 1. Module identity

- [x] 1.1 `git mv lumen-sev/lumen-api lumen-sev/lumen-feign`；模块 pom artifactId + 头注释名同步
- [x] 1.2 聚合器 `lumen-sev/pom.xml` `<module>` 改名；`lumen-bom/pom.xml` dependencyManagement artifactId；`lumen-patient` / `lumen-us` pom 依赖 artifactId

## 2. Package root sweep

- [x] 2.1 模块内 13 文件：`package` 声明、package-info javadoc（含 "lumen-api" 字样）→ `org.birdbro.lumen.feign`
- [x] 2.2 消费方扫换：lumen-us（11 Java）、lumen-patient（6 Java）import / `@EnableFeignClients` / javadoc；lumen-system `NumberIssueRequest` javadoc 注释刷名

## 3. Config keys

- [x] 3.1 `@FeignClient` url 表达式与 javadoc：`lumen.api.patient.url` → `lumen.feign.patient.url`、`lumen.api.system.url` → `lumen.feign.system.url`；lumen-us `application.yml` 注释行
- [x] 3.2 整理用户本地 gitignored dev yml 手改清单（2 行键名），写入实施报告

## 4. Docs

- [x] 4.1 `lumen-sev/CLAUDE.md` + `AGENTS.md`：模块清单刷名；`{patient-api,integration-api}` 拆分写法 → 平铺 `lumen-feign`
- [x] 4.2 `.claude/rules/naming.md`、`openspec/README.md`、交接文档模块树（含 patient-api/integration-api 字样修正）
- [x] 4.3 活跃 change `us-station-registration-sev`：proposal(2) / design(2) / tasks(1) / execution-contract(1) / patient-domain delta(2) 引用刷新
- [x] 4.4 协调文档：`us-station-registration.md`(2) 刷名；`init-project-skeleton.md`(4) 刷名并注明 rename-lumen-api-feign

## 5. Spec deltas

- [x] 5.1 `specs/service-topology/spec.md` MODIFIED Foundation Module Set（模块清单名换，场景超集）
- [x] 5.2 `specs/number-rule/spec.md` MODIFIED Internal Service Issuance Contract（宿主名换，场景超集）

## 6. Verification

- [x] 6.1 `./mvnw compile -q` + `./mvnw test -q` 全量绿（JDK 11，reactor；共享测试库独占窗口）
- [x] 6.2 `@FeignClient` name/url 目标值 diff 核查零变更；活跃面 grep 残留核查（历史记录目录除外）
- [x] 6.3 verify.md（L1/L2/L3）

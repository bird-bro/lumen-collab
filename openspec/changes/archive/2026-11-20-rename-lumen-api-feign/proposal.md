# Proposal: rename-lumen-api-feign

## Why

`lumen-api` 名字有歧义：本项目里 "api" 已被 REST 面占用（每个服务 `/api/v1/**` + `openspec/specs/api/spec.md`），模块名读起来像 API 服务或网关，而它实际是**服务间 Feign 契约 jar**（OpenFeign 接口 + DTO + Sentinel fallback，非可执行，add-only）。消费方仅 lumen-patient / lumen-us，v1 未发布是改名成本最低的时刻。用户裁决 2026-11-20 定名 **`lumen-feign`**（"钉技术进名字"的顾虑不成立：同步查询 = OpenFeign + Sentinel 是架构铁律，真换 RPC 技术时整个 jar 重写，名字随之重命名即可）。

## What Changes

- **BREAKING**（编译期显性）：模块目录 + artifactId `lumen-api` → `lumen-feign`；包根 `org.birdbro.lumen.api` → `org.birdbro.lumen.feign`（`feign.patient` / `feign.number`）。
- 契约类名不动（`PatientApi` / `NumberApi` / 两个 fallback / 各 DTO）——Feign 接口 `*Api` 后缀是惯例；owner 与消费方机械扫换 import（模块内 13 文件 + 消费方约 29 文件）。
- 配置键同族扫换：`lumen.api.patient.url` → `lumen.feign.patient.url`、`lumen.api.system.url` → `lumen.feign.system.url`（`@FeignClient` url 表达式 + javadoc + lumen-us application.yml 注释；**用户本地 gitignored dev yml 需手改 2 行**，实施后给出确切行）。
- pom 触点：聚合器 `<module>`、lumen-bom dependencyManagement、lumen-patient、lumen-us（lumen-system 无编译依赖，仅 javadoc 注释刷名）。
- 文档同步：`lumen-sev/CLAUDE.md` + `AGENTS.md`（顺带把从未落地的 `{patient-api,integration-api}` 拆分写法改回平铺现实）、`.claude/rules/naming.md`、`openspec/README.md`、活跃 change `us-station-registration-sev` 文档引用（proposal / design / tasks / execution-contract / patient-domain delta）、活跃协调文档 `us-station-registration.md`、治理协调文档 `init-project-skeleton.md`、交接文档模块树。
- 线格式零变更：URL / payload / `@FeignClient` name 全部不动 → 前端兄弟 `us-station-registration-web` 不受影响。
- 历史记录不动：`openspec/sdd/reports/*`、`sdd/progress.md` 审核行、已归档 change 文档与协调文档（number-rule-unification / exam-setting）、`docs/analysis/`、`docs/proposals/`。

## Capabilities

### New Capabilities

- （无）

### Modified Capabilities

- `service-topology`: Foundation Module Set 模块清单中 `lumen-api` → `lumen-feign`（纯名称，行为不变）。
- `number-rule`: Internal Service Issuance Contract 契约宿主模块名 `lumen-api` → `lumen-feign`（行为不变）。

## Affected Stacks

- single-stack: lumen-sev

## Depends-On

- 无（独立机械改名）。coordinates with `us-station-registration`（实施期间该协调特性已由并行会话完成 web 侧并批量归档，commit 665303d——其活跃文档引用刷新随归档落定，归档副本即为 `lumen-feign` 名；归档提交定向 add 避开了本变更在途 WIP）。

## Success Criteria

- 全量 `./mvnw compile -q` + `./mvnw test -q`（JDK 11，reactor）exit 0，测试数不低于改前基线（426）。
- `@FeignClient` 的 name 与 url 目标值逐字节不变（线格式零变更）。
- 活跃面 grep 零残留：`openspec/specs/`、`.claude/`、`lumen-sev/`、活跃 change 与活跃/治理协调文档、交接文档（历史记录目录除外）。

## Risks

- 包根扫换的编译断裂全部显性（import 失败），全量编译兜底，无静默路径。
- 配置键改名后若本地 dev yml 未同步：`${...:}` 空串兜底、调用期才报错——实施报告中单独列出用户手改清单。
- 全量测试需共享测试库独占窗口（避开并行会话的测试运行）。

# Design

## Context

`init-backend-skeleton` 的零基础设施保证即将到期：lumen-auth 是第一个真用 PostgreSQL 的
change。部署蓝本已在 `docs/analysis/hospital-deployment-forms.zh.md` §5.6 定稿，本 change
只落地其基础设施子集。用户决策：仅基础设施容器、含 Nacos、六库全建、PG 对外端口自定义
（以 15432 默认经 `.env` 可调）。

## Goals / Non-Goals

**Goals:** 三容器 + 六库 + 命名卷 + healthcheck + `.env` 可调端口；为 lumen-auth 提供真 PG。
**Non-Goals:** dcm4chee、lumen 服务容器、TLS、备份、生产加固。

## Decisions

### D1 - 蓝本来源与容器清单
`docs/analysis/hospital-deployment-forms.zh.md` §5.6 的基础设施行：postgres(15432) ·
nacos :8848 仅配置中心（standalone，derby 卷）· redis(6379)。**不含** Java 网关（nginx 归
部署 change）、不含 lumen 服务容器（auth 成为可部署 jar——需要 spring-boot repackage——后
由其自己的 change 加入 compose）。

### D2 - 无凭证入库
`.env` gitignored；`.env.example` 只含占位（`PG_PASSWORD=change-me`）。沿用行为 16。

### D3 - 六库初始化走镜像 init 目录
`docker/init/01-create-dbs.sql` 挂载到 postgres 镜像 `/docker-entrypoint-initdb.d/`，用
`CREATE DATABASE` + 存在性守卫（psql `\gexec` 模式）幂等建库。选镜像 init 而非独立
migrate 容器：零额外容器、首次启动即生效、官方支持路径。

### D4 - Nacos standalone + derby 卷
医院形态 Nacos 仅配置中心；standalone 模式 + derby 持久化（`nacos-derby` 命名卷）足够，
不引入 MySQL 依赖。

## Risks / Trade-offs

- **Docker daemon 未运行时 up 不可验证** → `docker compose config -q` 为最低门禁（无需
  daemon）；up 冒烟为可选任务（daemon 可用时执行）。
- **端口 15432 非标准** → 正是用户选择（避开宿主机既有 PG）；`.env` 一处可改。
- **derby 单文件存储** → 医院单机形态可接受；备份策略归部署 change。

## Migration Plan
新增目录，无迁移。回滚 = 删除 `docker/`。

## Open Questions
无。

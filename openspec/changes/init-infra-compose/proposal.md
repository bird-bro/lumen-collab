# Proposal: init-infra-compose

## Why

`init-backend-skeleton` 的验证保证是"零基础设施下测试通过"——但下一个 change（lumen-auth）是第一个真用 PostgreSQL 的 change（Flyway + mapper + 真连接），没有真 PG 就没有可执行的 SQL 验证。医院形态的基础设施蓝本已在 `docs/analysis/hospital-deployment-forms.zh.md` §5.6 定稿（PG + Nacos 配置中心 + Redis，nginx 入口，无 Java 网关），本 change 把蓝本中最小可用部分落地为 `docker/` compose。

## What Changes

- 创建 `docker/`：`docker-compose.yml`（postgres + nacos + redis 三容器，healthcheck 齐备）+ `.env.example`（端口/口令全部经变量，PG 对外 15432）+ `init/` PG 初始化脚本（**一次建齐六库**：lumen_auth / lumen_patient / lumen_radiology / lumen_us / lumen_endo / lumen_integration——DB-per-service 直接物化）+ `README.md`
- 数据卷：命名卷（pg-data、nacos-derby、redis-data），断电自启由 `restart: unless-stopped` 承担
- **不含**：dcm4chee-arc-light（独立重活，另 change）、lumen 服务容器（auth 成为可部署 jar 后加入）、TLS/生产加固

## Capabilities

### New Capabilities
- `infra-compose`: 医院形态基础设施栈的 compose 契约——容器清单、端口契约（经 `.env` 可调）、六库初始化、持久化与健康检查。

### Modified Capabilities
无。

## Affected Stacks
single-stack: docker（基础设施；无 Java/前端源码变更）

## Depends-On
无前置。下游：`lumen-auth` change 消费本栈（JDBC 指向 :15432/lumen_auth，Nacos 配置中心 :8848，Redis :6379）。

## Scope

- In scope: `docker/docker-compose.yml`、`docker/.env.example`、`docker/init/01-create-dbs.sql`、`docker/README.md`
- Out of scope: dcm4chee-arc-light、任何 lumen 服务容器、TLS/反向代理、备份脚本、生产加固

## Success Criteria

- `docker compose config -q` exit 0（无需 daemon）
- `docker compose up -d` 且三容器 healthy 后：六库存在（psql 验证）、Nacos :8848 可达、Redis PING 返回 PONG
- `.env` 修改端口后 compose 跟随（可调性）
- 无任何明文生产口令入库（`.env` 在 .gitignore，`.env.example` 只含占位）

## Constraints

- 医院形态对齐：无 Java 网关容器；Nacos 仅配置中心身份（standalone）
- 敏感配置排除（沿用行为 16）：口令走 `.env`（gitignored），`.env.example` 占位
- 兼容 B4 约束：lumen-sev 测试仍不依赖本栈（零基础设施保证不变）

## Risks

- Docker daemon 未运行时 up 不可验证 → `docker compose config -q` 作为无需 daemon 的最低门禁；up 冒烟列为可选任务
- 六库一次建齐 vs 渐进建库 → 建库脚本幂等（CREATE DATABASE 加存在性守卫），后续 change 零成本

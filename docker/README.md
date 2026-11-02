# LumenCollab `docker/` 目录

> **本目录不部署数据服务。** PostgreSQL / Nacos / Redis 由医院中心机房单独的机器（或云端
> 独立实例，如阿里云 RDS/MSE）提供——lumen 从不自己部署这些基础服务（2026-10-30 用户决策，
> 取代 init-infra-compose 的容器化前提）。

## 环境契约（数据服务由环境提供）
lumen 服务通过环境变量/Nacos 消费外部地址，从不写死：
| 服务 | 环境提供 | 服务消费方式 |
|---|---|---|
| PostgreSQL | 实例 + 六个业务库（建库脚本：`docs/deployment/pg-init.sql`，交 DBA 执行）| `spring.datasource.url/password` 走 env/Nacos |
| Nacos | 配置中心地址 | `bootstrap.yml` `${NACOS_ADDR:...}` |
| Redis | 实例地址 | 应用配置（token 状态/缓存） |

完整契约规格：`openspec/specs/environment-contract/spec.md`

## 本目录将来放什么
lumen 服务成为可执行 jar（spring-boot repackage）后：服务容器定义 + nginx 统一入口配置。
nginx 由部署 change 提供（医院形态无 Java 网关）。

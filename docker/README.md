# LumenCollab hospital-form infrastructure

## 容器清单（init-infra-compose）
| 容器 | 用途 | 端口（.env 可调） | 数据卷 |
|---|---|---|---|
| postgres:16-alpine | 六个 per-service DB（DB-per-service 物化） | `${PG_PORT:-15432}` → 5432 | `pg-data` |
| nacos-server v1.4.2 | **仅配置中心**（standalone，derby） | `${NACOS_PORT:-8848}` → 8848 | `nacos-derby` |
| redis:7-alpine | token 状态/缓存（lumen-auth 起） | `${REDIS_PORT:-6379}` → 6379 | `redis-data` |

## 使用
```bash
cd docker
cp .env.example .env   # 设置 PG_PASSWORD
docker compose up -d   # 首次启动自动建六库
```

## lumen 服务如何加入（后续 change）
- 服务成为可执行 jar（spring-boot repackage）后，在此 compose 增加服务容器：
  `SPRING_PROFILES_ACTIVE=hospital` + `bootstrap.yml` 的 `NACOS_ADDR=nacos:8848`
- JDBC 示例（lumen-auth）：`jdbc:postgresql://postgres:5432/lumen_auth`
- 统一入口 nginx 由部署 change 提供（本 compose 无网关容器——医院形态决策）

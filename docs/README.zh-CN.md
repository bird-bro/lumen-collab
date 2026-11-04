# LumenCollab

**LumenCollab（流明协作）** 是一个开源医学影像协作平台。它与久经考验的开源 PACS
[dcm4chee-arc-light](https://github.com/dcm4che/dcm4chee-arc-light) 配套而非替代：影像留在
PACS（通过 QIDO-RS 按 Accession Number 关联），LumenCollab 负责影像之外的临床流程——v1
交付放射科信息系统（RIS），超声、内镜科室随后以独立微服务跟进。所有科室共用同一个检查流程
内核（预约→登记→检查→报告）、同一套认证与 RBAC 基座（JWT 会话、菜单树权限与按钮级门控）、
同一层系统数据（机构树、科室、用户、角色、字典、操作日志）。项目全程采用规格驱动开发
（OpenSpec）——每条行为契约都可测试，65+ 集成测试对着真实基础设施验证登录、权限、审计身份
与参照数据。如果你的医院在用 dcm4chee，又需要一个轻量、可读、能说 DICOM MWL/MPPS 与 HL7v2
的 RIS——LumenCollab 就是为这个缺口而建。

> English documentation: [README.md](../README.md)

**状态：🚧 积极开发中。** 后端 *system 服务*（认证、RBAC、机构树、用户、角色、菜单、
字典、操作日志）已实现、验证并归档规格；Web 控制台是当前进行中的工作。

## v1 能做什么

- **检查流程** —— 登记 → 检查 → 报告，沉淀为可复用内核（`lumen-exam-core`），各科室
  服务共享扩展
- **多机构就绪** —— 层级机构树（医院根 + 子机构），科室挂靠其上
- **RBAC** —— 用户、角色、菜单树（目录/菜单/按钮）+ `perms` 权限串，支撑动态导航与
  按钮级门控
- **系统数据** —— 字典（label↔value）与操作/登录日志，落于 `public` schema 全平台共享
- **对接 PACS** —— 按 Accession Number 经 dcm4chee-arc-light 的 QIDO-RS 关联影像；
  MWL/MPPS + HL7v2 由独立集成服务负责

## 仓库结构

| 路径 | 内容 |
|:--|:--|
| `lumen-sev/` | 后端 —— Maven 多模块聚合器（Java 11 · Spring Boot 2.5.15） |
| ├ `lumen-bom` | 唯一版本源（OPS 验证过的版本火车，1:1 钉版） |
| ├ `framework` / `common` | OPS 血统共享平台层：安全链（JWT 过滤器、token 服务）、统一信封、审计填充、字典/日志机制、A 类错误码 |
| ├ `lumen-system` | **当前唯一活服务** —— 认证 + RBAC + 机构/科室/用户/角色/菜单 + 字典/日志管理（端口 8081） |
| `lumen-web/` | 前端 SPA（React 18 · TypeScript · Vite · Ant Design 5，pnpm） |
| `docker/` | 预留给 lumen 应用容器 + nginx 入口——**从不部署数据服务** |
| `openspec/` | 规格驱动开发：`specs/`（权威行为契约）+ `changes/`（提案 → apply → verify → archive） |
| `docs/` | 部署交付（`pg-init.sql`）、提案中文导读（`proposals/*.zh.md`）、指南（`GUIDE.zh.md`） |

**数据模型** —— 单库 `lumen`，每服务一个 schema（`system` / `patient` / `radiology` /
`us` / `endo` / `integration`），每服务一个连接角色（GRANT 限定），`public` schema 放
跨服务共享的系统表。Redis 存 token 状态。**入口** —— nginx（80/443）路由
`/api/v1/<service>/**`；无 Java 网关。**配置** —— 提交的 `application.yml`（零凭据）+
每台机器 gitignored 的 `application-dev.yml`；无注册中心/配置中心。

## 快速开始

前置：JDK 11、PostgreSQL 14+、Redis、Node 18+ 与 pnpm。

```bash
# 1. 数据库（以超级用户执行——建 lumen 库、各服务 schema、角色模板）
psql -f docs/deployment/pg-init.sql

# 2. 后端配置
cp lumen-sev/lumen-system/src/main/resources/application-dev.yml.example \
   lumen-sev/lumen-system/src/main/resources/application-dev.yml
#    → 填入你的 PostgreSQL/Redis 地址与凭据（gitignored，绝不提交）

# 3. 构建并运行后端（端口 8081）
cd lumen-sev
./mvnw install -DskipTests
./mvnw -pl lumen-system org.springframework.boot:spring-boot-maven-plugin:run

# 4. Swagger UI
open http://localhost:8081/swagger-ui/index.html

# 5. 前端
cd ../lumen-web && pnpm install && pnpm dev
```

种子管理员：`admin` / `admin123` —— 带 must-change-password 标记，首次登录请改密。

### 测试

```bash
cd lumen-sev
# 创建 .env-test.local（gitignored）写入 AUTH_TEST_* 变量，或直接导出环境变量：
#   AUTH_TEST_PG_HOST / AUTH_TEST_PG_PORT / AUTH_TEST_PG_USER / AUTH_TEST_PG_PASSWORD
#   AUTH_TEST_DB（可选，默认 lumen）
#   AUTH_TEST_REDIS_HOST / AUTH_TEST_REDIS_PORT / AUTH_TEST_REDIS_PASSWORD（可选）
./mvnw test
```

后端测试直连真实 PostgreSQL + Redis（团队测试服务器或本地一对）——连接信息来自
gitignored 的 `.env-test.local`（`AUTH_TEST_*` 变量）。未配置环境会显式失败，
绝不静默跳过。

## 本仓库的运转方式

开发是**规格先行**（OpenSpec）：`openspec/specs/` 是权威的 WHEN/THEN 行为契约；每个
change 走 提案 → 设计 → 任务 → TDD 实现 → 三层验证 → 归档，归档时同步主规格。
`CLAUDE.md` / `AGENTS.md` 是 AI 编码代理的入口。中文提案导读在
`docs/proposals/*.zh.md`；指南见 `docs/GUIDE.zh.md`。

## 路线图

- [x] `lumen-system` —— 认证闭环（JWT + Redis 会话）、RBAC、机构树、科室、用户、
      角色 + 菜单分配、字典、操作/登录日志
- [ ] Web 控制台 —— OPS 风格设计系统、认证流、动态导航、系统管理页面（*进行中*）
- [ ] `lumen-patient` —— EMPI（主患者索引）
- [ ] 放射科（v1 核心）—— `lumen-exam-core` 检查流程、`lumen-integration` 的 DICOM
      MWL/MPPS + HL7v2、dcm4chee-arc-light 对接
- [ ] 超声 / 内镜科室服务

## 许可证

[Apache-2.0](../LICENSE) —— 另见 [NOTICE](../NOTICE)。

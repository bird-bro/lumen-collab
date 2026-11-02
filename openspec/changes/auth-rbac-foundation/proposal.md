# Proposal: auth-rbac-foundation

## Why

`lumen-auth` 是骨架里唯一的空壳服务，而整个平台现在没有任何登录、任何用户、任何权限——
前端三个占位页无守卫，EntityBase 的 `create_by`/`update_by` 悬空无语义，framework 里照搬的
`TokenService`/`JwtAuthenticationTokenFilter`/`SecurityConfig` 没有签发方。本 change 让
lumen-auth 成为第一个真服务：机构层级树（多机构能力，参照随访 `unit_info`）、用户/角色、
菜单+按钮权限（随访已完整实现、OPS/RCP 从未有过的部分）、JWT 签发-验证闭环、首批 Flyway
迁移，以及第一个真实 REST 端点。

## What Changes

- `lumen-auth` 激活数据层：连接团队测试服务器 PG + 首批 Flyway 迁移（7 张表：
  `unit_info` 层级树、`user_info`、`role`、`user_role`、`role_menu`、`menu_info`、
  `department`）+ 种子数据（默认管理员 + 三科室 radiology/us/endo）
- 登录闭环：`POST /api/v1/auth/login`（首个真实端点）→ 拷贝来的 `TokenService` 签发 JWT
  （Redis 存 token 状态，Testcontainers Redis）→ `JwtAuthenticationTokenFilter` 验证
- 管理 CRUD：用户 / 角色（含角色-菜单分配）/ 菜单 / 机构 / 科室 的端点与服务
- EntityBase 与 copied-code 语义对齐：主键采用 **Integer 自增**（用户决策 2026-11-02，
  随访/OPS 同款）——**这与主规格 data 的 "bigint 雪花" 条款冲突**，由本 change 的 data
  MODIFIED 增量对齐；`create_by`/`update_by` 保持 Integer 不升宽（协调文档既有 flag 就此
  关闭为"维持 Integer"）
- service-topology 的"零基础设施测试"条款修订：lumen-auth 的测试直连**团队测试服务器**
  （PG + Redis，外部供给），连接走 gitignored `lumen-sev/.env-test.local`——未配置 =
  显式失败；其他模块（framework/common）的零依赖测试不变（2026-11-02 重定，用户裁定）
- 随访模式移植：`menu_info`（type=目录/菜单/按钮 + perms 权限串）、`role_menu`/`user_role`
  绑定、`unit_info` 层级树（`upId`/`level`）、`UnitTree` 树形响应——common 里已照搬的
  `MenuTree` POJO 首次投入使用

## Capabilities

### New Capabilities
- `auth-rbac`: 认证与 RBAC 基础——登录签发/验证闭环、机构层级树、用户/角色/菜单/按钮
  权限模型（含种子数据与首管理员）、科室（独立表 FK unit 树）。

### Modified Capabilities
- `data`: "Entity Schema Documentation" 的 id 子句改为 Integer 自增（用户决策；雪花子句
  移除），`create_by`/`update_by` 维持 Integer。
- `service-topology`: "Context Load Without Infrastructure" 修订为"不要求已部署基础设施；
  lumen-auth 测试允许 Testcontainers 临时容器（PG+Redis）"，其余模块零依赖不变。
- `api`: 新增第一个具体端点文档 `POST /api/v1/auth/login`（同时使主规格
  `Endpoint Documentation` 的陈旧 `code: 0` 示例获得对齐基准）。

## Affected Stacks
single-stack: backend (`lumen-sev/`，仅 `lumen-auth` 模块 + 少量 copied-code 语义注释)

## Depends-On
`init-backend-skeleton`（archived）、`init-infra-compose` 的环境契约
（`environment-contract`：外部 PG/Nacos/Redis，连接走环境变量）。

## Scope

- In scope:
  - `lumen-auth` 的 Flyway 迁移、实体/Mapper/Service、auth 控制器与管理 CRUD 端点
  - 测试基类：启动自举（建库/schema 幂等）+ 连接走 gitignored env 文件
  - EntityBase `@MapperScan` 既有契约下的 mappers；种子数据迁移
  - api 增量：login 端点文档；data/service-topology 修订增量
- Out of scope:
  - `DataScope` 行级数据权限（排队至 radiology/exam change，参照随访 `scope/`）
  - 前端登录页/动态菜单/`<HasPermi>` 组件（紧随的 cross-stack 前端 change）
  - OAuth2/SSO、密码策略、菜单管理 UI
  - 其他服务的 JWT 验证接线（framework 过滤器已就位，各服务接入随各自 change）

## Success Criteria

- `cd lumen-sev && ./mvnw test -q`（Docker daemon 运行时）：Testcontainers 拉 PG+Redis，
  Flyway 迁移执行，全部测试绿
- 登录端点集成测试：正确凭据 → `code:"200"` + JWT；错误凭据 → 字母码错误；无效 token →
  `"203"` 信封拒绝（拷贝件 `AuthenticationEntryPointImpl` 语义，用户裁定 2026-11-02）
- 种子验证：默认管理员可登录、三科室存在
- `create_by` 语义验证：登录用户创建数据时 `MetaObjectConfig` 填入其 id
- `openspec validate auth-rbac-foundation --strict` 通过

## Constraints

- 照搬纪律：`TokenService`/`JwtAuthenticationTokenFilter`/`SecurityConfig`/`MetaObjectConfig`
  按骨架已登记适配使用；新代码走 `org.birdbro.lumen.auth` 分层包
- 主键 Integer 自增（用户决策）——数据规格由 MODIFIED 增量对齐
- 敏感配置排除：DB/Redis 连接走 Testcontainers 动态注入（测试）与环境变量（运行）
- 验收命令一律 `cd lumen-sev && ./mvnw ...`（可移植写法）

## Risks

- **测试直连共享测试服务器**——凭据走 gitignored 文件不入库；测试数据累积/清理属测试库用途
- **Integer 主键 = 放弃雪花**：多院区数据合并将来需额外策略（云端产品提案时重议）
- **首次真端点**：envelope/错误处理的运行时行为首次可观察——与照搬实现的偏差（若有）
  在本 change 内暴露并修
- **TokenService 的 Redis 依赖**：登录闭环需要 Testcontainers Redis（已列入）

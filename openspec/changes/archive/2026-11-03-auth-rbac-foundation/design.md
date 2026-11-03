# Design

## Context

`lumen-auth` 是空壳；framework 里的安全机器（`TokenService`/`JwtAuthenticationTokenFilter`/
`SecurityConfig`/`MetaObjectConfig`）已照搬并通过骨架验证。用户四项拍板（2026-11-02）：
主键 Integer 自增（随访/OPS 血统）、科室独立表 FK org（表命名经 2026-11-03 更名，见 D2）、Testcontainers 测试 PG、带种子
数据。随访（`fu_sev`，同血统）是 menu/unit/scope 机制的参考实现——`MenuTree` POJO 已在
common 照搬件中。本 change 产出平台**第一个真实端点**，同时修订两条既有规格条款。

## Goals / Non-Goals

**Goals:**
- JWT 签发-验证闭环（登录 → token → 过滤器验证 → 审计身份填充），全链路测试实证
- 机构层级树 / 用户 / 角色 / 菜单（页面+按钮）/ 科室 的表结构与端点
- Testcontainers 测试基建：Flyway + mapper 在真 PG 上执行
- 种子：默认管理员 + 三科室，装完即可登录

**Non-Goals:**
- `DataScope` 行级数据权限（排队 radiology/exam change，参照随访 `scope/`）
- 前端登录页/动态菜单/`<HasPermi>`（紧随的 cross-stack change）
- OAuth2/SSO、密码策略、菜单管理 UI、其他服务的 JWT 接线

## Decisions

### D1 - 主键 Integer 自增（用户决策），规格经 MODIFIED 对齐
全部新表 `id Integer AUTO`（随访/OPS 血统）；`EntityBase.create_by/update_by` 维持
Integer——协调文档既有的"升宽 bigint"flag 就此关闭为"维持 Integer"。
**主规格 data 的 "bigint 雪花" 条款与本决策冲突**，由本 change 的 data MODIFIED 增量
对齐（雪花子句撤回）。多院区数据合并将来若需全局唯一 id，由云端产品提案重议。

### D2 - 科室独立表 FK org 树（表命名：用户决策 2026-11-03）
`org_info` 只承载组织层级（`upId`/`level`/`type`，随访形状）；`org_dep` 独立表
FK `org_info`，医疗属性（模态、检查类型——radiology change 加入）归属科室表。
组织管理与医疗语义分离，org 树两个产品共用。
表命名（用户决策 2026-11-03）：英文 `unit` 语义是计量单位而非机构，机构一律 `org`
（`unit_info`→`org_info`、`department`→`org_dep` 表达从属、`role`→`role_info` 对齐
`user_info`/`menu_info` 的 `_info` 后缀规则）；URL 跟随（`/orgs`、`/org-deps`）。
随访 `unit_info`/`UnitTree` 仅作模式参照，不改照搬件。

### D3 - 测试直连团队测试服务器（PG + Redis），连接走 gitignored env 文件
用户提供测试服务器（PG 220.181.78.238:54322 · Redis :16379）。测试基类：
1. 启动自举——连 `postgres` 维护库，`CREATE DATABASE lumen`（存在性守卫）+ 建 `auth`
   schema（幂等）——测试套件在任何全新 PG 上自举，无需 DBA 预建；
2. 应用数据源 `currentSchema=auth` 连 `lumen` 库，Flyway 对真库执行；
3. 连接信息从 gitignored `lumen-sev/.env-test.local` 读取（`AUTH_TEST_*` 变量，行为 16）；
   **未配置 = 测试显式失败**（占位符解析错误），绝不静默跳过。
`lumen-bom` 不再需要 Testcontainers 登记。Redis 走测试服务器的 :16379（TokenService 的
token 状态在测试中真实生效）。

### D4 - 种子走 Flyway 数据迁移
`V1` 建表、`V2` 种子（默认管理员 admin——BCrypt 哈希口令 + must-change-password 标记，
三科室 radiology/us/endo 挂医院根）。迁移即幂等（Flyway 版本化语义）。BCrypt 用类路径上的
Spring Security `BCryptPasswordEncoder`（随访验证过的替代：RuoYi 自带 MD5 已弃）。

### D5 - 菜单模型移植随访形状
`menu_info`：`upId` 树 + `type`（目录/菜单/按钮）+ `perms` 串 + path/component/icon +
sort；`role_menu`/`user_role` 绑定表。登录与 `/auth/me` 返回菜单树 + perms 集合——前端
动态菜单与按钮门控的数据契约。common 照搬件中的 `MenuTree` POJO 首次投入使用。

### D6 - Token 流（照搬机器的接线）
登录 → `SecurityUtils`/`BCrypt` 验密 → `TokenService.createToken`（JWT，token 状态入
Redis `RedisCache`）→ 响应 `data` 携带 token+profile；受保护端点由
`JwtAuthenticationTokenFilter` 验 token 并填充安全上下文。`SecurityConfig` 的过滤器链在
lumen-auth 生效（适配 (b) 的闭环兑现）。`/auth/login` 本身放行（`@Anonymous`-等价：OPS
的 `IgnoreResponseAdvice`/匿名放行机制以拷贝件实际实现为准，apply 时核对）。

### D7 - 模块布局（入口类契约的第一次实例化）
代码全部在 `org.birdbro.lumen.auth.{controller,service,mapper,entity,dto}`；
`AuthApplication` 既有契约不变（`scanBasePackages="org.birdbro"` +
`@MapperScan("org.birdbro.**.mapper")`）；Flyway 在 `lumen-auth/src/main/resources/db/migration`。

### D8 - 配置本地化，取消 Nacos（用户决策 2026-11-03，随访 fu-launch 同形）
移除 `spring-cloud-starter-alibaba-nacos-discovery` / `nacos-config` /
`spring-cloud-starter-bootstrap` 三个依赖与 `bootstrap.yml`；配置 = 提交的
`application.yml`（framework `@Value` 需求的 `token.*`/`xss.*`/`dispose.*` + 服务身份
8081 + mybatis-plus/jackson/logging 本地默认，零凭据）+ gitignored
`application-dev.yml`（druid 数据源 + Redis 凭据，提交模板
`application-dev.yml.example`，与随访 `fu-launch` 完全同形）。service-topology 主规格的
"Context Load Without Infrastructure"（注册中心/配置中心场景重写为"栈内不存在"）与
"Sensitive Configuration Exclusion"（env 变量或 gitignored profile 文件）由本 change 的
service-topology delta 对齐。`lumen-bom` 保留 SCA/Spring Cloud 版本管控（未来 cloud-form
change 不受影响）；OpenFeign/Sentinel 是后续 change 的独立事项。

### D9 - 安全装配平台化（用户裁定 2026-11-03：framework/common 可在 OPS 血统基础上演进）
裁定放宽了 2026-10-29 的 verbatim-only 约束：共享平台层可调整优化，OPS 原仓库保持
不动作为 re-diff 基线；演进一律**加法式**（新 framework/common 文件优先于改 OPS 老文件
行）——未来上游同步零冲突。落地（适配 (6)，记入协调文档）：
1. `UserHeaderBridgeFilter` 从 lumen-auth 上移 `framework/security/filter/`——无 Java
   网关部署下 `MetaObjectConfig` 审计身份桥接是所有 Servlet 服务的共性需求，经组件
   扫描自动注册，后续服务开箱即得；
2. lumen-auth 删除 `AuthWebSecurityConfig`：匿名路径由 framework 新增件配置化
   （`security.permit-all` + `AnonymousPathCustomizer`/`SecurityAnonymousProperties`，
   拷贝件 `SecurityConfig` 的 permitAll 清单保持 verbatim），共享 `BCryptPasswordEncoder`
   bean 上移 `SecurityBeanConfig`——服务只声明自己拥有的匿名端点。

## Risks / Trade-offs

- **测试直连共享测试服务器** → Flyway 在其 `lumen` 库建七表（专用测试库，非生产）；测试
  产生的业务数据由测试自行清理或累积于测试库（属测试库用途）；凭据仅存 gitignored
  本地文件。daemon 不再是测试前置（较 Testcontainers 方案简化）。
- **Integer 主键放弃雪花** → 多院区合并将来需策略；云端产品提案重议。
- **首次真端点暴露照搬代码的运行时行为** → envelope/异常/审计填充首次可观察；与规格的
  偏差在本 change 内修，不得带病归档。
- **BCrypt 与随访口令互认**：不追求跨项目口令互通，lumen 自管。
- **Flyway 7.7.3（Boot 2.5.15 管控）× 测试服务器 PG 14.11**：仅 WARN（未官方支持 14，
  反编译证实不抛错），迁移全为普通 DDL、套件绿——接受为零版本偏离；若后续迁移真触发
  不兼容，修复路径是 lumen-only 的 flyway-core 钉版提案（7.15.0 起 PG 14），不走静默升版。

## Migration Plan

`lumen-auth` 从壳变服务；`verify.config.yaml` 不变（test 命令仍是 `./mvnw test -q`，语义
新增"需要 Docker daemon"）；`openspec/verify.config.yaml` 的 backend L3 语义随之更新。
回滚：删除 lumen-auth 的新增源码与迁移，恢复壳状态。

## Open Questions

无——四项拍板 + 随访参照已足。菜单表的具体列以随访 `menu_info` 为基、apply 时按 lumen
需要裁剪（记录于任务 2.x 的证据）。

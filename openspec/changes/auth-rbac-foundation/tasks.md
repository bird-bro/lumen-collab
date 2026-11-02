# Tasks

> TDD：每个行为先红后绿（测试即红绿载体，直连团队测试服务器）。验收命令一律
> `cd lumen-sev && ./mvnw ...`；连接凭据走 gitignored `lumen-sev/.env-test.local`，
> 未配置 = 显式失败（设计 D3，用户裁定 2026-11-02）。

## 1. 测试基建（测试服务器直连）

- [x] 1.1 创建 gitignored `lumen-sev/.env-test.local`（`AUTH_TEST_*` 变量，用户提供）并加
  .gitignore；测试基类从该文件/环境读取连接。Verify: 文件存在且 `git check-ignore` 命中；
  变量名与 design D3 一致。
- [x] 1.2 写 `AuthIntegrationTestBase`（抽象基类）：启动自举（连 `postgres` 维护库 →
  `CREATE DATABASE lumen` 存在性守卫 → 建 `auth` schema 幂等）；数据源
  `currentSchema=auth` 连 `lumen` 库；Redis 连测试服务器；Flyway 启用。Verify: 最小
  `@Test`（context loads + 连接真库）先红后绿——红 = env 未配置时的占位符解析失败。
- [x] 1.3 证明"未配置=显式失败"语义：无 env 文件/变量时测试**显式失败**（占位符解析错误，
  捕获记录入报告），绝无静默跳过。Verify: 报告含 unconfigured 失败证据 + configured 绿证据。

## 2. Flyway 迁移与数据层

- [x] 2.1 `V1__auth_schema.sql`：七表 `unit_info`（upId/level/type）、`department`
  （FK unit）、`user_info`（username unique、password hash、must_change_password）、
  `role`、`user_role`、`role_menu`、`menu_info`（upId/type/perms/path/component/sort）。
  Verify: Testcontainers 测试中断言七表存在（information_schema）。
- [x] 2.2 `V2__seed.sql`：医院根 unit、三科室（radiology/us/endo FK unit）、默认管理员
  （BCrypt 口令 + must-change-password）、基础角色、最小菜单集（登录/me/用户管理 + 三科室
  页面占位 + 审核按钮示例）。Verify: 迁移后查询断言种子行存在。
- [x] 2.3 实体 + Mapper（`org.birdbro.lumen.auth.entity/mapper`）：七实体继承
  `EntityBase`（id 为各自 `@TableId Integer AUTO`）、Mapper 接口；`MenuTree` 装配
  service。Verify: mapper 冒烟测试（insert/select 往返）绿。

## 3. 登录闭环（首个真端点）

- [x] 3.1 `AuthController`：`POST /api/v1/auth/login`（BCrypt 验密 → `TokenService.createToken`
  → 信封携 JWT+profile）与 `GET /api/v1/auth/me`（token → profile + 菜单树 + perms 集合）。
  先写红测试（TestRestTemplate 打真测试服务器上下文）：正确凭据 → `"200"`+token；错凭据
  → `A0210`；无 token 打 `/auth/me` → `"203"` 信封拒绝（拷贝件 AuthenticationEntryPointImpl
  语义，用户裁定 2026-11-02）。Verify: 三测试全绿。
- [x] 3.2 匿名放行：`/auth/login` 免 token（以拷贝件实际机制为准——`SecurityConfig` 放行
  规则或注解），其余端点过滤器生效。Verify: 无 token 打 `/auth/me` 拒绝测试即此证明。
- [x] 3.3 审计身份证明：以 admin token 调一个写端点（如创建用户）→ 断言
  `create_by` = admin id（MetaObjectConfig 填充被真库证实）。Verify: 测试绿。

## 4. 管理 CRUD

- [ ] 4.1 机构树端点：unit CRUD + 树形查询（`UnitTree` 装配）。Verify: 建 two-root 场景
  测试（多根能力）+ 树形响应断言绿。
- [ ] 4.2 科室端点：department CRUD（FK unit）。Verify: 三科室种子列出断言绿。
- [ ] 4.3 用户端点：列表/创建/改密/停启用；重复用户名拒绝（`A` 类）。Verify: 创建+重复
  拒绝测试绿。
- [ ] 4.4 角色与菜单端点：role CRUD、role-menu 分配、menu 列表（树）。Verify: 分配后
  `/auth/me` 的 perms 集合包含所配按钮串（含 `radiology:report:audit` 示例）测试绿。

## 5. 文档与规格卫生

- [ ] 5.1 协调文档：关闭"create_by 升宽 bigint"flag（裁定维持 Integer，指向 data delta）；
  登记信封变异控制已重跑（首个消费者已存在）；登记 `Endpoint Documentation` 对齐经 api
  delta 归档完成。Verify: 文档 grep。
- [ ] 5.2 `lumen-auth/CLAUDE.md` 无需建（角色文件在）；确认 `verify.config.yaml` backend
  L3 语义注释更新（lumen-auth 测试需测试服务器连接，未配置=显式失败；framework/common
  零依赖不变）。Verify: 文件内容核对。
- [ ] 5.3 `openspec validate auth-rbac-foundation --strict`。Verify: exit 0 无警告。

## Workflow follow-up

- 全部完成后：`guard applying→verifying` → `/opsx:verify auth-rbac-foundation` 出凭证
  （L3 首次真跑 Testcontainers）→ 归档决策连同"前端登录 change"（cross-stack）一起定。
- 排队提醒：DataScope 行级权限 → radiology/exam change；菜单管理 UI → 前端。

# Tasks

> TDD：每个行为先红后绿（Testcontainers 测试即红绿载体）。验收命令一律
> `cd lumen-sev && ./mvnw ...`；Docker daemon 为 lumen-auth 测试前置（设计 D3）。

## 1. Testcontainers 基建

- [ ] 1.1 在 `lumen-bom` 登记 `org.testcontainers:junit-jupiter:1.19.8` 与
  `org.testcontainers:postgresql:1.19.8`（lumen-only 新增，注明非 OPS 验证）；`lumen-auth`
  以 test scope 引用 + `spring-boot-starter-test`。Verify: `./mvnw -pl lumen-auth -am compile -q`
  exit 0。
- [ ] 1.2 写 `AuthIntegrationTestBase`（抽象基类）：`@Testcontainers` 拉 postgres:16-alpine
  （动态映射端口）+ redis 通用容器；`@DynamicPropertySource` 注入 datasource/redis 地址；
  Flyway 启用。Verify: 一个最小 `@Test`（context loads + Flyway 应用了迁移）先红后绿
  —— 红 = 尚无迁移时 Flyway 空跑或上下文失败的表现，绿 = 基线成立。
- [ ] 1.3 证明 daemon 前置语义：daemon 停止时该测试**显式失败**（捕获错误类型记录入报告）。
  Verify: 报告含 daemon-off 失败证据 + daemon-on 绿证据。

## 2. Flyway 迁移与数据层

- [ ] 2.1 `V1__auth_schema.sql`：七表 `unit_info`（upId/level/type）、`department`
  （FK unit）、`user_info`（username unique、password hash、must_change_password）、
  `role`、`user_role`、`role_menu`、`menu_info`（upId/type/perms/path/component/sort）。
  Verify: Testcontainers 测试中断言七表存在（information_schema）。
- [ ] 2.2 `V2__seed.sql`：医院根 unit、三科室（radiology/us/endo FK unit）、默认管理员
  （BCrypt 口令 + must-change-password）、基础角色、最小菜单集（登录/me/用户管理 + 三科室
  页面占位 + 审核按钮示例）。Verify: 迁移后查询断言种子行存在。
- [ ] 2.3 实体 + Mapper（`org.birdbro.lumen.auth.entity/mapper`）：七实体继承
  `EntityBase`（id 为各自 `@TableId Integer AUTO`）、Mapper 接口；`MenuTree` 装配
  service。Verify: mapper 冒烟测试（insert/select 往返）绿。

## 3. 登录闭环（首个真端点）

- [ ] 3.1 `AuthController`：`POST /api/v1/auth/login`（BCrypt 验密 → `TokenService.createToken`
  → 信封携 JWT+profile）与 `GET /api/v1/auth/me`（token → profile + 菜单树 + perms 集合）。
  先写红测试（MockMvc/TestRestTemplate 打真容器上下文）：正确凭据 → `"200"`+token；错凭据
  → `A0210`；无 token 打 `/auth/me` → `A` 类拒绝。Verify: 三测试全绿。
- [ ] 3.2 匿名放行：`/auth/login` 免 token（以拷贝件实际机制为准——`SecurityConfig` 放行
  规则或注解），其余端点过滤器生效。Verify: 无 token 打 `/auth/me` 拒绝测试即此证明。
- [ ] 3.3 审计身份证明：以 admin token 调一个写端点（如创建用户）→ 断言
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
  L3 语义注释更新（需 Docker daemon）。Verify: 文件内容核对。
- [ ] 5.3 `openspec validate auth-rbac-foundation --strict`。Verify: exit 0 无警告。

## Workflow follow-up

- 全部完成后：`guard applying→verifying` → `/opsx:verify auth-rbac-foundation` 出凭证
  （L3 首次真跑 Testcontainers）→ 归档决策连同"前端登录 change"（cross-stack）一起定。
- 排队提醒：DataScope 行级权限 → radiology/exam change；菜单管理 UI → 前端。

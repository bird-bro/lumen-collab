# Tasks

> TDD：迁移/端点行为先红后绿；测试基座沿用 lumen-auth 团队测试服务器体系
> （.env-test.local，未配置 = 显式失败）。验收命令一律 `cd lumen-sev && ./mvnw ...`。

## 1. 迁移与种子

- [x] 1.1 `V3__system_data.sql`：`public.dict_catal` / `public.dict_detail`（EntityBase
  审计块，随访形状）+ `public.log_info` / `public.log_sign`（追加写，dateYm 键，无审计
  块）+ `common_status` 目录与明细种子（显式 id + setval）。Verify: 迁移测试断言四表存在
  于 public + 种子行可查。
- [x] 1.2 `docs/deployment/pg-init.sql` 增补：public 系统表对六服务角色的 GRANT 模板
  （注释风格与角色块一致）。Verify: grep。

## 2. framework 层（适配 (7) + dict 拷入）

- [x] 2.1 `framework/log` 两实体去 `ice.` 前缀（`log_info`/`log_sign`）；lumen-auth
  `application.yml` 连接串改 `currentSchema=auth,public`（dev 文件 + 模板同步）。
  Verify: 编译绿 + 既有 34 测试全绿。
- [x] 2.2 `framework/dict/` 加法式拷入：`dict_catal`/`dict_detail` 的 entity/mapper/
  service（OPS manage/dict 血统，Integer id + EntityBase）+ `DictLabelHelper`/
  `DictReverseHelper`。`DictMenu`/`DictCity` 不拷。Verify: 编译绿。

## 3. lumen-auth 端点

- [x] 3.1 字典目录端点：`/api/v1/auth/dicts/catal` CRUD（code 查重 A 类）+ 明细端点
  `/api/v1/auth/dicts/detail` CRUD + 按 caCode 列表。Verify: 先红后绿端点测试。
- [x] 3.2 日志查询端点：`/api/v1/auth/logs` 分页（type/uid/dateYm 过滤，actionTime 倒序）。
  Verify: 先红后绿端点测试。
- [x] 3.3 `@Log` 接线：管理写端点（orgs/org-deps/users/roles/dicts 的写方法）标注拷贝件
  `@Log`。Verify: 4.4 的实测。
- [x] 3.4 登录事件接线：`AuthServiceImpl` 登录成功/失败分支经 `AsyncFactory.recordForSign`
  写 `log_sign`（登出已由拷贝件 `LogoutSuccessHandlerImpl` 接线）。Verify: 4.5 的实测。

## 4. 测试闭环

- [x] 4.1 数据层往返：dict entity insert/select（public 落位断言 information_schema）。
- [x] 4.2 字典端到端：登录 → 建目录 → 建明细 → 按 code 列表 → 重复 code 拒绝。
- [x] 4.3 日志查询：分页 + 过滤断言。
- [x] 4.4 `@Log` 实测：带注解的写端点调用后 `public.log_info` 出现对应行（uid = 操作者）。
- [x] 4.5 sign 实测：登录成功与密码错误各产生一条 `public.log_sign`（type/uid/account 断言）；
  登出经拷贝件链路同样落行。

## 5. 文档

- [x] 5.1 `docs/proposals/system-data.zh.md` 中文导读；协调文档适配 (7) 登记；
  spec-hygiene 若未归档则由其归档流程顺带落地 data/service-topology 措辞（避免两 change
  冲突：system-data 的 delta 以归档顺序后者为准）。（附注：适配 (8)
  RequestContextConfig 由 Batch 2 增补登记；两 delta 的适配清单措辞已与 spec-hygiene
  收敛为同一最终文本，归档顺序无关。）

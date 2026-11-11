<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: audit-log-unify-api
  date: 2026-11-10
  overall: PASS   # 2026-11-10 refresh: def-partition dedicated test added (AuthSystemDataMigrationTests 14/14)
  stacks:
    backend:
      L1: PASS
      L2: "14/15"
      L3: PASS
---

# 验证结果: audit-log-unify-api

> 生成时间: 2026-11-10 · 总体: **PASS**（15/15 场景 IMPLEMENTED；2026-11-10 二次刷新——def 分区专属断言补齐后由 PASS_WITH_ISSUES 升级）

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` 全 reactor 成功（JDK 11） |
| L2 规格对齐 | 15/15 已实现 | def 分区场景已由专属测试闭环（见下） |
| L3 测试 | ✓ PASS | 全 reactor BUILD SUCCESS：lumen-system 151/151（含新增 def-partition 断言，AuthSystemDataMigrationTests 14/14）+ common 4/4 = 155/155，0 失败 0 错误（AuthAuditLogQueryTests 4/4、AuthSignLogTests 2/2、AuthUserAgentResilienceTests 3/3） |

## 场景对齐详情

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Annotated endpoint writes an operation log（type=1） | ✓ 已实现 | LogInfoServiceImpl.java:55（codeOf 唯一赋值路径）+ AuthAuditLogWriteTests（新增→1 Integer，MyBatis 参数日志） |
| Login records a sign event（10/11） | ✓ 已实现 | AuthSignLogTests.java:76/88（log_info，type=10/11，title='Sign-IN'，session IS NOT NULL） |
| Logout records a sign event（12） | ✓ 已实现 | AuthSignLogTests.java:117-148（signOutAppendsSignRowThroughTheCopiedHandler，type=12，title='Sign-OUT'） |
| Credential anchor never reaches admin responses | ✓ 已实现 | LogInfoServiceImpl.java:98（select 排除 session）+ AuthAuditLogQueryTests.java:138-139（wire 层断言无 session 键） |
| Log table exists on fresh database | ✓ 已实现 | V25 + 活库实测：log_info relkind='p' RANGE (date_ym)，children 2026/2027/def（psql pg_inherits 三行）；AuthSystemDataMigrationTests:269（type integer） |
| log_sign is gone | ✓ 已实现 | V25:55-56（DROP CASCADE + parked）+ AuthSystemDataMigrationTests:242-263（5 关系 + 序列 pg_class 双查全 0）+ 活库 `relname LIKE 'log_sign%'`=0 |
| Rows survive the conversion | ✓ 已实现 | V13 转换 + V25 re-type 活库实证：18 标签行全数以码值存活（1×3, 2×11, 3×1, 4×3——b2 reviewer 独立 JDBC 复核；本轮 type 分布含 10×2/11×1 登录残留） |
| Write into an unpartitioned year lands in the default partition | ✓ 已实现 | AuthSystemDataMigrationTests.writeIntoAnUnpartitionedYearLandsInTheDefaultPartition（date_ym=179901 → tableoid=log_info_def，finally 硬删 + 残留断言；活库探针先证，专属测试后补——2026-11-10 闭环） |
| Query operation logs | ✓ 已实现 | AuthLogQueryTests.java:51-133（分页/uid 过滤/Integer type=1/dateYm/空结果，actionTime DESC） |
| Account filter matches sign and operation rows | ✓ 已实现 | AuthAuditLogQueryTests.java:122-131（account 过滤双族命中 Total:2） |
| Integer type filter | ✓ 已实现 | AuthAuditLogQueryTests.java:174-186（type=11 精确命中种子失败行） |
| Sign-log endpoint is gone | ✓ 已实现 | AuthAuditLogQueryTests.java:222-226（/sign → 404） |
| Session column excluded | ✓ 已实现 | AuthAuditLogQueryTests.java:138-139 + MyBatis SELECT 列表面无 session（wire 层证明） |
| Sign rows land in log_info instead | ✓ 已实现 | LogSignInsertServiceImpl.insert（title=msg/type 直通/token→session）+ AuthSignLogTests 全绿；log_sign 关系 0 |
| The sign-log endpoint answers 404 | ✓ 已实现 | 同上 /sign 404 + system:signlog:list 权限随菜单 17 退役（V25 + AuthAuditLogQueryTests） |

## 发现的问题

（无——首验的 1 个 PARTIAL 已于 2026-11-10 闭环：def 分区写路径补上专属测试断言
`AuthSystemDataMigrationTests.writeIntoAnUnpartitionedYearLandsInTheDefaultPartition`，
14/14 绿，verify 整体升 PASS。）

## 过程备注

- 实现期 3 次子代理 API 超时 → 用户裁决接管，B3/B4 由主会话直接驱动（progress.md 全记录）
- b4 调试：全量 reactor 首跑 30 类上下文全炸 = target 残留陈旧 LogSignMapper.xml（rm 后 156/156）
- 台账 #4 修订：ILogSignService 删除清单改为「查询半删除 + 写入半改名 LogSignInsertService 保留」
- A 类参数错误按裁决特性化（copied handler 实际映射 A0421，测试断言 code.startsWith("A")）

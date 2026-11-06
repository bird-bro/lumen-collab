# Batch 3 Report - sign-log-query / V14 menu seed + probe cleanup (tasks 3.1 + 3.2)

日期：2026-11-05。范围：`V14__sign_log_menu.sql`（新）+ `AuthSignLogQueryTests.cleanupMenu`
（硬删改造）+ 测试库一次性探针清淤。断言、双向 perm pin、其他迁移零改动。

## 1. V14 文件（task 3.1）

`lumen-sev/lumen-system/src/main/resources/db/migration/V14__sign_log_menu.sql`：

- 菜单行 `(17, 16, 'C', '登录日志', 'system:signlog:list', 'signlog',
  'system/log/sign', NULL, 3)`，挂在安全 (16) 下、排序紧跟日志查询 (12)；
  `perms` 即 LogController `/sign` 的方法级门禁字符串，`component` 即 web 路由
  注册的 `system/log/sign`（b4 已落地）。
- role_menu 授权保留 V8 的 guarded `INSERT SELECT`（`NOT EXISTS` 只查 live 行），
  `setval(pg_get_serial_sequence(...), MAX(id))` 收尾 —— 与 V5/V8 纪律一致。
- id 17 为下一空闲显式 id（V2:1-6、V5:7-15、V8:16，逐一核对无冲突）。

### 冲突子句 = revive 语义（偏离 V8 的理由，b2-review Important #1）

头注释完整记载：共享测试库累积了 b2 夹具的软删探针菜单（`path LIKE
'b2signlogmenu_%'`），每次套件运行消耗序列 id，其中一枚恰好落在 id 17。若用
`ON CONFLICT (id) DO NOTHING`，V14 在该库会**静默 no-op**（真实登录日志菜单永远
建不出来，控制台死入口），而在新库上通过 —— 掩盖性分叉。故采用
`DO UPDATE SET up_id/title/perms/path/component/icon/sort/deleted=0/update_time`
全列收敛：新库首次插入；被探针占据的库原地复活为真实菜单行，两个起点收敛。
配合 task 3.2 的夹具硬删 + 一次性清淤，当前所有库都走插入路径，UPDATE 路径是
对仍未清淤库的安全网。

## 2. 测试库探针清淤（task 3.2，psql，.env-test.local 参数只读、值未打印）

执行前先SELECT 污染集 → 删 role_menu 引用行（FK `V1__auth_schema.sql:122`
`role_menu.menu_id REFERENCES menu_info (id)`）→ 硬删菜单行 → 复查。

**Before**（6 行软删探针，id 17 即 b2 评审发现的占位行）：

```text
 id |            path             | deleted
----+-----------------------------+---------
 17 | b2signlogmenu_1793865360676 |       1
 18 | b2signlogmenu_1793865543816 |       1
 19 | b2signlogmenu_1793865728218 |       1
 20 | b2signlogmenu_1793865730916 |       1
 21 | b2signlogmenu_1793866561412 |       1
 22 | b2signlogmenu_1793866563973 |       1
 max_menu_id = 22 ;  role_menu 引用行 = 6
```

**清淤语句命中**：`DELETE 6`（role_menu）→ `DELETE 6`（menu_info）。

**After**：

```text
 id | path | deleted
----+------+---------
(0 rows)
 max_menu_id = 16
```

未触碰其他任何行；`MAX(id)` 回落 16，V14 显式 id 17 随后首次插入，序列
resync 后探针从 18 起推进（见 §3）。

## 3. 套件运行 ×2（`export JAVA_HOME=jdk-11 …; cd lumen-sev && ./mvnw test -q`）

- **Run 1**：EXIT=0，surefire 汇总 **74 tests / 0 failures / 0 errors / 0 skipped**。
  启动时 Flyway 对测试库真实应用 V14。
- **Run 2**：EXIT=0，**74 / 0 / 0 / 0**。 revive 幂等收敛：V14 未重放
  （checksum 不变，见下），探针 id 推进越过 17，与真实行零碰撞。

## 4. psql 校验（Run 1 后 + Run 2 后终态）

Run 1 后：

```text
 id | up_id | type |  title   |        perms        |  path   |    component    | sort | deleted
----+-------+------+----------+---------------------+---------+-----------------+------+---------
 17 |    16 | C    | 登录日志 | system:signlog:list | signlog | system/log/sign |    3 |       0

 version |      description       |  checksum   | success
---------+------------------------+-------------+---------
 13      | log tables partitioned | -1499008026 | t
 14      | sign log menu          | -1849169454 | t

b2signlogmenu_% 残留 = 0 行 ;  max_menu_id=17, seq_last=19（Run1 两枚探针占 18/19，
新 cleanupMenu 已硬删）
```

Run 2 后终态：

```text
 menu 17 同上（update_time=2026-11-05 16:30:29 = V14 boot 时刻，未被 Run2 重放）
 b2signlogmenu_% 残留 = 0 行 ;  max_menu_id=17, seq_last=21（Run2 探针 20/21，已硬删）
 role_menu WHERE menu_id=17 → 共 1 行、live 1 行（role 1 的 V14 授权）
 flyway V14 checksum=-1849169454 success=t（两次运行间零漂移）
```

即：revive 语义两连跑收敛；夹具硬删使探针不再留下任何行；序列推进无害
（V14 显式 id 17 + 探针只从 18 起自增）。

## 5. Self-review

- V14 列元组逐项对照 task 3.1 文本与 V5/V8 惯例；id 17 空闲性对 V2/V5/V8 逐一核对。
- 冲突子句与 brief 给定语义逐字一致（含 `deleted = 0` 与 `update_time`）。
- 夹具仅改 `cleanupMenu` 一个方法（+其 javadoc），`git diff` 确认断言、双向
  perm pin、imports 均未动；硬删顺序 = FK 依赖序（role_menu → menu_info）。
- 未触碰 rcp-test；V1-V13 零改动；除清淤两条 DELETE 外零手工 DDL/DML；
  `.env-test.local` 值未打印、未入库（psql 经本机 `postgres:16-alpine` 镜像的
  docker 客户端执行 —— 宿主机无 psql 可执行文件）。
- tasks.md 3.1/3.2 已勾选（随 change 目录一并归档，未随本 commit 暂存）。

## 6. Concerns

1. **revive 路径在本库未真实执行**：清淤先行后，id 17 在 V14 应用时已不存在，
   走的是首次插入路径。DO UPDATE 分支的正确性由（a）两连跑收敛、（b）头注释
   语义审查、（c）未来任何仍带探针残渣的库首启时真实检验 —— 记录为已知事实，
   非缺陷。
2. **psql 经 docker 客户端执行**：宿主机无 psql（b1 时代环境不同），改用本地
   `postgres:16-alpine` 镜像 `docker run --rm` 直连测试库，等价只读/清淤语义，
   无镜像拉取（本地已有）。

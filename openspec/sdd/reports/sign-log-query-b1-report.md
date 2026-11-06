# sign-log-query Batch 1 Report — V13 partitioned log tables (tasks 1.1 + 1.2)

**Status**: DONE_WITH_CONCERNS（功能全部达成、套件全绿；一次带外操作与三处骨架偏差需评审知悉）
**Date**: 2026-11-05 · **Branch**: dev · **Module**: lumen-sev/lumen-system

---

## 1. What was implemented

New file `lumen-sev/lumen-system/src/main/resources/db/migration/V13__log_tables_partitioned.sql`
(唯一改动文件):

- `public.log_info` / `public.log_sign` 由 V3 平表转换为 `RANGE (date_ym)` 分区表：
  - 年度子分区 `*_2026`（`FOR VALUES FROM (202600) TO (202699)`）、`*_2027`、
    默认分区 `*_def`（用户拍板的对 rcp-test 的偏离，防未预建年份写入报错）；
  - 复合主键 `(id, date_ym)`（PG 要求分区键入唯一约束；id 经原序列保持全局唯一，
    MyBatis-Plus AUTO 写入路径不变）；
  - `idx_*_date_ym` / `idx_*_uid` 以临时名 `*_p` 在父表重建（分区索引，子索引自动跟随），
    换名后回改为原名——外部索引名不变；
  - 序列 D3 舞步：`OWNED BY NONE`（先于 DROP，防序列随旧表被删）→ 改名后
    `OWNED BY <new>.id` → `setval` 至 `MAX(id)`；
  - 行搬运：`INSERT INTO ... (<显式列清单>) SELECT (<同清单>) FROM ...`，两侧显式列名，
    绝不按位置。
- 头部注释涵盖 brief 要求全部要点：目的、rcp-test 参照布局、`*_def` 偏离、
  DEFAULT 分区排空警示（挂新年份子分区前必须先搬空）、事务/幂等说明。

未触碰 V3–V12；无实体/服务/控制器/测试改动。

## 2. TDD evidence

### 2.1 Baseline (pre-change characterization run)

- 命令：`export JAVA_HOME=<jdk-11> && cd lumen-sev && ./mvnw test -q`
- **EXIT=0，全绿**：lumen-system surefire 汇总 **71 tests, 0 failures, 0 errors, 0 skipped**
  （全部测试集中于此模块；其余模块无测试）。
- 三张日志套件：AuthLogQueryTests 1 ✓ · AuthSignLogTests 2 ✓ · AuthOperationLogTests 1 ✓。

### 2.2 Post-change run #1 — 抓到一个真问题（非静默通过）

- **EXIT=1**：`AuthSystemDataMigrationTests.logTablesCarryEntityColumnsWithoutAuditBlock:154`
  —— `public.log_info must lead with its serial id ==> expected: <id> but was: <date_ym>`。
- 根因：brief 骨架把 `id integer ... DEFAULT nextval(...)` 写在 V3 列清单**之后**
  （列尾），而仓库既有形状契约（AuthSystemDataMigrationTests，基线中已存在并绿）
  断言 `ordinal_position` 第 1 列必须是 `id`。骨架该行是示意性写法，应以仓库契约为准。
- V13 本次运行已真实落库（Flyway 记账 version=13 checksum=-1855147780 success=true），
  落库状态 = id 在列尾的分区表。

### 2.3 修复与带外操作（评审重点）

- 修正 V13：`id` 回到 V3 首位（serial 原位展开）。
- 因文件内容必须变更，而已应用行的 checksum 与新文件必然不一致（Flyway 7 默认
  validateOnMigrate 会令后续所有 boot 校验失败），且 V13 转换对"已转换状态"不可重跑
  （子分区名占用、行会在 DROP 中丢失），按用户既定裁决（MEMORY：*Flyway never rewrite
  an applied migration — delete history row + re-run，never repair-and-diverge*）执行：
  1. **带外唯一一次 DB 操作**：`DELETE FROM system.flyway_schema_history WHERE
     version='13' AND script='V13__log_tables_partitioned.sql' AND success=true;`
     （返回 DELETE 1；应用行参数已先行记录：checksum=-1855147780, success=true,
     installed_on=2026-11-05 14:46:35）。
  2. V13 文件内置**收敛前奏**（详见 §4），使同一文件对"平表/已被旧稿转换过"两种起点
     都能行数保全地重建——重跑动作全部经由 Flyway 完成，无任何手工 DDL。
- 被替换的 id-in-last 草稿存档于 `/tmp/V13-as-applied-id-last.sql`（临时记录，不入库）。

### 2.4 Post-change run #2 — 全绿

- **EXIT=0**：**71 tests, 0 failures, 0 errors, 0 skipped**。
- AuthSystemDataMigrationTests **7/7 ✓**（形状契约：id 居首、无审计块、四个索引名精确命中）、
  AuthLogQueryTests 1 ✓ · AuthSignLogTests 2 ✓ · AuthOperationLogTests 1 ✓。
- Flyway 真实重放 V13（新 checksum **-1499008026**，success=true），即提交文件本身——
  库与文件零漂移。

## 3. Partition layout check (psql, team test server)

```text
== parents ==
log_info relkind=p RANGE (date_ym)
log_sign relkind=p RANGE (date_ym)
== children (6 rows) ==
log_info <- log_info_2026 FOR VALUES FROM (202600) TO (202699)
log_info <- log_info_2027 FOR VALUES FROM (202700) TO (202799)
log_info <- log_info_def DEFAULT
log_sign <- log_sign_2026 FOR VALUES FROM (202600) TO (202699)
log_sign <- log_sign_2027 FOR VALUES FROM (202700) TO (202799)
log_sign <- log_sign_def DEFAULT
== PK ==
log_info pk=id,date_ym
log_sign pk=id,date_ym
== lookup indexes ==
idx_log_info_date_ym ON log_info / idx_log_info_uid ON log_info
idx_log_sign_date_ym ON log_sign / idx_log_sign_uid ON log_sign
== sequence ==
log_info_id_seq owned by log_info.id last_value=234
log_sign_id_seq owned by log_sign.id last_value=226
== scratch leftovers ==
0   （log_info_new / *_parked 等零残留）
== ledger ==
version=13 checksum=-1499008026 success=true
```

**Row preservation**（修复期间各表行数）：pre-change 128/124 → 修复前 S1（id-尾态）
181/175 → 修复后 234/226。`min_id=1` 始终未变（最早行全程在表内），max_id 连续推进
（181→234、175→226，增量全部来自回归套件自身的登录写日志），序列 last_value 与
MAX(id) 一致。行保全由单事务 INSERT SELECT + 收敛前奏"先停行、后拆表"双重保证。

## 4. 对 brief 骨架的三处偏差（均为有意，已在文件头部注明）

1. **子分区建于搬运 INSERT 之前**：骨架先 INSERT 后建分区；无任何分区的分区表会拒绝
   一切 INSERT（"no partition of relation found for row"），而实测线上两表非空
   （首查 128/124 行）。先建分区（含 DEFAULT）对空/非空库都正确。
2. **id 置于首位**（V3 serial 原位展开）：brief 骨架把 id 行放在列尾；仓库形状契约
   AuthSystemDataMigrationTests 钉死第 1 列必须是 id。以契约为准（run #1 实证）。
3. **不写显式 BEGIN/COMMIT，且新增收敛前奏**：仓库全部既有迁移不带事务语句——Flyway
   默认把整个脚本包进单事务，等价"每表 copy-swap 原子"且更强（log_sign 半程失败会连带
   回滚 log_info 半程，重试永远从迁移前状态出发；若照抄骨架的 COMMIT，半程失败会留下
   无法重试的半转换库）。前奏 DO 块使文件对"平表起点"与"已被本迁移旧稿转换过的起点"
   双收敛（V6/V7/V12 共享库收敛纪律的延续），且先停行再拆表、绝不带行 DROP；
   亦不预 DROP `*_parked`（陈旧 parked 表宁可响亮报错，不静默销毁唯一行副本）。

## 5. Self-review

- 列清单逐列对照 V3 抄录（log_info 25 列 + id；log_sign 17 列 + id；两表 `type` 类型
  不同 varchar(255)/integer，已分别核对）。
- 索引/约束/序列命名闭环：`*_new_pkey → *_pkey`、`idx_*_date_ym_p → idx_*_date_ym`，
  线上四索引名精确存在；序列所有权与 last_value 已验。
- 套件是唯一的回归判据：三张日志套件 + 形状契约套件全绿；写路径（LogAspect 登录写日志）
  在 run #2 期间真实写入分区表（行数增量即证据）。
- 安全红线遵守：未触碰 rcp-test；除 §2.3 的单条 DELETE 外零手工 DDL/DML；.env-test.local
  值未打印、未入库。
- 3-strike 纪律：V13 落库尝试共 2 次（首次成功但形状违契，第二次收敛成功），非同根因
  连败，未触发停止条件。

## 6. Concerns

1. **带外 DELETE（已执行）**：`system.flyway_schema_history` 删除一行属用户既定裁决的
   修复动作，但严格说超出 brief 的"仅 psql 只读校验"清单——已在上文完整披露，
   请评审确认该先例口径。
2. **收敛前奏常驻文件**：为使共享测试库与未来新库同文件收敛，V13 永久携带 ~15 行
   DO 块前奏。若评审认为一次性事故不应留在永久迁移中，备选是批准一次性手工
   逆向换表（本次未获授权故未执行）；当前实现选择"零手工 DDL"路线。
3. **`setval(COALESCE(MAX(id),1))` 在空表上会跳过 id=1**（骨架原样保留）；日志表
   可接受，与 V3 对 virgin 序列的处理不同，特此备注。
4. 2026 子分区上界为 `TO (202699)`（开区间），真实月份 202601–202612 全覆盖，仅
   非法值 202699 会落入 DEFAULT——与 rcp-test 参照布局一致，非缺陷。

## 7. Files

- 新增：`lumen-sev/lumen-system/src/main/resources/db/migration/V13__log_tables_partitioned.sql`
- 报告：`openspec/sdd/reports/sign-log-query-b1-report.md`（本文件）
- 提交：见 dev 分支 commit（仅含迁移文件；工作区其余改动属其他批次，未纳入）。

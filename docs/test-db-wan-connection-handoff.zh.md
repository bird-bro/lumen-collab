# 交接:共享测试库公网连接错误(2026-11-25)

> 写给接手的会话:本文件记录 2026-11-25 实施与验证过程中观察到的共享测试库
> 连接错误家族、已验证的定性方法、以及留给你的处理事项。结论先行:
> **错误是环境性的(公网 WAN 瞬断),当前不影响数据正确性;要做的是稳定性
> 评估与缓解,不是修代码 bug。**

## 1. 环境(接手前先知道)

- 后端套件跑在**远程共享测试 PG** 上(非本地库),连接串经
  `.env-test-local`(gitignored,凭据不入库)+ 各栈的
  `*TestServerBootstrap`(SettingTestServerBootstrap / UsTestServerBootstrap /
  KernelTestServerBootstrap / TestKernelServerBootstrap)装配;
  `pgsql-test-lumen` MCP 直连同一个库(隔离共库实例)。
- 多会话共用这个库 —— **并行会话同时跑套件会互相踩夹具**(见 §3 的家族 B,
  与 WAN 瞬断是两回事,别混)。

## 2. 2026-11-25 的实发记录(全部已定性、已自愈或隔离证明)

| 时间点 | 现象 | 定性 | 证据 |
|:--|:--|:--|:--|
| 早间全 reactor | AuthWorkstationSeedTests "re-executed seed must not add grants expected 2 was 3" | 家族 C(共享库残留)+ 并行会话夹具竞态 | 隔离重跑绿 |
| device-fields 全 reactor | UsSlotConfigApiTests `尝试连线已失败` + `EOFException` | **家族 A:公网瞬断** | 隔离重跑绿(exit 0) |
| lumen-system 全量 ×2 | AuthRoomApiTests / AuthExamSettingSeedTests 同上传输层错误 | 家族 A | 两次查询库均在线,重跑全量 322/0/0 绿 |
| lumen-system 全量 | AuthDeviceApiTests A0301 权限失败 ×2 | **家族 B:并行会话清扫钩子**在中途硬删 `user_role=4 org_device=4 org_dep=4` 夹具(日志交错可见 `[system-test-hook] hard-swept run residue`) | 隔离重跑绿;并行会话后台任务被用户叫停后窗口干净 |
| device-fields apply 中 ×2 | Flyway `checksum mismatch for version 53` | **家族 D:迁移已应用后文件再改**(种子回填两次嵌入) | 删 `system.flyway_schema_history` version='53' 行重放(迁移幂等构造) |

## 3. 错误家族判别表(接手后遇到失败先查这张表)

| 家族 | 签名 | 定性动作 | 不要做的事 |
|:--|:--|:--|:--|
| **A. 公网瞬断** | `PSQLException: 尝试连线已失败` / `EOFException`,失败集中在套件中段某两个类 | MCP 直查 `SELECT now()` 确认库在线 → 隔离重跑该类 → 全量重跑(或 `rerunFailingTestsCount=1`) | 不要当测试缺陷修断言 |
| **B. 并行套件夹具竞态** | A0301/断言失败 + 日志里**别人的** `[system-test-hook] hard-swept run residue` 行(user_role/org_device 等非你域的表被删) | 确认并行会话是否在跑 → 叫停或等窗口 → 隔离重跑定性 | 不要改自己的测试去"修"它 |
| **C. 共享库残留** | 种子/计数类断言差 1-3 行(如 grants expected 2 was 3) | 隔离重跑;确认清扫前缀家族(各基类只扫 ut_*/自家标记) | 不要扩大清扫范围波及他人家族 |
| **D. 校验和不匹配** | `Migration checksum mismatch for version N` 且该迁移**未提交但已应用** | 删 `flyway_schema_history` 该版本行重放(**仅限幂等构造的迁移**);已提交的迁移永远改新 V 号 | 不要 repair-and-diverge |

## 4. 留给你的处理事项(按优先级)

1. **稳定性评估**:统计一段时间内家族 A 的频次(套件日志 grep `尝试连线已失败`
   / `EOFException`)。若仍维持"每次 WAN run 1-2 次",现状可接受(重跑即愈);
   若恶化(整套件多次中断),考虑缓解:
   - 测试基座(JdbcTemplate 层)加连接重试/keep-alive 参数(`tcpKeepAlive=true`,
     `socketTimeout` 放宽)——动 framework 前先起提案;
   - 或评估本地 PG 副本/内网链路(环境工程,非代码)。
2. **独占窗口纪律落地**:验证(verify)与全量套件前确认无并行套件在跑;
   两个会话都要跑时错峰。记忆条目「共享测试库验证六坑」已有此裁决,本文件
   补充了实发案例。
3. **家族 D 预防**:迁移文件在"已应用但未提交"状态下**不要再改**;确需改时
   先删历史行(幂等迁移)或改新 V 号。今天两次都这么处理的,记录在案。

## 5. 交接时的环境快照

- 2026-11-25 收官提交:`fbaced4`(内核对齐)→ `f1b6e96`(设备字段+V53 种子)
  → 本文件。活跃 OpenSpec 提案 **0**(全部归档)。
- 种子数据现状:`system.exam_item` 等五表已由 V53 固化(46 项目/34 部位/23 组/
  15 方法/58 链,拉丁设备值已回填);共享库另有 11 行**额外**的手工标记部位
  行(id 不在种子闭包内)与 1 行 DR 残留(remark='1')——均非种子责任,
  清扫与否由你判断(共享库硬删裁决)。
- 测试基座连接参数未做任何改动(本任务无需);`.env-test-local` 内容未变。

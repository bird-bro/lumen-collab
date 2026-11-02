<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: init-backend-skeleton
  date: 2026-10-29
  overall: PASS_WITH_ISSUES
  stacks:
    backend:
      L1: PASS
      L2: "29/37"
      L3: PASS
---

# 验证结果: init-backend-skeleton

> 生成时间: 2026-10-29（第二轮，修复后复审计）· 总体: **PASS_WITH_ISSUES**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `cd lumen-sev && ./mvnw compile -q` exit 0（clean 后重跑） |
| L2 规格对齐 | 29/37 已实现 | 8 PARTIAL · 0 NOT_IMPLEMENTED（第一轮 13⚠/2✗ → 修复后 8⚠/0✗） |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit 0；AuthApplicationTests 1.749s；8848/5432/6379/9876 全程空闲、日志零接触 |

## 第一轮问题的处置（凭证刷新依据）

- 2 个 ✗ 已清除：api "Service routing"、dep-gov "Integration service is added later" 改写为当前可检查形态并验证 ✓
- MAJOR-1（命令可移植性）：全库归一 `./mvnw from lumen-sev/`，仓库根 dir-build 复测 exit 0
- MAJOR-2（Redis 语义）：审计员 matchIfMissing 误读已驳回；补记 default-on + `lumen.redis.enabled=false` opt-out，场景现 ✓
- data/PostgreSQL-on-path/SUCCESS("0000")/ApiEnvelope 声称：修正全部经复审计 spot-check 确认落地

## 残余 8 个 PARTIAL（按性质分两类，均非矛盾）

### A 类 · 前瞻过程规则，本 change 无守卫对象（6 个）
| 场景 | 状态 | 说明 |
|:---|:---|:---|
| 科室规则入共享库的拒绝 | ⚠ | 规则三处成文，无 enforcer/CI 强制物（CI 在 Intent Lock 之外） |
| 两服务端口冲突 | ⚠ | 仅一个服务存在，端口表无人校验 |
| 数据库口令来自 env/Nacos | ⚠ | 机制已证（`${NACOS_ADDR:…}`）；application-test.yml 的 dummy jdbc-URL/token.secret 被严格解读为"committed literals"——已标注 dummy、非凭证，裁定为标记而非违背 |
| 后续新依赖先入 BOM | ⚠ | 先例与机制在（bcprov/7 个 lumen-only 坐标），无构建期拦截 |
| 未登记版本偏离=漂移 | ⚠ | 现存偏离全部已登记且程序化比对 0 mismatch；OPS 树在仓外，检测靠人工 diff |
| 第三方错误 C 类码 | ⚠ | C0001 已定义；本 change 无外部调用方，前置条件不可达 |

### B 类 · 延迟物 Observable 边界（2 个）
| 场景 | 状态 | 说明 |
|:---|:---|:---|
| 错误信封全形 | ⚠ | 无控制器则无可序列化响应可观察；`R.ofFail(code,msg,data)` 三参重载丢弃 data 为 OPS 源码原样 bug（verbatim 纪律保留，行为上 data 仍 null 合规格）——已记录，不得未登记即"修" |
| 标准实体字段 | ⚠ | EntityBase 审计契约半边与代码逐行吻合；`id` bigint/雪花子句 WHEN("定义新实体") 未发生——唯一在树实体 LogInfoEntity 是照搬件（Integer/AUTO，OPS 原样），非本 change 定义 |

## 发现的问题
无 Critical / Important。残余 ⚠ 均为"骨架阶段合理边界"：守卫物属于未来的 CI change、第二服务、首个真实实体。

## 下一步
- PASS_WITH_ISSUES → 确认后归档；但跨栈门禁要求 `init-web-skeleton` 也 verify PASS（其现状: proposing）
- 建议：转前端 apply → verify → 双 PASS 后各自 `/opsx:archive`
- 可选加餐（不阻断）：聚合器加 maven-enforcer（bannedDependencies: boot3-line starters；requireNoVersionsOutsideBom）可低成本消灭 A 类 3 个 ⚠——属新增范围，需另行批准

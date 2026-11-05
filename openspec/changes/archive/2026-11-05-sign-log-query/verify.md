<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: sign-log-query
  date: 2026-11-05
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "5/5"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "3/3"
      L3: PASS
---

# 验证结果: sign-log-query

> 生成时间: 2026-11-05 · 总体: **PASS**（8/8 场景已实现）

## backend (lumen-sev)

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q`（JDK 11）exit 0；`./mvnw test -q` 编译同批通过 |
| L2 规格对齐 | 5/5 已实现 | 全部 ✓ IMPLEMENTED，含测试服实测（分区布局/行数/菜单行） |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit 0 — 74/74（71 基线 + 3 新增），本轮独立复跑 |

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `pnpm build` exit 0 |
| L2 规格对齐 | 3/3 已实现 | 全部 ✓ IMPLEMENTED（单测 seam 桩 + 真实客户端生产路径） |
| L3 测试 | ✓ PASS | `pnpm test` exit 0 — 167/167（159 基线 + 8 新增） |

## 场景对齐详情

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 1 日志表分区终态（fresh DB） | ✓ 已实现 | V13__log_tables_partitioned.sql:123-127（`PARTITION BY RANGE (date_ym)` + 2026/2027/def 子分区）；测试服实测 relkind='p'、6 子分区、bound 逐条核对（b1 评审） |
| 2 行数据迁移存活 | ✓ 已实现 | V13:130-137 copy-swap 单事务显式列清单；测试服行数 287/277 连续单调（含真实登录写入），min_id=1 保留（b1 评审） |
| 3 未预建年份落 default 分区 | ✓ 已实现 | V13:127 + `log_sign` 镜像 DEFAULT 分区存在；PG 路由结构性证明（上界排他 → 2028xx 入 `*_def`）（b1 评审） |
| 4 登录日志分页查询（无 token） | ✓ 已实现 | LogController.java:86-94（`/sign` + MAX_PAGE_SIZE 复用）；LogSignServiceImpl.java:61-70（QueryWrapper + token select 排除）；AuthSignLogQueryTests.java:344-346 逐行键缺失断言（真实 token 值造数） |
| 5 服务端权限双向独立 | ✓ 已实现 | LogController.java:86 方法级覆盖类级（:38）；AuthSignLogQueryTests.java:224、257（log-only→A0301 / sign-only→200+A0301 双向 pin）；菜单 17 权限串实测在库（b3 评审） |
| 6 前端过滤查询 | ✓ 已实现 | pages/system/logs/sign.tsx:40-83（usePerm、过滤态机、page-1 显式重载）；menu-paths.ts:32 + router.tsx:85 |
| 7 只读页面 | ✓ 已实现 | sign-logs-page.test.tsx 断言无 mutation 控件；types.ts LogSignItem 零 token 字段（grep=0） |
| 8 菜单可见性随权限 | ✓ 已实现 | V14__sign_log_menu.sql:27-28（菜单 17 + `system:signlog:list` + role-1 授权 + revive 冲突子句）；渲染走既有 /me perms 机制；测试服菜单 17 实测在库（b3 评审，revive 分支回滚事务实测） |

## 发现的问题

（无 PARTIAL / 无 NOT_IMPLEMENTED / 无构建或测试失败）

备注（非场景缺口）：合同评审门要求**用户对真后端浏览器验收**登录日志页（登录 → 安全 →
登录日志，核对列与真实 `log_sign` 数据）后再 `/opsx:archive` —— 这是用户验收步骤，
已在本文件备注，不阻塞 PASS 判定。完整证据链见同目录 `verify-evidence.md`（任务 5.1
的 8 场景映射 + 各批次评审独立复跑记录）。

## 下一步

- PASS → 用户浏览器验收后 `/opsx:archive sign-log-query`

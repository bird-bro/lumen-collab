---
verify:
  change: org-device-drop-box
  date: 2026-11-19
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "2/2"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: org-device-drop-box

Overall: **PASS**（with a user-ruled scope note — see Verification Scope）

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Evidence |
|:--|:--|:--|:--|:--|
| backend | PASS（`./mvnw -pl lumen-system -am test` 多轮编译+运行；grep 门：`src/main` Java 零 box 命中） | 2/2 | PASS（隔离定向跑） | /tmp/b1-device-tests.log 等见下 |
| lumen-web | PASS（`pnpm tsc --noEmit` exit 0） | 2/2 | PASS（device+room **33/33** 绿） | /tmp/b3-web-tests.log |

## L2 Spec Alignment（delta specs/api/spec.md → MODIFIED「Device Create Validation」）

- **Scenario: Create device**（acquisition 块收窄为 capture/prune 七字段）→
  `AuthDeviceApiTests.createDeviceReturnsIdAndPersistsTheFullSurface`（box-free body +
  无 box 断言，连续两次绿：夹具先行收缩轮 + 全 reactor 轮）
- **Scenario: Device create rejects foreign or duplicate identifiers**（回归锚，不变）→
  `AuthDeviceApiTests.createDeviceRejectsForeignOrDeletedDepAndDuplicateCode`
  （隔离 b1 轮绿；后两轮全量跑中因共享库瞬态闪失败，见下）
- 层叠归档规则：MODIFIED 块携带主规格完整超集（两场景全文），已由 reviewer 复核

## L3 Evidence

- **V48 迁移**：共享测试库账本 `48 org device drop box success=true`；
  `AuthDeviceRoomSeedTests` 全绿 = 收缩后 `DEVICE_COLUMNS` 逐列断言通过 +
  新增缺席守卫（`FORBIDDEN_DEVICE_COLUMNS`，评审 minor 落地）通过
- **夹具收缩先行**（task 1.1）：box-free 夹具在字段移除前编译+通过 = 零依赖证明
- **web 镜像**：`types.ts` 双类型收缩 + 两夹具收缩，tsc 干净，device/room 33/33
- **评审**：reviewer 全文复核 PASS，无 blocker/major；minor（缺席断言）已补

## Attribution Notes（全量跑 7 失败 = 既有测试基建瞬态，非本变更）

- 全 reactor 轮的 7 失败（AuthDeviceApiTests 1 + AuthPermEnforcementTests 6）全部
  命中同一机制：WAN 共享库上 run-admin 克隆 ensure 瞬态落库失败 → 该类登录全
  A0201（"用户账户不存在"，app 查询 `Total: 0`）。决定性实验：手动预置克隆行后
  单测即绿（/tmp/perm-one.log，清扫计数 user_info=2 证明两行都存在过）；复跑单测
  exit=0。基类 javadoc 自证该瞬态（"transient … 1-2 per WAN run"）。非本变更引入
  （本变更不触 auth/perm 面）。
- 并行会话 rep-temp-normal-range 收尾残留造成的夹具竞态（前两轮 org-not-live/
  A0301）随其会话结束消失。

## Verification Scope（user ruling 2026-11-19）

用户裁决**跳过全 reactor 重跑**（"跳过"）：上述全量轮已执行两遍，失败均为既有
基建瞬态（已定性 + 实验证明），本变更直接验证面（设备双套件 + 种子套件 + web
33/33 + tsc）全绿。L1 全 reactor 编译在两轮 `./mvnw test` 中均已通过（失败发生在
测试阶段）。本变更的实现文件此后未再改动（除评审 minor 的种子测试 3 行补充，已
由缺席断言所在的种子套件隔离验证覆盖）。

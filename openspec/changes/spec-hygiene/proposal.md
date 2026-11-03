# Proposal: spec-hygiene

## Why

auth-rbac-foundation 归档时（2026-11-03）用户作出两项平台级裁定，主规格中有四处需求措辞
已被超越但不在该 change 的 delta 范围内，形成规格与现实的脱节：

1. **framework/common 可演进**（裁定：OPS 血统基础上可调整优化，加法式）——`service-topology`
   的 "Verbatim Adoption of the OPS Base Modules" 与 "Recorded Adaptations to the Base
   Modules"（"Exactly the five adaptations"）仍锁定逐字节镜像 + 五项适配清单；适配已有
   第 6 项（无网关审计桥接 + 匿名路径配置化）。
2. **配置本地化，Nacos 移出技术栈**（裁定：随访 fu-launch 同形）——`environment-contract`
   的 "Externally Provisioned Data Services" 与 "Connection via Environment, Never
   Committed" 仍要求提供/对接 Nacos。

规格是共享事实源：不留这四处旧措辞，后续 change 的 delta 会与主规格自相矛盾。

## What Changes

- `service-topology` MODIFIED："Verbatim Adoption of the OPS Base Modules" → OPS 血统
  共享平台层、可演进、加法式（新增场景：加法式演进落在共享层）；"Recorded Adaptations to
  the Base Modules" → 适配清单按裁定增长（现六项），新增适配 (6) 场景
- `environment-contract` MODIFIED："Externally Provisioned Data Services" → 仅 PostgreSQL
  + Redis 外部供给，栈内无注册中心/配置中心；"Connection via Environment, Never
  Committed" → env 变量或 gitignored profile 文件（随访模式）
- 纯规格措辞变更：**零代码**，无行为变化

## Capabilities

### Modified Capabilities

- `service-topology`: 平台层从"逐字节镜像 + 五项适配"改为"OPS 血统可演进 + 适配清单按裁定
  增长（加法式优先）"。
- `environment-contract`: 外部数据服务收敛为 PostgreSQL + Redis；连接配置走 env 变量或
  gitignored profile 文件。

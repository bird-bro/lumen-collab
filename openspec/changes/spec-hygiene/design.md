# Design

## Context

auth-rbac-foundation 归档日（2026-11-03）的两项平台级裁定使主规格四处需求措辞过时；
本 change 只做规格对齐，零代码。

## Goals / Non-Goals

- Goals: 主规格与两项裁定一致（framework/common 可演进、配置本地化无 Nacos）
- Non-Goals: 不改任何代码；不新增能力；不动 auth-rbac 能力规格

## Decisions

### D1 - 纯规格措辞变更，走标准 delta 流程
四处 MODIFIED 全部按"拷贝主规格整块 + 修改"的 delta 纪律书写；场景保留原有并按需增补
（加法式演进场景、适配 (6) 场景）。归档时由 archive 同步落主规格。

## Risks / Trade-offs

- 无行为风险（零代码）；唯一风险是漏掉第三处同类措辞——归档前全规格 grep Nacos/verbatim 复查。

## Open Questions

无——两项裁定均已记录在协调文档与 naming.md。

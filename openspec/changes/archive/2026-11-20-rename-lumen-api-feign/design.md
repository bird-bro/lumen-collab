# Design: rename-lumen-api-feign

## Context

`lumen-api` 是服务间 Feign 契约 jar（`NumberApi` owner = lumen-system `/issue` 端点；`PatientApi` owner = lumen-patient internal 端点族；消费方 lumen-patient / lumen-us）。模块名 "api" 与项目既有语义（REST 面 `/api/v1/**`、`openspec/specs/api/spec.md`）冲突。决策链：2026-11-20 会话内用户先问"这模块是干嘛的"，确认歧义成立，从候选（contract / client / 拆分 / 不改）中自定 `lumen-feign`。

## Decisions

### D1 — 定名 `lumen-feign`（用户裁决 2026-11-20）

- 候选否决记录：`lumen-contract`（备选推荐，无歧义但偏抽象）；`lumen-client`（与 web 前端"客户端"语义碰撞）；拆分 `lumen-patient-api` / `lumen-integration-api`（目前仅 patient + number 两个契约、number 归属尴尬、模块数翻倍；CLAUDE.md 里该拆分写法从未落地，本次顺带修正为平铺现实）。
- "钉技术进名字"顾虑在本项目不成立：同步查询 = OpenFeign + Sentinel fallback 是架构铁律（OPS train 1:1 钉死）；换 RPC 技术 = 整 jar 重写，名字随之重命名不迟。

### D2 — 包根随模块名，类名不动

- 包根 `org.birdbro.lumen.api` → `org.birdbro.lumen.feign`（子包 `feign.patient` / `feign.number`）。
- `PatientApi` / `NumberApi` / `*ApiFallback` / DTO 类名全部保留：Feign 接口 `*Api` 后缀是惯例；owner controller 与消费方 adapter 按类名注入，纯 import 扫换。
- naming.md 的 merge-setting-into-system 先例（保 FQN 只动宿主）不适用：该先例解决"域并入服务"，本变更是模块+包根整体改名，诉求就是消灭旧名。

### D3 — 配置键同族扫换

- `lumen.api.patient.url` → `lumen.feign.patient.url`；`lumen.api.system.url` → `lumen.feign.system.url`（直连 URL 属性键，no-discovery 裁决 2026-11-03 的接线）。
- `@FeignClient(name = ...)` 值（`lumen-system-number` / `lumen-patient-registry`）不动——直连模式下 name 只是 Sentinel/日志标识，不含歧义词。
- 用户动作：本地 gitignored `application-dev.yml` 手改 2 行键名（实施报告列出行号）。漏改后果：`${...:}` 空串兜底、调用期才报错，故报告单独提示。

### D4 — 记录分界：活文档刷名，历史记录不动

- 刷：`lumen-sev/CLAUDE.md` + `AGENTS.md`、`.claude/rules/naming.md`、`openspec/README.md`、活跃 change 文档（us-station-registration-sev）、活跃协调文档（us-station-registration）、治理协调文档（init-project-skeleton——虽源自已归档 change，但 adaptation 清单 + 端口表仍被 naming.md/规格引用，刷名并注明 rename）、交接文档模块树。
- 不刷（历史）：`openspec/sdd/reports/*`、`sdd/progress.md` Review 行、已归档 change 文档、归档协调文档（number-rule-unification / exam-setting）、`docs/analysis/*`、`docs/proposals/*`——均为已完成工作的时点记录。
- us-station-registration-sev 的文档是活文档（其归档被 web 凭据挡住、遥遥无期），刷名让未来批量归档时 delta 同步进主规格的就是正确名字。

### D5 — spec delta 最小面

- 两个 MODIFIED：service-topology「Foundation Module Set」（模块清单名换）、number-rule「Internal Service Issuance Contract」（契约宿主名换）。场景原样保留（超集=全等），仅需求文本内模块名变化。
- Shared Library Boundary requirement 现文本未列 `lumen-api`，不顺手补 `lumen-feign`（超出改名范围；未来要给契约 jar 定库边界另立 change）。

## Migration / Rollout

单提交原子落地（模块 mv + 包扫换 + 键扫换 + 文档同步），无过渡双名期。编译期断裂全部显性，全量编译 + 全量测试兜底。

## Risks / Trade-offs

见 proposal Risks 节。

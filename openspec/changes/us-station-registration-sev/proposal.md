# Proposal: us-station-registration-sev

## Why

内核登记域已归档但只是 jar + 测试（无宿主、无 HTTP 面、无 schema）——US 工作站（路线 §5.3 第一步科室工作站）的登记页需要真实的 `/api/v1/us/**` 登记后端才能开工（后端先行裁决 2026-11-05）。本变更给内核第一个真宿主，并接通 patient 域留下的两个接缝：PatientRegistryPort 适配与 patient.changed 快照刷新通道。

## What Changes

- 新模块 `lumen-us`（`org.birdbro.lumen.us`，`UsApplication`，端口 8083，独立 `us` schema + Flyway ledger，nginx `/api/v1/us/**`），入 aggregator；端口表加行。
- us ledger **V1 vendor-copy** 内核登记 DDL（D2 契约，逐语句照抄）；V2 加 `us_registration_ext`（孕检扩展：妊娠状态/孕周/备注，经 `RegistrationHook` 接缝读写）；V3 跨 schema 种子 US W 节点子菜单（登记 C 页 + F 按钮，`us:order:*` 权限族，patient V2 先例）。
- 登记.HTTP 面：`RegistrationController`（创建预约/查询分页/登记转状态/取消），薄壳直调内核 `ExamOrderRegistrationService` / `ExamOrderStateMachineService`；accession 经 `NumberIssuerPort` 适配器发号（rule `accession`）。
- `PatientRegistryPort` 适配器 = Feign → patient **内部端点**（内部钥匙保护，system `/issue` 先例）：`resolve-or-register` 复合端点（解析链服务端原子编排，A0715 冲突语义不变）。
- `lumen-api` 新增 `PatientApi` 契约（add-only，NumberApi 先例）；patient 侧新增内部端点族：resolve-or-register + outbox-pull/ack（内部钥匙守卫，不走用户权限）。
- 快照刷新接线：us 定时轮询 patient outbox（PENDING 批量拉取→按事件分派→ack 标记消费，失败保持 PENDING 幂等重试）：`PATIENT_CHANGED` 刷新非取消单快照（内核 `refreshPatientSnapshot`），`PATIENT_MERGED` repoint `exam_order.patient_uid` 后刷新（决策 #3 最小合并卫生）。**不部署 MQ**（用户裁决 2026-11-20：内部轮询端点）。

## Capabilities

### New Capabilities

- `us-station`: US 工作站宿主——模块身份/登记 HTTP 面/患者注册适配器/快照刷新接线/孕检扩展/菜单权限种子。

### Modified Capabilities

- `patient-domain`: 新增内部服务面（internal-key 守卫的 resolve-or-register 与 outbox-pull/ack 端点族，不走用户权限）。
- 不改 `exam-workflow-kernel`：内核库零改动（纯消费）。

## Affected Stacks

- single-stack: lumen-sev（lumen-us 新模块 + lumen-api 契约 + lumen-patient 内部端点）

## Depends-On

- `2026-11-19-lumen-core-registration-kernel`（已归档——内核库与 D2/D5/D6/D8 契约）
- coordinates with `us-station-registration-web`（UI 消费真实 API）

## Scope

- In scope: us 模块脚手架、vendored DDL、登记 HTTP 面、PatientApi 契约 + patient 内部端点、两个端口适配器、快照轮询、孕检扩展、菜单种子。
- Out of scope: 报告域/执行域/叫号（内核后续 change）；HIS 两阶段 HTTP 面与工作列表 UI（随 `lumen-integration` HL7v2，今日无生产者）；MQ 部署；放射/内镜工作站。

## Success Criteria

- us 服务以 8083 起来，登记单全生命周期（创建→登记→取消）经 `/api/v1/us/**` 真实可用，accession 由平台号规则发号。
- US 工作站 W 节点下出现登记菜单（admin 授权后可见），`us:order:*` 权限族生效。
- 患者解析链经内部端点闭环（含占位档案）；patient.changed / patient.merged 经轮询落成快照刷新与 repoint，outbox 行被消费。

## Constraints

- 内核库零修改——宿主只做适配与接线（D1/D6/D8 契约）。
- vendored DDL 逐语句照抄、不改列（内核头注释契约）；科室字段走扩展表。
- 内部端点一律内部钥匙守卫（复用 `InternalKeyInterceptor` 形态），永不挂用户权限。

## Risks

- 轮询通道的消息延迟/重复：ack 前失败保持 PENDING + 刷新幂等（快照覆盖写）兜底；MQ 迁移留待未来提案（outbox 表结构不变）。
- patient 内部复合端点与既有 `/resolve` 语义漂移：复合端点只编排既有服务方法，不重写解析逻辑。

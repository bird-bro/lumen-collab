# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: us-station-registration-sev
- **Problem to solve**:
内核登记域已归档但只是 jar + 测试（无宿主、无 HTTP 面、无 schema）——US 工作站（路线 §5.3 第一步科室工作站）的登记页需要真实的 `/api/v1/us/**` 登记后端才能开工（后端先行裁决 2026-11-05）。本变更给内核第一个真宿主，并接通 patient 域留下的两个接缝：PatientRegistryPort 适配与 patient.changed 快照刷新通道。

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:
- In scope: us 模块脚手架、vendored DDL、登记 HTTP 面、PatientApi 契约 + patient 内部端点、两个端口适配器、快照轮询、孕检扩展、菜单种子。
- Out of scope: 报告域/执行域/叫号（内核后续 change）；HIS 两阶段 HTTP 面与工作列表 UI（随 `lumen-integration` HL7v2，今日无生产者）；MQ 部署；放射/内镜工作站。
- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/patient-domain/spec.md`
3:### Requirement: Internal Service Surface
13:#### Scenario: Resolve-or-register resolves an exact match
18:#### Scenario: Resolve-or-register registers a new archive
24:#### Scenario: Outbox pull and ack hand out events exactly once
30:#### Scenario: Internal-key guard refuses foreign callers
- `specs/us-station/spec.md`
3:### Requirement: US Service Host
11:#### Scenario: Endpoints are reachable under the unified entry
17:#### Scenario: Kernel DDL is vendored verbatim
23:### Requirement: Registration Surface
31:#### Scenario: Create a booked order
38:#### Scenario: Illegal transition is refused verbatim
44:#### Scenario: Cancel carries a reason
50:### Requirement: Patient Registry Adapter
57:#### Scenario: Registration consumes the port result
62:#### Scenario: Incomplete port result refused
67:#### Scenario: US-side resolve passthrough for the registration UI
75:### Requirement: Snapshot Refresh Wiring
83:#### Scenario: Patient change refreshes open orders
89:#### Scenario: Merge repoints live orders
95:### Requirement: US Registration Extension
102:#### Scenario: Extension persists with the order
107:### Requirement: Menu and Perms Seed
114:#### Scenario: Registration page joins the US workstation tree


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
- 内核库零修改——宿主只做适配与接线（D1/D6/D8 契约）。
- vendored DDL 逐语句照抄、不改列（内核头注释契约）；科室字段走扩展表。
- 内部端点一律内部钥匙守卫（复用 `InternalKeyInterceptor` 形态），永不挂用户权限。

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 `lumen-us` module: pom (aggregator + BOM import), `UsApplication` (scan `org.birdbro`,
9:- [ ] 1.2 Ledger V1: vendor-copy the kernel registration DDL verbatim. Test: schema migration test
12:- [ ] 1.3 Ledger V2 `us_registration_ext` + ledger V3 cross-schema menu seed (登记 C page + F
18:- [ ] 2.1 `lumen-api/patient`: `PatientApi` Feign contract + DTOs (resolve-or-register identity,
20:- [ ] 2.2 patient-side: `InternalKeyInterceptor` config + `InternalPatientController`
27:- [ ] 3.1 us adapters: `PatientRegistryPortAdapter`, `NumberIssuerPortAdapter`, internal-key
29:- [ ] 3.2 `RegistrationController` (resolve passthrough / create / page query / register / cancel)
35:- [ ] 4.1 Snapshot poller: scheduled pull → PATIENT_CHANGED refresh / PATIENT_MERGED repoint+refresh
41:- [ ] 5.1 Full reactor `./mvnw test -q` green; `/opsx:verify` credential.

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: 
- **Required edge cases**: 
- **Regression-sensitive areas**: 

## Execution Mode

- **Mode**: `Inline` | `Batch Inline` | `SDD`
- **Selection rationale**: <!-- AI: choose based on task count and complexity -->

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 
- **Blocking categories**: 

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

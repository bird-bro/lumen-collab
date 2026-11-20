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

- **Change name**: us-station-registration-web
- **Problem to solve**:
US 工作站是路线 §5.3 的第一个科室工作站，登记页是它的第一张真页面：患者解析/建档 + 登记单全流程，全部对着真实 `/api/v1/us/**` 后端（无 mock 层裁决 2026-11-05）。患者域后端与内核登记域已就绪，本变更补上工作站侧的用户面。

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:
- In scope: `/us/registration` 页面族 + `PatientDemographicsForm` 共享组件 + us 订单 API client。
- Out of scope: 报告/采集/图文/打印 UI（内核报告域与后续 US change）；HIS 入站工作列表 UI；放射/内镜登记页。
- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-us-station/spec.md`
3:### Requirement: Registration Page Shell
11:#### Scenario: Page renders under the activated US workstation
17:#### Scenario: Actions hide without perms
22:### Requirement: Patient Resolution Flow
31:#### Scenario: Exact id_no match fills demographics
37:#### Scenario: Soft-match candidates offer selection
42:#### Scenario: No match opens a new archive with placeholder path
48:### Requirement: Order Form and Actions
56:#### Scenario: Create posts identity, items, and extension
63:#### Scenario: 登记 transitions the order
68:#### Scenario: Cancel requires a reason and confirmation
73:#### Scenario: Illegal transition surfaces verbatim
78:### Requirement: Order List
85:#### Scenario: List renders with state and identity


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
- Real backend only — 每个请求走 vite 代理到真实 us 服务，绝不造数据（2026-11-05 裁决）。
- 复用系统控制台成熟件（表格/抽屉/确认），不新造轮子；US 扩展段仅在本页出现。

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 Route + page shell (`us/registration`), workstation static mapping, perm gates; API
7:- [ ] 1.2 `PatientDemographicsForm` shared component (fields from the archive shape + PROVISIONAL
12:- [ ] 2.1 Patient resolution flow: lookup bar → exact fill / candidate modal / new-archive path.
14:- [ ] 2.2 Order form (US items filter + pregnancy ext block +急诊) and create submit; success
16:- [ ] 2.3 Order list + 登记 / 取消 (reason confirm) actions; verbatim backend refusals; state tags
21:- [ ] 3.1 `pnpm build` + full `pnpm test` green; `/opsx:verify` credential.

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

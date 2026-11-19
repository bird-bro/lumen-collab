# Proposal — lumen-core-registration-kernel

## Why

The department workstations share one exam workflow, but no shared kernel exists yet:
building lumen-us directly would fork EIS's 78-field `order_info` god table per
department — the exact pattern the EIS deep-dive disproved (三科登记页三份复制、
`order_patient` 三处写、状态机调用点散落五个服务类). patient-domain (archived
2026-11-18) delivered the patient half (`pat_uid`/`pat_id`/发号); the exam-side half is
the next step on the dependency route patient → lumen-core → lumen-us
(`lumen-reference/NOTES-us-workstation.md` §5.3), and it is the foundation both the US
workstation and radiology's booking flow build on. User rulings 2026-11-18: 登记侧先行
(booking-side only), 报告域审核链 = 内核统一可配置审核级 (recorded for the report-domain
proposal, mechanics not in this change), 纯内核库 (no first-department consumer, no HTTP).

## What Changes

- New shared-library module **`lumen-core`** (`org.birdbro.lumen.core`): non-executable
  jar, no `@SpringBootApplication`, no controllers — joins `framework`/`common` on the
  Shared Library Boundary as the first lumen-owned shared library (service-topology delta).
- **Exam-order aggregate, anti-god-table** — EIS's `order_info` 78 fields split into four
  members: `exam_order` (登记单: 编号段/状态段/申请段/取消段), `exam_execution` (检查执行),
  `exam_report` (报告实体占位: status + 来源接缝 only), patient reference (`patient_uid`
  NOT NULL per patient decision #3 + jsonb 患者快照 per decision #4).
- **Number-issuer port seam** (rewound 2026-11-18, user ruling: number rules unify into
  the platform's system schema via `number-rule-unification`) — the kernel defines a
  `NumberIssuerPort` (issue one number for a rule family) that consuming services adapt
  to the platform's number-rule service; the row-lock rule machinery is platform-owned,
  NOT kernel-owned. Kernel registration takes accession numbers only from the port and
  fails fast on issuer failure (no fabricated-number fallback). 影像号 (`pat_id`) stays
  with the patient service per patient decision #1 — the kernel also defines a
  `PatientRegistryPort` seam (resolve-or-register → identity), adapters belong to
  consuming services.
- **Document state machine** — 已预约 →(签到) 已登记 → 处理中 → 已完成, with 取消预约/已取消
  branches (EIS `OrderStatusEnum` semantics, fresh lumen enum + codes); illegal
  transitions reject with A-class codes. Post-registration side-effects (PACS worklist
  push / 叫号 / 云胶片 / 分单 / HIS 出站 — EIS 五步联动) are NOT in the kernel: the
  transition service fires ordered extension hooks consumers register into.
- **HIS two-phase intake** — apply cache (原始报文 source + 字典映射 target, unmatched
  exam items dropped) → manual confirm commit to a formal order (无服务端自动转单, EIS
  1.4 semantics); the HL7v2 protocol layer itself belongs to lumen-integration (future
  change) — this change owns the cache model + confirm flow only.
- **Report entity placeholder** — carries status + the pluggable completion-source seam
  (本地书写流 / 远程回传流, patient decision #9); 审核链 mechanics (可配置 0/1/2 级, user
  ruling 2026-11-18) land with the report-domain proposal.
- **Kernel tables have no schema of their own** — DDL ships as Flyway classpath resources
  each consuming service adds to its own ledger (hosted-domain precedent, naming ruling
  2026-11-17); this change verifies them against a test-only schema.
- Out of scope: 叫号域 (queue), 报告域 mechanics (审核/锁/合并/签名), 打印, department
  consumption (radiology/us), any HTTP surface, lumen-integration protocols.

## Capabilities

### New Capabilities
- `exam-workflow-kernel`: the kernel's registration-side behavior — exam-order aggregate,
  document state machine with legal/illegal transitions, exam-side numbering issuance +
  patient-registry port, HIS two-phase intake, and the extension-hook seam contract.

### Modified Capabilities
- `service-topology`: Shared Library Boundary restates `lumen-core` as a present shared
  library (its "future" wording goes stale the moment the module exists) and pins the
  kernel's department-neutral scope. The Aggregator Module Declaration needs no delta —
  its scenarios already hold once the module is declared in the pom.

## Constraints Inherited (not restated in specs)

- Dependency iron rule (patient decision #10): lumen-core never depends on remote;
  remote embeds lumen-core without rewriting it (#6). Kernel never imports
  department modules; departments depend on the kernel.
- Order↔patient linkage: `patient_uid` NOT NULL (占位患者行 exists from birth of the
  order); snapshot jsonb auto-refresh on `patient.changed` only while 报告未出
  (decision #4) — the refresh listener is consumer-side; the kernel exposes the
  snapshot-refresh service.
- v1 reserves the 申请渠道 `channel` field with a 远程申请 enum value present but not
  yet enabled (patient decision #30 note, remote readiness).

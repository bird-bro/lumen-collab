# Spec Delta

## Purpose

The exam-workflow kernel is the department-neutral core of the exam pipeline
(booking → registration → exam → report): one order aggregate, one document state
machine, exam-side numbering, and the seams departments extend. This delta establishes
its registration-side behavior so radiology and ultrasound workstations consume one
kernel instead of forking a god table per department.

## ADDED Requirements

### Requirement: Exam-Order Aggregate

The kernel SHALL model a registration order as an aggregate of four members persisted
together — the order document (`exam_order`), the exam execution (`exam_execution`),
the report placeholder (`exam_report`), and the patient reference (`patient_uid` NOT
NULL plus a jsonb 患者快照). No member SHALL carry a department-specific field; the
snapshot refresh SHALL be guarded by report state and the channel SHALL reserve the
remote value:

#### Scenario: Aggregate persists as four linked members

- **WHEN** the kernel saves a new registration order carrying execution, report, and
  patient data
- **THEN** all four members persist linked to the order's identifier
- **AND** the patient reference is NOT NULL

#### Scenario: Snapshot refresh guarded by report state

- **WHEN** the snapshot refresh runs for an order whose report is not 已出
- **THEN** the jsonb snapshot updates from the delivered patient data
- **WHEN** the same refresh runs for an order whose report is 已出
- **THEN** the refresh is refused and the stored snapshot is unchanged

#### Scenario: Channel reserves the remote value

- **WHEN** an order is created without an explicit channel
- **THEN** `channel` records 本地申请
- **AND** the channel value set contains 远程申请 as a defined but unenabled value

### Requirement: Document State Machine

The kernel SHALL drive every order through a single state machine —
已预约 →(签到)→ 已登记 → 处理中 → 已完成 — with the branches 取消预约 (from 已预约) and
已取消 (from 已登记 or 处理中). Booking creates an order as 已预约; walk-in registration
and HIS two-phase confirm enter directly as 已登记. The kernel SHALL reject any other
transition with a client-error signal and leave the stored status unchanged:

- Cancelling an order whose report is 已出 SHALL be refused.

#### Scenario: Booking enters at 已预约

- **WHEN** a booking creates an order
- **THEN** the order is stored in the 已预约 state

#### Scenario: Walk-in and HIS confirm enter at 已登记

- **WHEN** a walk-in registration commits without a booking
- **THEN** the order is stored in the 已登记 state
- **AND** the same holds for an order confirmed from the HIS intake cache

#### Scenario: Happy path advances through each stage

- **WHEN** 签到, 处理中, and 已完成 fire in sequence on a 已预约 order
- **THEN** the status advances 已登记 → 处理中 → 已完成, each step persisted

#### Scenario: Cancellation branches split by stage

- **WHEN** cancellation fires on a 已预约 order
- **THEN** the order lands in 取消预约
- **WHEN** cancellation fires on a 已登记 or 处理中 order whose report is not 已出
- **THEN** the order lands in 已取消
- **WHEN** cancellation fires on an order whose report is 已出
- **THEN** it is refused and the status is unchanged

#### Scenario: Illegal transition rejected

- **WHEN** a transition not defined by the state machine fires (e.g. 处理中 back to
  已预约, or any move out of 已完成)
- **THEN** the kernel rejects it with a client-error signal
- **AND** the stored status is unchanged

### Requirement: Number Issuer Port

The kernel SHALL define a number-issuer port seam — issuing one number for a rule
family (the 检查号 accession and 预约号 booking families among them) — that consuming
services adapt to the platform's number-rule service (the row-lock rule machinery is
platform-owned, not kernel-owned). Kernel registration SHALL take accession numbers
ONLY from a port result and SHALL fail fast when the issuer fails — a fallback that
fabricates numbers never exists (numbers are never reused):

#### Scenario: Registration issues the accession through the port

- **WHEN** a registration creates an order without a caller-brought accession
- **THEN** the stored accession number is the number-issuer port's result verbatim

#### Scenario: Issuer failure refuses the registration

- **WHEN** the number-issuer port fails during registration
- **THEN** the registration is refused and no order row survives

### Requirement: Patient Registry Port

The kernel SHALL define a patient-registry port seam — resolve-or-register semantics
returning the patient identity (`pat_uid`, `pat_id`, resolved state) — that consuming
services adapt to the patient service. Kernel order creation SHALL take identity only
from a port result and SHALL refuse creation without one:

#### Scenario: Order creation consumes the port

- **WHEN** kernel services create an order
- **THEN** the patient reference comes from a patient-registry port result
- **AND** order creation without a resolved identity is refused

#### Scenario: Incomplete port result refused

- **WHEN** a port result carries no `pat_uid`
- **THEN** the kernel refuses to link the order and reports the failure

### Requirement: HIS Two-Phase Intake

The kernel SHALL provide two-phase HIS intake: an apply-cache stage (raw source +
dictionary-mapped target; unmatchable exam items dropped at cache time) followed by a
manual confirm creating the formal order — no server-side auto-commit, corrections
allowed only before commit, and the HL7v2 protocol layer itself out of scope:

#### Scenario: Cache stores matched apply and drops unmatched items

- **WHEN** an inbound apply caches with one matchable and one unmatchable exam item
- **THEN** the cache row stores source and target with the matchable item
- **AND** the unmatchable item is dropped at cache time

#### Scenario: Manual confirm creates the order at 已登记

- **WHEN** confirm fires for a cached apply with a resolved patient identity
- **THEN** a formal order is created entering 已登记 and the cache row is marked committed

#### Scenario: No auto-commit from the cache stage

- **WHEN** an apply is cached
- **THEN** no order exists until the confirm fires

#### Scenario: Cached apply corrected before commit

- **WHEN** the upstream corrects a not-yet-committed cached apply
- **THEN** the cache row updates its target
- **WHEN** the same correction targets a committed cache row
- **THEN** it is rejected

### Requirement: Registration Extension Hooks

The kernel SHALL fire ordered registration extension hooks inside the registration
transaction; consuming services register their hooks (PACS worklist push, 叫号, 分单,
HIS outbound all live outside the kernel). A hook failure SHALL roll back the
registration so no partial order survives:

#### Scenario: Hooks fire in registered order

- **WHEN** a registration completes
- **THEN** every registered hook executes in registration order

#### Scenario: Hook failure rolls back the registration

- **WHEN** a hook raises during registration
- **THEN** the transaction rolls back and no order row survives

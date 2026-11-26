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

- **Change name**: patient-identity-card-projection
- **Problem to solve**:
The US workbench's resolve backfill never fills 就诊卡号: the archive stores visit cards
as `patient_identifier` rows (`idType 'card'` — the resolve chain walks them), but the
internal identity projection (`InternalPatientController.toIdentity`) reads the
`PatientMaster` only, so `PatientIdentityResponse.cardNo` is structurally absent — the
web type and the backfill mapping were built anticipating the field
(`us-orders.ts:180`, the page's `identity.cardNo ?? null`). Everything else the archive
carries (idType/idNo/name/gender/birthDate/phone + the derived age) already backfills;
就诊卡号 is the only gap (患者来源/住院号/床号/身高/体重 are exam-time attributes the

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/patient-domain/spec.md`
5:### Requirement: Internal Service Surface
18:#### Scenario: Resolve-or-register resolves an exact match
23:#### Scenario: Resolve-or-register registers a new archive
29:#### Scenario: Outbox pull and ack hand out events exactly once
35:#### Scenario: Internal-key guard refuses foreign callers
40:#### Scenario: The identity projects the archive's latest visit card


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [x] 1.1 Internal API test FIRST: the resolved identity carries the archive's latest
9:- [x] 1.2 Add `cardNo` to `PatientIdentityResponse` + the `toIdentity` projection
14:- [x] 2.1 Backend suite green — verify: `./mvnw test -q` exit 0 (JAVA_HOME pinned)

## Test Obligations

- **Behaviors requiring failing-test-first**: the resolved identity carries the latest
  card identifier; the no-card archive projects null.
- **Required edge cases**: resolve-by-cardNo echoes the searched card; multiple card
  identifiers -> the highest id wins; the resolve chain's three outcomes unchanged.
- **Regression-sensitive areas**: the outbox pull/ack; the A0715 conflict semantics;
  the internal-key guard.

## Execution Mode

- **Mode**: `Inline`
- **Selection rationale**: 2 tasks, two files, single stack — direct implementation.

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

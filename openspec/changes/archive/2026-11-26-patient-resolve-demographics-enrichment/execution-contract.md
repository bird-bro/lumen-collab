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

- **Change name**: patient-resolve-demographics-enrichment
- **Problem to solve**:
The composite resolve-or-register, when the chain MATCHES an existing archive, returns
the identity WITHOUT any update — the desk's typed demographics (证件号/出生日期/手机号)
ride the ORDER's snapshot only, so sparse archives (created from a minimal first
registration: name + gender + the resolve card) stay sparse forever, and every later
resolve keeps backfilling nulls (observed 2026-11-26: card 00114's second registration
typed phone etc., the archive 2244 still reads all-null). The user ruling: the desk's
information SHOULD update the patient info.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/patient-domain/spec.md`
5:### Requirement: Identity Resolution Chain
23:#### Scenario: Exact id_no hit resolves immediately
28:#### Scenario: Soft match returns candidates
34:#### Scenario: No match creates a new archive
40:#### Scenario: The composite match enriches the archive's null demographics


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [x] 1.1 Archive service test FIRST: a resolve-or-register over a sparse archive
10:- [x] 1.2 Implement the minimal-merge enrichment in the composite's matched branch
16:- [x] 2.1 Backend suite green — verify: `./mvnw test -q` exit 0 (JAVA_HOME pinned)

## Test Obligations

- **Behaviors requiring failing-test-first**: the null-fill + card append on a
  card-matched sparse archive; idempotency; the no-overwrite band.
- **Required edge cases**: repeated identical requests; the request card already an
  identifier; all fields present (no-op); the lookup path side-effect-free.
- **Regression-sensitive areas**: the chain's three outcomes; the A0715 race backstop;
  the outbox pull/ack.

## Execution Mode

- **Mode**: `Inline`
- **Selection rationale**: 2 tasks, one service file, single module.

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

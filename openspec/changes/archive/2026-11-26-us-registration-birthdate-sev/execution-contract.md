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

- **Change name**: us-registration-birthdate-sev
- **Problem to solve**:
The US registration create chain stops at age: the walk-in archive it builds
keeps `birth_date` null because nothing in the chain carries the value
(`RegistrationCreateRequest` has no birthDate; the kernel port request has no
birthDate). The Feign contract already carries birthDate
(`PatientResolveOrRegisterRequest.birthDate`, a resolve soft key) and the
patient domain accepts it — the US chain simply never hands it over. EIS
precedent: 出生日期 is a first-class registration form field.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: `RegistrationCreateRequest.birthDate` (Date, `@JsonFormat yyyy-MM-dd`,
  nullable) + kernel `PatientRegistrationRequest.birthDate` + `toPortRequest`
  pass-through + adapter javadoc; TDD via `UsRegistrationApiTests` stub seam.
- **Out of scope**: Feign/patient-domain edits (contract already carries birthDate);
  snapshot birth date (`PatientSnapshot` stays age-only); controller changes;
  resolve-key UI changes; any web work (lives in us-registration-birthdate-web).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/us-station/spec.md`
3:### Requirement: Birth Date Capture
12:#### Scenario: Walk-in birth date joins the archive chain
19:#### Scenario: Birth date is optional


## Design Constraints

- **Architecture/Decisions**:
- **D1 — binding**: `birthDate` is `java.util.Date` with
  `@JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")` on
  `RegistrationCreateRequest` — the web submits a date-only string; nullable,
  no default. Mirrors the file's existing `bookingTime` annotation habit.
- **D2 — kernel port (lumen-core)**: `PatientRegistrationRequest` gains the
  `birthDate` field (additive; the builder class grows one member). It is a
  resolve key, so it rides the contract unlike the exam-time extras; the
  adapter javadoc's copied-keys list gains birthDate (doc-only edit).
- **D3 — pass-through**: `toPortRequest` adds
  `.birthDate(request.getBirthDate())` to the builder chain. The adapter's
  `copyProperties(request, contract, true)` picks it up unchanged.
- **D4 — snapshot stays age-only**: `toSnapshot`/`PatientSnapshot` untouched;
- **Technical constraints**: JDK 11 (`export JAVA_HOME=.../jdk-11.jdk/Contents/Home`);
  reactor builds only from `lumen-sev/` (`./mvnw -pl lumen-us -am test -q`);
  additive-first — new DTO fields, no existing-method signatures touched;
  `@author birdbro` headers on any new class (none expected).

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 Red: `UsRegistrationApiTests` — walk-in create with `birthDate`
9:- [ ] 1.2 Green: `RegistrationCreateRequest` + `birthDate` (`Date`,
17:- [ ] 2.1 `./mvnw -pl lumen-us -am test -q` (JDK 11) — full lumen-us suite green
19:- [ ] 2.2 Confirm no other consumer of `PatientRegistrationRequest` breaks

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: both spec scenarios —
  "Walk-in birth date joins the archive chain" (captured contract body carries
  birthDate) and "Birth date is optional" (null pass-through regression guard)
  get red tests in 1.1 before the green fields in 1.2.
- **Required edge cases**: absent birthDate → contract null + unchanged
  resolution path; date-only string binding at GMT+8 midnight (calendar date
  preserved into the archive).
- **Regression-sensitive areas**: existing walk-in create flow (stub seam
  captures, accession prefix assertions); other `PatientRegistrationRequest`
  consumers (lumen-radiology/lumen-endo) — additive field, builder-safe (task 2.2).

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 4 tasks, one stack, one reactor (lumen-core+lumen-us
  build together via `-am`), no risk indicators — same mode as the
  us-apply-info-sev / us-booking-slot-picker-sev precedents.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: red→green ordering (tests 1.1 before fields 1.2);
  full lumen-us suite green before verify.
- **Blocking categories**: kernel signature breaks (never-modify rule); snapshot
  field creep beyond D4.

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope

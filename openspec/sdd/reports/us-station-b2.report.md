# Report: us-station-registration-sev Batch 2 (tasks 2.1-2.2)

## Status

DONE_WITH_CONCERNS — both tasks implemented and green (internal suite 9/9; full patient
module 54/54; L1 full-reactor compile exit 0). One REAL pre-existing bug was uncovered
and routed around additively (the A0715 race backstop, see Self-review/Concerns); one
batch-3 contract gap flagged (lookup-only resolve).

## What was implemented

### Task 2.1 — `lumen-api/patient` contract (ADD-ONLY, NumberApi precedent)
New package `org.birdbro.lumen.api.patient` — six new files, ZERO tracked-file changes
in lumen-api (git-verified):
- `PatientApi` — `@FeignClient(name = "lumen-patient-registry",
  url = "${lumen.api.patient.url:}", fallback = PatientApiFallback.class)` with the
  three endpoints the task pins: `POST /api/v1/patient/internal/patients/resolve-or-register`
  (`R<PatientResolveOrRegisterResult>`), `GET /api/v1/patient/internal/events/outbox`
  (`R<List<PatientOutboxEntry>>`, `limit` param), `POST
  /api/v1/patient/internal/events/outbox/{id}/ack` (`R<Boolean>`). Javadoc cites the
  spec requirement + the internal-key header mechanism (D3/D4).
- `PatientApiFallback` — fail-fast C0001 throws on all three (绝不造身份 / 绝不伪造
  投递 / 绝不伪造消费确认 — the NumberApiFallback discipline).
- `PatientResolveOrRegisterRequest` — the resolve chain's keys 1:1 (idType/idNo/cardNo/
  name/gender/birthDate/phone/provisional).
- `PatientIdentityResponse` — patUid/patId/state + the archive display fields
  (idType/idNo/name/gender/birthDate/phone); deliberately decoupled from the patient
  entity.
- `PatientResolveOrRegisterResult` — result (MATCHED/CANDIDATES/CREATED, the patient
  domain's own vocabulary) + identity + candidates; the adapter (task 3.1) refuses on
  a missing identity.
- `PatientOutboxEntry` — id/eventType/payload (RAW jsonb document as string)/
  createTime/updateTime.

### Task 2.2 — patient-side internal surface
- `config/InternalKeyInterceptor` — the patient-side COPY of the system manage/config
  precedent (additive-first, no framework change; lumen-system is not on this
  classpath): fail-CLOSED on an unconfigured key, HTTP-401 envelope on a
  missing/wrong header.
- `config/PatientInternalApiWebConfig` — registers the interceptor on exactly
  `/api/v1/patient/internal/**` (a patient-domain configurer, NOT a framework edit).
- `application.yml` — the service's FIRST `security.permit-all` entry:
  `/api/v1/patient/internal/**` (tokenless service calls must reach the key guard,
  never the JWT chain); the patient-domain "deliberately ABSENT" comment block amended
  in place.
- `service/PatientEventOutboxDeliveryService` (+impl) — NEW top-level interface + impl
  for the consumer-side outbox state machine: `pullPending(limit)` (PENDING, id
  ascending, LIMIT-capped, pure SELECT — no claim: entries stay visible to re-pull
  until acked) and `ack(id)` (ONE guarded UPDATE `id = ? AND status = PENDING` →
  CONSUMED; zero-row = idempotent no-op SUCCESS; rows kept for audit). Deliberately
  SEPARATE from the append-only `PatientEventOutboxService` so the append-only
  reflection pin (`PatientOutboxTests`) passes UNTOUCHED; no custom mapper statement
  (inherited MP members only — the mapper-declares-no-SQL pin also stays true).
- `controller/InternalPatientController` — `/api/v1/patient/internal/**`, NO permission
  annotations, NO user-token dependency; resolves through the composite (below);
  payload Map → raw jsonb string via the injected Jackson mapper.
- `PatientArchiveService.resolveOrRegister` — ADDITIVE interface method (the
  `create(request, oid)` overload precedent): composes the EXISTING `resolve` chain in
  ONE transaction (self-invocation within its own `@Transactional`) and surfaces the
  identity race with the existing A0715 payload — see Self-review for the bug it
  routes around. The controller maps contract↔domain 1:1 (house
  `BeanUtil.copyProperties(src, dest, true)`).

## TDD evidence

Contract RED lives in the patient-side suite (lumen-api has no test classes — the
NumberApi precedent): the suite consumes the DTOs, so the first RED is a compile
failure. Commands from `lumen-sev/` with `JAVA_HOME=...jdk-11.jdk/Contents/Home`.

| Step | Command | Result |
|:--|:--|:--|
| RED 1 (contract) | `./mvnw -pl lumen-patient -am test-compile -q` | exit 1 — `程序包org.birdbro.lumen.api.patient不存在` |
| RED 2 (behavior) | `./mvnw -pl lumen-patient -am test -q -Dtest=PatientInternalApiTests` | exit 1 — 9/9 fail (203 JWT-chain rejections; no permit-all, no endpoints) |
| GREEN | same, after 2.2 implementation | exit 0 — **9/9** |
| Regression | `./mvnw -pl lumen-patient -am test -q` | exit 0 — **54/54** across 9 suites (incl. `PatientOutboxTests` 3/3 append-only pins, `PatientArchiveServiceTests` 18/18, `PatientApiTests` 7/7 A0715 route test) |
| L1 | `./mvnw compile -q` | exit 0 — full reactor |

(The `PatientPermTests.txt` failure line in stale surefire output is dated Nov 18 — a
prior session's residue; that class is not in the tree and not in this run.)

Scenario coverage (the acceptance gate's four WHEN/THEN):
1. **Exact match → existing identity, no new row** — composite MATCHED, patUid/patId/
   state asserted, `COUNT(*) = 1`.
2. **No match → create** — placeholder (provisional=true) CREATED with PROVISIONAL
   state + issued pat_id persisted; `A0715` proven through the composite by the
   deterministic backstop race test (uncommitted competing row on a separate
   connection → the chain's lookups cannot see it → composite INSERT blocks on the
   index entry → competing row commits mid-flight → the EXISTING A0715 payload naming
   the duplicate's pat_id); plus the convergence proof (duplicate id pair → MATCHED,
   never an invented conflict) and the `A0410` typeless-id_no verbatim proof.
3. **Outbox pull/ack exactly once** — pull hands out PENDING marker entries id-ordered;
   re-pull WITHOUT ack hands the SAME ids out; a pull alone flips nothing (PENDING
   count intact); ack removes exactly the acked id; the row stays CONSUMED for audit;
   ack replay + ack of a missing row = no-op success; limit=1 caps the batch; entry
   shape (id/eventType/raw-payload-string/audit timestamps) asserted.
4. **Internal-key refusal** — keyless + wrong-key calls answer HTTP 401 envelope
   BEFORE the service layer (no archive row written) on all three endpoints.

## Files changed

- New: `lumen-sev/lumen-api/src/main/java/org/birdbro/lumen/api/patient/` (6),
  `lumen-sev/lumen-patient/.../config/InternalKeyInterceptor.java`,
  `.../config/PatientInternalApiWebConfig.java`,
  `.../controller/InternalPatientController.java`,
  `.../service/PatientEventOutboxDeliveryService.java` (+impl),
  `lumen-sev/lumen-patient/src/test/.../PatientInternalApiTests.java`,
  `PatientUsB2Fixtures.java`
- Modified: `PatientArchiveService.java` (+1 additive method + javadoc),
  `PatientArchiveServiceImpl.java` (+1 method, +1 `@Autowired` field — the documented
  LogController additive-DI shape; existing constructor byte-identical),
  `application.yml` (permit-all entry + comment amendment),
  `PatientIntegrationTestBase.java` (additive `@DynamicPropertySource` + test key
  constant — keeps every patient suite on ONE shared context)
- lumen-core / lumen-system / lumen-us: **zero changes**

## Self-review

- **Add-only lumen-api**: `git diff -- lumen-sev/lumen-api` is EMPTY (six untracked new
  files only) ✓
- **Internal endpoints never behind user perms**: no `@PreAuthorize` anywhere on the
  internal controller; the family rides `security.permit-all` + the fail-closed key
  interceptor (the system `/issue` shape, patient copy) ✓
- **A0715 untouched**: the payload comes from the impl's own private
  `identityConflict` (verbatim reuse — no duplicated message format); the sequential
  conflict semantics are pinned green by the existing `PatientArchiveServiceTests`
  18/18 + `PatientApiTests` A0715 route test ✓
- **Transaction boundary**: the composite's `@Transactional` opens the ONE transaction
  the chain runs in; the race catch reads the winner through a REQUIRES_NEW template
  (escapes the aborted tx); conversion happens ONLY when a live holder is confirmed —
  otherwise the original failure is rethrown untouched ✓
- **Never-modify discipline**: interface growth (overload precedent), additive DI
  field, new files elsewhere; existing method bodies byte-identical ✓

## Concerns

1. **REAL pre-existing bug found (not fixed — out of scope, flagged for the parent):
   create()'s unique-index backstop is dead code.** The in-service A0715 check and the
   resolve chain's exact step query the same rows, so the ONLY way the partial unique
   index fires is a concurrent race — and the catch block's backstop lookup then runs
   INSIDE the already-aborted transaction, where PostgreSQL rejects it with 25P02
   ("current transaction is aborted"). The javadoc/comment claims "the
   DuplicateKeyException re-throws as the SAME A0715 payload" — that can never happen;
   a racing duplicate create answers 500 (系统异常) today. Fixing it would mean
   editing an existing method body (the never-modify rule forbids me). The composite
   routes around it additively (multi-catch `DuplicateKeyException |
   UncategorizedSQLException` → REQUIRES_NEW re-read → existing A0715 or rethrow); the
   DIRECT create route keeps its existing (broken) race behavior. If the parent wants
   the root fixed, that is a deliberate never-modify-rule exception.
2. **Real-race residue window**: if the winning transaction has not committed yet when
   the composite's fresh read runs (no blocking index wait in a true parallel race),
   no live holder is visible and the raw failure propagates — the consumer's retry
   then converges via MATCHED. The spec scenario is proven deterministically by the
   blocking-injector test; this window is the honest limit of "A0715 unchanged"
   without touching create().
3. **Batch-3 contract gap flagged: the us-side resolve passthrough needs a LOOKUP-ONLY
   mode.** The us-station spec scenario ("US-side resolve passthrough ... an empty
   result returns with no side effect") cannot be served by today's contract — the
   composite CREATES on no-match (that is its job). Task 3.1 will need an additive
   contract growth (e.g. a lookup flag or a read-only method); the contract is
   add-only so that lands cleanly then.
4. **tasks.md checkboxes + loop-state artifacts_hash left to the loop** (B1 precedent:
   checkbox state is loop-managed; the implementer report is this file).

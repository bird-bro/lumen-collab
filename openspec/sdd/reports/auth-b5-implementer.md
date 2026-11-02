# Batch 5 Implementer Report - Documentation & spec hygiene (tasks 5.1-5.3)

Status: **DONE** - all three tasks plus both coordinator addendum records landed.
Docs/config only: no source file changed (backend and web untouched except one temporary,
already-deleted probe file), nothing committed, no tasks.md checkbox ticked.
`openspec validate auth-rbac-foundation --strict` exits 0 with no warnings.

## 5.1 - Coordination doc (`openspec/coordination/init-project-skeleton.md`)

Five in-place annotations; the decision log was never rewritten (flag text and the QUEUED
wording survive inside their closure/re-run notes).

1. **create_by bigint flag CLOSED** (lines 214-218, inside the 2026-10-29
   "API/data/error contracts aligned to OPS verbatim" decision). The original flagged text
   (`Integer` (int32) - **flagged**: if lumen user PKs are snowflake `bigint`, `create_by`/
   `update_by` must widen to bigint in the `lumen-auth` change) is kept verbatim; appended
   inline: **Flag CLOSED (2026-11-02, auth-rbac-foundation)**: ruled **stay Integer** -
   PKs are Integer auto-increment per this change's data delta
   (`openspec/changes/auth-rbac-foundation/specs/data/spec.md`: user decision 2026-11-02,
   the bigint-snowflake clause is withdrawn); no widening ever needed since
   `user_info.id` is Integer. The sentence flows back into the untouched
   `init-web-skeleton`'s planned ... continuation.
2. **Flyway/PG watch-item** (coordinator addendum; new decision bullet, lines 221-228,
   end of Decisions section before `## Change Registry`): team test server PostgreSQL
   14.11 vs Boot 2.5.15-managed `flyway-core` 7.7.3 (NOT 7.14.0/7.15) - PG 14 triggers
   only a WARN ("not tested, latest supported version 13"; decompile-verified: warn, no
   throw), migrations plain DDL, suite green 34/34; accepted as-is with **zero version
   deviation** from the OPS train; IF a future migration misbehaves, the design-gated fix
   is a lumen-only `flyway-core` pin to 7.15.0 (first 7.x with PG 14 support) via
   proposal - never a silent dependency bump. - 2026-11-02
3. **Endpoint Documentation alignment registered** (lines 239-243, Change Registry area,
   between the existing "Batch 5 traceability" note and the mutation-control note): the
   MAIN api spec's `Endpoint Documentation` requirement still exemplifies `code: 0`
   (verified stale: `openspec/specs/api/spec.md` line 35); it gets aligned when this
   change's api delta archives -
   `openspec/changes/auth-rbac-foundation/specs/api/spec.md` (its scenarios carry
   `"200"`, 3 occurrences); `openspec/specs/` stays untouched until then. - 2026-11-02
4. **Envelope mutation control re-run** (lines 245-252): QUEUED paragraph replaced by the
   RE-RUN record (the queued trigger is named: "previously QUEUED for the first
   endpoint-owning change") - see evidence below. Original QUEUED text retained inside
   the record as history.
5. **Authority-gating deferral** (coordinator addendum; lines 278-282, Gate area
   immediately after the existing "Deferred naming input (final review Minor-6)" note):
   the User Management requirement says "authenticated administrator" - implementation
   enforces authentication only (any valid token; no task in that change mandates
   authority checks); permission gating queued to the DataScope/radiology change;
   recorded so archive/verify is not silent about it.

## Envelope mutation control evidence (task 5.1.2 / doc line ~245)

Read-only probe on the web stack; type check = `pnpm exec tsc --noEmit` (the type-check
half of `pnpm build`; `tsconfig.json` includes `src`, strict mode).

- Temporary probe `lumen-web/src/envelope-mutation-probe.ts` (imported `ApiEnvelope` from
  the `./api` barrel, assigned an `ApiEnvelope<{ id: number }>` variable with numeric
  `code: 200` - the pre-OPS envelope shape).
- RED run: `pnpm exec tsc --noEmit` →
  `src/envelope-mutation-probe.ts(10,3): error TS2322: Type 'number' is not assignable to type 'string'.` **EXIT=2**.
- Probe deleted (`rm`); `src/` listing back to its 10 pre-existing entries.
- Clean re-run: `pnpm exec tsc --noEmit` → no output, **EXIT=0**.
- Verdict recorded in the doc: **Control PASSES** - the string-code contract is enforced
  at the type level. Not-run reason: none (toolchain did not block).

## 5.2 - `lumen-auth/CLAUDE.md` absence + verify.config.yaml comment

- `ls lumen-sev/lumen-auth/` → `pom.xml  src  target`; `ls lumen-sev/lumen-auth/CLAUDE.md`
  → "No such file or directory". **Confirmed absent** - role file `lumen-sev/CLAUDE.md`
  covers it; nothing created.
- `openspec/verify.config.yaml` line 13, comment text ONLY (command `./mvnw test -q`
  unchanged, column alignment kept):
  - Before: `test: ./mvnw test -q        # zero infrastructure required (init-backend-skeleton guarantee)`
  - After: `test: ./mvnw test -q        # lumen-auth tests need the team test server via gitignored lumen-sev/.env-test.local (unconfigured = explicit failure); framework/common tests stay zero-dependency`
- `build` line (line 12) and the `lumen-web` stack block untouched.

## 5.3 - Strict validation

Command (repo root): `openspec validate auth-rbac-foundation --strict`

```
Change 'auth-rbac-foundation' is valid
EXIT=0
```

Exit 0, single info line, no warnings.

## Out of scope / not done (per brief)

- No `openspec/specs/` edits (alignment happens at archive time via the delta).
- No tasks.md checkbox ticks (coordinator ticks).
- No commit (coordinator commits after review).
- Working tree after this batch: `openspec/coordination/init-project-skeleton.md` and
  `openspec/verify.config.yaml` modified; `openspec/sdd/reports/auth-b5-implementer.md`
  added; no other changes.

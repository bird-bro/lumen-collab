# Batch 5 Brief: Documentation & spec hygiene (tasks 5.1-5.3)

## Goal

Close the coordination-doc flags this change resolved, re-run the queued envelope
mutation control, update verify.config.yaml semantics, and pass strict validation.

## Task text (tasks.md 5.1-5.3)

- **5.1** Coordination doc (`openspec/coordination/init-project-skeleton.md`):
  1. Close the "create_by 升宽 bigint" flag (in the 2026-10-29 "API/data/error contracts
     aligned to OPS verbatim" decision, lines ~211-215): ruled **stay Integer** - user
     decision 2026-11-02, PKs are Integer auto-increment per this change's data delta
     (snowflake withdrawn); no widening ever needed since `user_info.id` is Integer.
     Keep the flag text, append the closure note inline (this doc is a decision log -
     annotate, don't rewrite history).
  2. "Envelope mutation control - QUEUED" (line ~227): re-run the type-level negative
     control now that the first envelope consumer exists. The control: a deliberate
     type error against `lumen-web/src/api`'s `ApiEnvelope` type must be REJECTED by
     tsc (TS2322). Procedure (backend agent, but this is a read-only probe on the web
     stack): add a temporary `.ts` file in `lumen-web/src` assigning a wrong-shaped
     value to an `ApiEnvelope`-typed variable, run the web type check (`cd lumen-web &&
     pnpm build` or `pnpm exec tsc --noEmit` - check package.json scripts), capture the
     TS2322 evidence, DELETE the temp file, re-run the clean check. If the toolchain
     blocks you, record not-run + reason instead of forcing it. Update the doc entry:
     QUEUED → re-run 2026-11-02 (auth-rbac-foundation Batch 5), paste the 1-line
     evidence.
  3. Register that the stale `Endpoint Documentation` example (`code: 0`) in the MAIN api
     spec gets aligned when THIS change's api delta archives (the delta's scenarios
     carry `"200"`). One line in the change registry / decisions area of the
     coordination doc, cross-referencing `openspec/changes/auth-rbac-foundation/specs/api/spec.md`.
- **5.2** `lumen-auth/CLAUDE.md` must NOT be created (role file lumen-sev/CLAUDE.md
  covers it) - verify it does not exist. Update `openspec/verify.config.yaml` backend
  `test` comment: lumen-auth tests now need the team test server connection
  (gitignored `lumen-sev/.env-test.local`, unconfigured = explicit failure);
  framework/common tests stay zero-dependency. Keep `build`/`test` COMMANDS unchanged.
- **5.3** `openspec validate auth-rbac-foundation --strict` → exit 0, no warnings.

## Notes

- Edits in 5.1/5.2 are coordination/config docs - do NOT touch `openspec/specs/` (main
  specs are read-only for agents; alignment happens at archive time via the deltas).
- Do NOT tick tasks.md checkboxes (coordinator ticks them).
- Do NOT commit yet (coordinator commits after review) unless your instructions say
  otherwise. Report first.

## Report

Write to `openspec/sdd/reports/auth-b5-implementer.md`: exact edits made (with line
references), the mutation-control evidence (or not-run reason), validate output, and
confirm `lumen-auth/CLAUDE.md` absent.

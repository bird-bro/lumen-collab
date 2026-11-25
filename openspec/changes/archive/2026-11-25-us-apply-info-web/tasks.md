# Tasks: us-apply-info-web (retrospective — all landed, evidence per task)

> The implementation landed before this closure file was written; verify.md PASS
> 2026-11-25 (2/2, pnpm build + pnpm test green) is the credential.

## 1. Form

- [x] 1.1 请检信息 gains the 申请科室 / 申请医生 / 申请时间 inputs inside the section chrome — evidence: index.tsx 请检信息 section (Field label=申请科室/申请医生/申请时间, the 88px label chrome); page suite green
- [x] 1.2 The trio submits inside the create body's `ext` block as optional strings (blank allowed, trimmed) — evidence: the save() ext block assembly + the create posts test green

## 2. Guardrails

- [x] 2.1 The trio is NOT coupled to lumen org/user/dict vocabularies (free text; the reserved id anchors stay unsent) — evidence: the ext submit test asserting the verbatim strings round-trip
- [x] 2.2 The list rows do NOT render the trio in this change — evidence: no apply-dept cell in the wide table/rail; page suite green

## 3. Verification hooks

- [x] 3.1 `pnpm build` exit 0; `pnpm test` full suite green — evidence: verify.md L1/L3
- [x] 3.2 `openspec validate us-apply-info-web` passes — evidence: the synced ADDED requirement (Apply Origin Fields)

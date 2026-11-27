# Tasks

<!-- The implementation this change records ALREADY LANDED and is committed
     (2295574 + the us-order-exam-room-web archive sweep). The tasks below are the
     reconciliation/verification pass that proves the spec text matches the shipped
     behavior - nothing ships new code. -->

## 1. Reconciliation audit

- [x] 1.1 Walk the shipped compact card (`src/pages/us/registration/index.tsx`) against
      each delta scenario (anatomy, anchor semantics, discipline) and confirm the spec
      text matches observed behavior; fix spec wording where it drifts, never the code
- [x] 1.2 Confirm the ConsoleAction `cancel` key and the ×-circle call site pair in
      `src/pages/system/console-shell.tsx`, and that no 删除 verb uses the cancel key

## 2. Verification

- [x] 2.1 Run the registration page suite (`pnpm vitest run
      src/test/us-registration-page.test.tsx`) - all tests green including the
      position assertions (the head lead owns the source, the time band owns the
      status chip, the ids row owns the 急 glyph)
- [x] 2.2 Typecheck (`pnpm exec tsc --noEmit`) exits 0
- [x] 2.3 L2 check: every delta scenario maps to at least one shipped test or an
      explicit user visual confirmation recorded in the change (the 2026-11-26
      真机 walkthrough covers the anatomy; record it in verify.md)

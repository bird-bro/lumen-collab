# Tasks: password-transport-encryption-web

## 1. Crypto util (TDD)

- [x] 1.1 `pnpm add sm-crypto` (only new dependency).
- [x] 1.2 Write failing `src/test/password-cipher.test.ts` (design T1): fixed-vector
      interop literal (same plaintext + keypair + expected ciphertext hex as the
      backend `PasswordCipherHelperTest` — coordination D4), key fetch + module cache
      (two encrypts → one `/auth-key` call), cache cleared on `A0400`.
- [x] 1.3 Implement `src/utils/password-cipher.ts` (`@/api/client` for the envelope):
      `fetchAuthKey()` → `GET /auth-key`, module cache, `encryptPassword(raw)` →
      `sm2.doEncrypt(raw, publicKey, 1)` hex; cache-clear on `A0400` (design D3).
      Green: 1.2 passes.

## 2. Call sites (TDD)

- [x] 2.1 Update `src/test/login-page.test.tsx` (red): submit asserts the request
      body's `password` is a hex string ≠ the typed value and the typed value appears
      nowhere in the body (design T2; Enter-key path covered by the updated existing
      test).
- [x] 2.2 Wire `pages/login/index.tsx` submit handler: `encryptPassword(password)`
      before building the body; pending/error UX untouched. Green: 2.1.
- [x] 2.3 Update user-management tests (red): `createUser` / `resetPassword` request
      bodies carry hex ciphertext (design T3).
- [x] 2.4 Wire `src/api/users.ts`: `createUser` / `resetPassword` encrypt inside the
      client (raw-password signatures unchanged, design D2). Green: 2.3.

## 3. Verification

- [x] 3.1 `pnpm test` all green; `pnpm build` clean. Evidence in verify.md.
- [ ] 3.2 Joint smoke with the real backend (backend change task 3.2): UI login over
      the dev proxy succeeds; DevTools network body shows hex ciphertext, typed
      password nowhere; user create + reset round-trip. Evidence recorded in verify.md.
      > Automated layers DONE 2026-11-09: unit tests assert the wire body (04-hex
      > ciphertext, typed value absent) at BOTH call sites through the real encrypt
      > path; the api change's curl smoke proved the live backend accepts this exact
      > wire format (sm-crypto -> JWT). UI layer pends the user's browser verification
      > (restart lumen-system with the new code, then DevTools network panel).

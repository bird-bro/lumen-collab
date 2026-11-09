# Proposal: password-transport-encryption-web

## Why

The sibling backend change `password-transport-encryption-api` makes every
password-bearing endpoint REJECT plaintext (clean cutover, `A0400`): the `password` of
`POST /api/v1/system/login`, the `password` of `POST /api/v1/system/users`, and the
`newPassword` of `PUT /api/v1/system/users/{id}/password` must now arrive as SM2
ciphertext (C1C3C2, hex). The SPA is the only API consumer, so it must fetch the
anonymous key (`GET /api/v1/system/auth-key`) and encrypt before submit — the EIS
reference's `passwordSecret` pattern (`lumen-reference/code/eis-web/.../utils/http.ts`),
modernized with sm-crypto instead of the legacy `window.RSAUtils`. Cross-stack parent:
`openspec/coordination/password-transport-encryption.md` (decisions D1–D9; algorithm SM2
per user final ruling 2026-11-09).

## What Changes

- **NEW** dependency `sm-crypto` (pure JS SM2, Vite-safe) — the ONLY new package.
- **NEW** `src/utils/password-cipher.ts`: fetch + module-cache of
  `{ keyId, publicKey }` from `GET /api/v1/system/auth-key` (anonymous, no token header
  needed) and `encryptPassword(raw: string): Promise<string>` →
  `sm2.doEncrypt(raw, publicKey, 1)` hex output (coordination D4/D8).
- **MODIFIED** login submit (`src/pages/login/index.tsx`): the typed password is
  encrypted BEFORE the `POST /system/login` body is built; the typed value itself never
  enters the request body.
- **MODIFIED** user-management API calls (`src/api/users.ts`): `createUser` and
  `resetPassword` encrypt their password argument before sending (call-site keeps the
  human-readable raw-password signature; encryption is inside the API boundary).
- Failure UX unchanged in mechanism: a backend `A0400` (stale key after rotation etc.)
  surfaces through the existing `ApiError` paths — the login page's inline error box,
  the users page's message. No auto-retry (coordination D8, YAGNI).
- No routing, store, or test-harness changes beyond the encrypt step.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-auth-flow`: the Login Page requirement gains the encrypt-before-submit behavior
  (typed password never leaves the browser as plaintext; key fetched from `/auth-key`
  and cached).
- `web-system-console`: the user-management requirement's create + password-reset
  actions encrypt the password fields before their documented endpoints are called.

## Impact

- `lumen-web/package.json`: +`sm-crypto`.
- `lumen-web/src`: new `utils/password-cipher.ts` + test; edits to
  `pages/login/index.tsx` (submit handler), `api/users.ts` (two functions); tests
  updated (`login-page.test.tsx`, `api-clients.test.ts` / `api-clients.test.ts` user
  cases) to assert the wire body carries ciphertext, never the typed plaintext.
- Wire contract: owned by the coordination doc D3/D4/D5 (key endpoint response shape,
  C1C3C2-hex, A0400 semantics) — implemented here, specified in the sibling change's
  `api` delta.
- Out of scope: key rotation UX (no auto-refetch loop), any non-password encryption,
  TLS config (deployment form), the future change-own-password page (inherits
  `encryptPassword` for free).

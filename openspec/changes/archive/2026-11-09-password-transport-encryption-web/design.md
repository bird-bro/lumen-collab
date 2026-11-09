# Design: password-transport-encryption-web

## Reference Lineage Map

| lumen artifact | reference source |
|:--|:--|
| `src/utils/password-cipher.ts` | EIS `utils/http.ts` `passwordSecret` (`getKeyPair` → encrypt helper), modernized: sm-crypto + module cache instead of `window.RSAUtils` + per-call fetch |
| encrypt call sites | login submit (`pages/login/index.tsx`), user create/reset (`api/users.ts` `createUser` / `resetPassword`) |

## Decisions

- **D1 sm-crypto, C1C3C2, hex (coordination D1/D4)**: `sm2.doEncrypt(raw, publicKey, 1)`
  — cipherMode `1` = C1C3C2 (GM/T 0003-2012 ordering), matching Hutool 5.8.20's backend
  default; output is a hex string accepted verbatim by the backend's Hutool decrypt.
  Public key input is the 130-char hex uncompressed point exactly as `/auth-key` returns
  it — zero format conversion. The interop fixed-vector test pins the output against the
  same literal the backend test uses (coordination D4).
- **D2 utility placement + API boundary**: `password-cipher.ts` lives in `src/utils/`
  (sibling of `perm.ts`); the LOGIN PAGE and the API CLIENT (`api/users.ts`) both call
  it. Encryption for user management happens INSIDE the api-client functions so every
  current and future caller of `createUser`/`resetPassword` is safe by construction; the
  login page calls it in the submit handler because the credential assembly is page
  logic. Signatures that humans read stay raw (`resetPassword(id, password: string)`) —
  the wire semantics live in one place.
- **D3 key fetch + cache (coordination D8)**: module-level `let cached: {keyId,
  publicKey} | null`; `encryptPassword` fetches on first use. The static-config keypair
  (backend D2) makes the cache valid for the deployment's lifetime; there is NO TTL and
  NO auto-refetch — after a server-side rotation the next submit gets `A0400`, the
  inline message shows the backend msg, and the FOLLOWING submit refetches once
  (cache cleared on any `A0400` from a password endpoint — a single, bounded
  self-heal step, not a retry loop). `fetchKey` goes through the existing `apiRequest`
  client so the standard envelope unwrap + error path apply; the endpoint is anonymous
  (no token needed; a stale token must NOT be attached-and-fail — the anonymous path
  tolerates its absence, matching `/login`'s existing behavior).
- **D4 failure UX (coordination D8)**: no new error machinery. Login: existing inline
  `.error-info` box (`ApiError.msg`). Users page: existing message flow. The backend
  msg text (密码解密失败，请刷新页面后重试) is self-explanatory for the rotation edge.
- **D5 dependency hygiene**: `sm-crypto` added as a direct dependency (it is called
  directly, not a transitive), pinned per lumen-web's existing version policy. No other
  package changes.
- **D6 tests assert the wire, not the math (house test seam)**: unit tests mock
  `@/api/...` at the seam (existing rule — unit tests stub the API client, they never
  serve page data). New tests: (a) `password-cipher.test.ts` — fixed-vector: with the
  recorded interop literal, `encryptPassword` returns the expected hex for a known
  input (key endpoint mocked); (b) `login-page.test.tsx` — submit issues a request whose
  `password` field is a hex string ≠ typed value, and the typed value appears nowhere
  in the body; (c) user-management tests — create/reset bodies carry hex ciphertext.
  The REAL decrypt-acceptance proof lives in the backend integration tests (sibling
  change, task 2.2(f)) plus the joint manual smoke (backend task 3.2).

## Error Handling

| case | surface |
|:--|:--|
| `/auth-key` unreachable at first encrypt | `ApiError` propagates → login inline box / users-page message (same as any backend failure today) |
| backend `A0400` on submit (stale key / tampered) | backend msg shown inline; cache cleared so the next attempt refetches |
| empty password | existing client-side validation fires BEFORE encryption (unchanged order) |

## Testing (Vitest + Testing Library, existing harness)

- **T1** `password-cipher.test.ts`: key-fetch caching (one `/auth-key` call for two
  encrypts), fixed-vector output, cache-clear-on-A0400 behavior.
- **T2** `login-page.test.tsx`: submit → request body `password` is hex ≠ typed value;
  typed value absent from the entire body; Enter-key path identical (existing test
  updated, not duplicated).
- **T3** user create/reset tests: bodies carry hex ciphertext; raw signatures unchanged.
- **T4** `pnpm test` + `pnpm build` green; joint manual smoke with the real backend
  (backend change task 3.2) closes the loop end-to-end.

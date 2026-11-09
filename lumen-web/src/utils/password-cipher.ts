/**
 * SM2 password-transport cipher (password-transport-encryption-web task 1.3;
 * coordination password-transport-encryption D3/D4/D8; EIS precedent
 * `lumen-reference/code/eis-web/.../utils/http.ts` `passwordSecret`, modernized).
 *
 * Wire contract (D4, refined during the backend apply): the request's password field
 * is SM2 C1C3C2 hex in the STANDARD uncompressed-point encoding - `"04" + sm-crypto
 * doEncrypt(raw, publicKey, 1)`. sm-crypto's native output is MARKERLESS (its C1 has
 * no leading 04 byte) while the backend's BouncyCastle engine requires the point
 * header, so THIS util owns the prepend. The fixed interop vector lives in
 * `src/test/password-cipher.test.ts` - the exact literal the backend helper pins.
 *
 * Key lifecycle (D3): the anonymous `GET /system/auth-key` response is cached
 * module-level for the session (the backend keypair is static per deployment). After
 * a server-side key rotation the next submit gets `A0400` - callers surface the
 * backend msg inline and call {@link invalidateKeyCache} so the FOLLOWING submit
 * refetches (a single bounded self-heal step, never an automatic retry loop).
 */
import { apiRequest } from '@/api/client'
import { sm2 } from 'sm-crypto'

/** Body of the anonymous key-distribution endpoint (api spec, Authentication Endpoints). */
export interface AuthKey {
  keyId: string
  publicKey: string
}

let cachedKey: AuthKey | null = null

/** Fetches (or returns the cached) SM2 public key for password encryption. */
async function fetchAuthKey(): Promise<AuthKey> {
  if (cachedKey) {
    return cachedKey
  }
  cachedKey = await apiRequest<AuthKey>('/system/auth-key')
  return cachedKey
}

/**
 * Encrypts a raw password into the wire format. Call sites: the login page submit
 * handler and the user-management API client (`src/api/users.ts`) - the typed value
 * must never appear in any request body as plaintext.
 */
export async function encryptPassword(raw: string): Promise<string> {
  const key = await fetchAuthKey()
  // cipherMode 1 = C1C3C2 (GM/T 0003-2012 ordering, matching Hutool's backend default);
  // "04" prefix = the standard uncompressed-point marker (coordination D4 refinement).
  return '04' + sm2.doEncrypt(raw, key.publicKey, 1)
}

/**
 * Drops the cached public key so the next {@link encryptPassword} refetches. Callers
 * invoke this on an `A0400` from a password endpoint (stale key after rotation).
 */
export function invalidateKeyCache(): void {
  cachedKey = null
}

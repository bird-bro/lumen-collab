import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Password-cipher util contract (password-transport-encryption-web tasks 1.2, design
 * T1; coordination password-transport-encryption D3/D4). The API client is stubbed at
 * the house seam (`vi.mock('@/api/client')`) - unit tests never touch the network.
 *
 * The fixed vector is the SAME literal the backend `PasswordCipherHelperTest` pins:
 * sm-crypto 0.3.13 C1C3C2 output for "Test@12345" under the fixed test public point
 * (markerless native form). Test material committed by design - it protects nothing.
 */
const apiRequest = vi.fn()
vi.mock('@/api/client', () => ({ apiRequest: (...args: unknown[]) => apiRequest(...args) }))

import { encryptPassword, invalidateKeyCache } from '@/utils/password-cipher'
import { sm2 } from 'sm-crypto'

const TEST_PRIVATE_D =
  '56d66802243a734aba690c6f2553ca69c87e8e78f9b282a19686d7fe1683cd83'
const TEST_PUBLIC_POINT =
  '0440025ec765361018ae5c5e603f9e55354e5aa2e1fcab3d541d7e6f560b40039f7d' +
  'a1972abc804eb41b0332bb04f348b3baa7009091d150c4a77763efdfd8d553'
const SM_CRYPTO_VECTOR =
  '9d84ff0341ff1c0dcd95fcfb8ca1ce8025a60a7290d3f3dd4ab67b6f61b880baa' +
  '4b0d6a1cb006873964ec2b196ff4518be41561a4001edfb9c84a6e6a117b601' +
  'c0a3de3b85516bf0385fd95582a3bc16939fab0249f2927bd7e44c4b3c667b8' +
  '2c2dc36c530eafa3120fb'
const VECTOR_PLAINTEXT = 'Test@12345'

function mockAuthKey(): void {
  apiRequest.mockImplementation((path: string) => {
    expect(path).toBe('/system/auth-key')
    return Promise.resolve({ keyId: 'sm2-test', publicKey: TEST_PUBLIC_POINT })
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  invalidateKeyCache()
})

describe('password-cipher interop contract', () => {
  it('the recorded sm-crypto vector decrypts under the fixed test scalar', () => {
    // The mirror of the backend interop pin: this literal + D is the wire contract.
    expect(sm2.doDecrypt(SM_CRYPTO_VECTOR, TEST_PRIVATE_D, 1)).toBe(VECTOR_PLAINTEXT)
  })

  it('encryptPassword emits 04-prefixed hex ciphertext that round-trips', async () => {
    mockAuthKey()
    const wire = await encryptPassword('admin123')
    expect(wire).toMatch(/^[0-9a-f]+$/)
    expect(wire.startsWith('04')).toBe(true)
    expect(wire).not.toBe('admin123')
    // sm-crypto's own doDecrypt expects the MARKERLESS form - strip the marker we
    // prepended for the wire (the backend's BC engine requires it).
    expect(sm2.doDecrypt(wire.slice(2), TEST_PRIVATE_D, 1)).toBe('admin123')
  })
})

describe('key fetch + module cache', () => {
  it('two encrypts fetch /auth-key exactly once', async () => {
    mockAuthKey()
    await encryptPassword('alpha-one')
    await encryptPassword('beta-two!')
    expect(apiRequest).toHaveBeenCalledTimes(1)
  })

  it('invalidateKeyCache forces a refetch on the next encrypt', async () => {
    mockAuthKey()
    await encryptPassword('alpha-one')
    invalidateKeyCache()
    await encryptPassword('beta-two!')
    expect(apiRequest).toHaveBeenCalledTimes(2)
  })
})

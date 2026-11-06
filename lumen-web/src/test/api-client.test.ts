import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, configureApiClient } from '@/api/client'

/**
 * Transport-client contract (batch 4 of web-foundation-auth-console, task 4.1;
 * design D4). Fetch is stubbed with plain stand-ins exposing only `json()` -
 * the client keys on the envelope code and NEVER on the HTTP status (the
 * "203" transport-auth signal arrives as HTTP 200 with the code in the body),
 * so a status field on the stand-in would be dead weight by construction.
 *
 * Envelope codes (openspec/specs/api/spec.md + openspec/specs/errors/spec.md):
 * "200" success-with-data, "204" success-empty, "203" session expired
 * (AuthenticationEntryPointImpl semantic), A/B-class business failures, and
 * the client's own B0001 for network/non-JSON transport failures.
 */

function okEnvelope(data: unknown): { json: () => Promise<unknown> } {
  return {
    json: async () => ({ timestamp: '2026-11-04 10:00:00.000', code: '200', msg: 'OK', data }),
  }
}

function envelopeOf(code: string, msg: string): { json: () => Promise<unknown> } {
  return {
    json: async () => ({ timestamp: '2026-11-04 10:00:00.000', code, msg, data: null }),
  }
}

function stubFetch(impl: (...args: unknown[]) => Promise<unknown>): {
  mock: { calls: unknown[][] }
} {
  const fetchMock = vi.fn(impl)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function lastCall(fetchMock: { mock: { calls: unknown[][] } }): [string, RequestInit] {
  const call = fetchMock.mock.calls[0]
  return [call[0] as string, call[1] as RequestInit]
}

afterEach(() => {
  vi.unstubAllGlobals()
  // Reset the module-level injection to the documented no-op defaults so a
  // token from an earlier test cannot leak into a later header assertion.
  configureApiClient({ getToken: () => null, onSessionExpired: () => undefined })
})

describe('apiRequest - transport client (web-foundation-auth-console 4.1)', () => {
  it('1. joins the resource path onto BASE_PATH (/system/me -> /api/v1/system/me)', async () => {
    const fetchMock = stubFetch(async () => okEnvelope(null))
    await apiRequest('/system/me')
    expect(fetchMock.mock.calls).toHaveLength(1)
    const [url] = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/me')
  })

  it('2a. sends "Authorization: Bearer <token>" when getToken returns a token', async () => {
    configureApiClient({ getToken: () => 't1' })
    const fetchMock = stubFetch(async () => okEnvelope(null))
    await apiRequest('/system/me')
    const [, init] = lastCall(fetchMock)
    expect(init.headers).toMatchObject({ Authorization: 'Bearer t1' })
  })

  it('2b. sends no Authorization header when there is no token', async () => {
    configureApiClient({ getToken: () => null })
    const fetchMock = stubFetch(async () => okEnvelope(null))
    await apiRequest('/system/me')
    const [, init] = lastCall(fetchMock)
    expect(init.headers ?? {}).not.toHaveProperty('Authorization')
  })

  it('3. serializes a present body as JSON with Content-Type: application/json', async () => {
    const fetchMock = stubFetch(async () => okEnvelope(7))
    await apiRequest('/system/users', { method: 'POST', body: { username: 'u1' } })
    const [, init] = lastCall(fetchMock)
    expect(init.method).toBe('POST')
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' })
    expect(init.body).toBe(JSON.stringify({ username: 'u1' }))
  })

  it('4a. resolves the envelope data for code "200"', async () => {
    stubFetch(async () => okEnvelope({ uid: 1, username: 'admin' }))
    const data = await apiRequest<{ uid: number; username: string }>('/system/me')
    expect(data).toEqual({ uid: 1, username: 'admin' })
  })

  it('4b. resolves null for code "204"', async () => {
    stubFetch(async () => envelopeOf('204', 'No Content'))
    const data = await apiRequest<null>('/system/orgs/1')
    expect(data).toBeNull()
  })

  it('5. throws ApiError{code,msg} for an A-class code while HTTP stays 200', async () => {
    stubFetch(async () => envelopeOf('A0210', '用户密码错误'))
    const promise = apiRequest('/system/login')
    await expect(promise).rejects.toBeInstanceOf(ApiError)
    await expect(promise).rejects.toMatchObject({ code: 'A0210', msg: '用户密码错误' })
  })

  it('6. fires onSessionExpired AND throws on code "203" even though HTTP is 200', async () => {
    const onSessionExpired = vi.fn()
    configureApiClient({ getToken: () => 't1', onSessionExpired })
    stubFetch(
      async () =>
        envelopeOf('203', '请求访问：/api/v1/system/me，认证失败，无法访问系统资源'),
    )
    await expect(apiRequest('/system/me')).rejects.toMatchObject({ code: '203' })
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('7a. wraps network-layer failures in ApiError B0001 carrying the original cause', async () => {
    stubFetch(async () => {
      throw new TypeError('Failed to fetch')
    })
    await expect(apiRequest('/system/me')).rejects.toMatchObject({
      code: 'B0001',
      msg: expect.stringContaining('Failed to fetch'),
    })
  })

  it('7b. wraps non-JSON response bodies in ApiError B0001', async () => {
    stubFetch(async () => ({
      json: async () => {
        throw new SyntaxError('Unexpected token < in JSON at position 0')
      },
    }))
    await expect(apiRequest('/system/me')).rejects.toMatchObject({
      code: 'B0001',
      msg: expect.stringContaining('Unexpected token'),
    })
  })

  it('7c. rejects a JSON body without an envelope code as a B0001 transport error', async () => {
    stubFetch(async () => ({ json: async () => ({ hello: 'world' }) }))
    await expect(apiRequest('/system/me')).rejects.toMatchObject({ code: 'B0001' })
  })

  it('8. appends encoded query parameters and skips null/undefined entries', async () => {
    const fetchMock = stubFetch(async () => okEnvelope(null))
    await apiRequest('/system/logs', {
      query: { page: 1, pageSize: 20, type: '新增', uid: undefined, dateYm: null },
    })
    const [url] = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/logs?page=1&pageSize=20&type=%E6%96%B0%E5%A2%9E')
  })

  it('9. defaults to GET and sends no body when none is given', async () => {
    const fetchMock = stubFetch(async () => okEnvelope(null))
    await apiRequest('/system/menus/tree')
    const [, init] = lastCall(fetchMock)
    expect(init.method).toBe('GET')
    expect(init.body).toBeUndefined()
  })

  it('10. coalesces concurrent identical GET calls without caching the completed request', async () => {
    let resolveFetch!: (value: unknown) => void
    const fetchMock = stubFetch(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve
        }),
    )

    const first = apiRequest('/system/me')
    const second = apiRequest('/system/me')
    expect(fetchMock.mock.calls).toHaveLength(1)

    resolveFetch(okEnvelope({ name: 'admin' }))
    await expect(Promise.all([first, second])).resolves.toEqual([
      { name: 'admin' },
      { name: 'admin' },
    ])

    const refresh = apiRequest('/system/me')
    resolveFetch(okEnvelope({ name: 'admin-2' }))
    await expect(refresh).resolves.toEqual({ name: 'admin-2' })
    expect(fetchMock.mock.calls).toHaveLength(2)
  })

  it('11. coalesces concurrent GET failures and retries only after settlement', async () => {
    let rejectFetch!: (reason: unknown) => void
    const fetchMock = stubFetch(
      () =>
        new Promise((_, reject) => {
          rejectFetch = reject
        }),
    )

    const first = apiRequest('/system/me')
    const second = apiRequest('/system/me')
    expect(fetchMock.mock.calls).toHaveLength(1)

    rejectFetch(new TypeError('Failed to fetch'))
    await expect(first).rejects.toMatchObject({ code: 'B0001' })
    await expect(second).rejects.toMatchObject({ code: 'B0001' })

    stubFetch(async () => okEnvelope({ name: 'admin' }))
    await expect(apiRequest('/system/me')).resolves.toEqual({ name: 'admin' })
  })

  it('12. does not coalesce GETs with different tokens or mutations', async () => {
    const fetchMock = stubFetch(async () => okEnvelope(null))
    configureApiClient({ getToken: () => 't1' })
    const first = apiRequest('/system/me')
    configureApiClient({ getToken: () => 't2' })
    const second = apiRequest('/system/me')
    await Promise.all([first, second])
    expect(fetchMock.mock.calls).toHaveLength(2)

    await apiRequest('/system/orgs', { method: 'POST', body: { name: 'a' } })
    await apiRequest('/system/orgs', { method: 'POST', body: { name: 'a' } })
    expect(fetchMock.mock.calls).toHaveLength(4)
  })
})

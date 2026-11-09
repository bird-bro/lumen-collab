import { afterEach, describe, expect, it, vi } from 'vitest'

/**
 * Dict items lookup (role-type-dict-web tasks 1.1/1.2): the raw client hits
 * `GET /api/v1/system/dicts/detail/items/{catalogName}` with the catalog name
 * RAW in the path (the recorded contract: pre-encoded %XX segments land on the
 * security entry point - the browser/fetch layer encodes automatically); the
 * utils resolver wraps it in a session cache (one wire call per catalog name,
 * failed lookups NOT cached so the next call retries) and matches codes to
 * labels with a raw-number fallback.
 */
const ITEMS = [
  { code: 10, value: '系统角色' },
  { code: 11, value: 'RCP角色' },
]

function stubOk(data: unknown, rejectFirst = false): ReturnType<typeof vi.fn> {
  let callCount = 0
  const fetchMock = vi.fn(async () => {
    callCount += 1
    if (rejectFirst && callCount === 1) {
      return {
        json: async () => ({ timestamp: 'x', code: 'A0301', msg: 'denied', data: null }),
      }
    }
    return {
      json: async () => ({ timestamp: 'x', code: '200', msg: 'OK', data }),
    }
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function freshResolver() {
  // Fresh module instance per test - the session cache is module state.
  return import('@/utils/dict-items')
}

describe('dict items client + session resolver (role-type-dict-web)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it('fetchDictItemsByName GETs the raw-name items path and unwraps {code,value}[]', async () => {
    const { fetchDictItemsByName } = await import('@/api/dicts')
    const fetchMock = stubOk(ITEMS)
    const items = await fetchDictItemsByName('角色类型')
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    // RAW name in the path - the recorded cross-stack contract (api verify notes).
    expect(call[0]).toBe('/api/v1/system/dicts/detail/items/角色类型')
    expect(call[1]?.method).toBe('GET')
    expect(items).toEqual(ITEMS)
  })

  it('dictItemsByName caches per catalog name: two calls, one wire request', async () => {
    const fetchMock = stubOk(ITEMS)
    const { dictItemsByName } = await freshResolver()
    const first = await dictItemsByName('角色类型')
    const second = await dictItemsByName('角色类型')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(second).toBe(first) // the cached promise - same array identity
  })

  it('caches per distinct name: different names fetch separately', async () => {
    const fetchMock = stubOk(ITEMS)
    const { dictItemsByName } = await freshResolver()
    await dictItemsByName('角色类型')
    await dictItemsByName('性别')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('a failed lookup is NOT cached - the next call retries the wire', async () => {
    const fetchMock = stubOk(ITEMS, true)
    const { dictItemsByName } = await freshResolver()
    await expect(dictItemsByName('角色类型')).rejects.toMatchObject({ code: 'A0301' })
    const retry = await dictItemsByName('角色类型')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(retry).toEqual(ITEMS)
  })

  it('resolveDictLabel matches by code and falls back to the raw number', async () => {
    const { resolveDictLabel } = await freshResolver()
    expect(resolveDictLabel(ITEMS, 10)).toBe('系统角色')
    expect(resolveDictLabel(ITEMS, 11)).toBe('RCP角色')
    expect(resolveDictLabel(ITEMS, 42)).toBe('42')
    expect(resolveDictLabel([], 42)).toBe('42')
    expect(resolveDictLabel(ITEMS, 0)).toBe('0')
  })
})

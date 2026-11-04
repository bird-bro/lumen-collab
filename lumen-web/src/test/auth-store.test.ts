import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Auth store contract (batch 4 of web-foundation-auth-console, task 4.2;
 * design D5): zustand + persist on localStorage key 'lumen-auth', hydration
 * at store creation, setSession/clear actions, and the wiring that closes the
 * 4.1 injection loop. The client never imports this store - the wiring tests
 * prove the edge is one-way by driving `apiRequest` against the
 * store-initialized session (token getter) and a "203" envelope (expiry
 * callback clearing the store).
 *
 * Every test re-imports the store module against a fresh registry so the
 * store is created AFTER the localStorage seed (hydration-at-creation) and
 * module-level state cannot leak between tests.
 */

const SEED_PROFILE = {
  uid: 1,
  username: 'admin',
  name: '系统管理员',
  enable: 1,
  mustChangePassword: 1,
  roles: ['系统管理员'],
}

const SEED_MENU = {
  mid: 1,
  title: '系统管理',
  upId: 0,
  icon: 'setting',
  path: '/system',
  query: null,
  component: null,
  type: 'M',
  sort: 1,
  visible: true,
  redirect: null,
  app: null,
  breadcrumb: null,
  action: null,
  isCache: null,
  isFrame: null,
  perms: null,
  remark: null,
  children: null,
}

/** Fetch stand-in with a widened signature so mock.calls entries are indexable. */
function stubFetch(impl: (...args: unknown[]) => Promise<unknown>): {
  mock: { calls: unknown[][] }
} {
  const fetchMock = vi.fn(impl)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

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

beforeEach(() => {
  vi.resetModules()
  vi.unstubAllGlobals()
  localStorage.clear()
})

async function loadStore() {
  const module = await import('@/stores/auth')
  return module.useAuthStore
}

async function loadClient() {
  const module = await import('@/api/client')
  return module
}

describe('auth store (web-foundation-auth-console 4.2)', () => {
  it('1. hydrates token/profile/menus/perms from localStorage at store creation', async () => {
    localStorage.setItem(
      'lumen-auth',
      JSON.stringify({
        state: {
          token: 't-seed',
          profile: SEED_PROFILE,
          menus: [SEED_MENU],
          perms: ['system:user:list'],
        },
        version: 0,
      }),
    )
    const useAuthStore = await loadStore()
    const state = useAuthStore.getState()
    expect(state.token).toBe('t-seed')
    expect(state.profile).toEqual(SEED_PROFILE)
    expect(state.menus).toEqual([SEED_MENU])
    expect(state.perms).toEqual(['system:user:list'])
  })

  it('2. starts with an empty session when nothing is stored', async () => {
    const useAuthStore = await loadStore()
    expect(useAuthStore.getState()).toMatchObject({
      token: null,
      profile: null,
      menus: [],
      perms: [],
    })
  })

  it('3. setSession writes the four fields and persists them to localStorage', async () => {
    const useAuthStore = await loadStore()
    useAuthStore.getState().setSession({
      token: 't-login',
      profile: SEED_PROFILE,
      menus: [SEED_MENU],
      perms: ['system:user:list'],
    })
    const state = useAuthStore.getState()
    expect(state.token).toBe('t-login')
    expect(state.profile).toEqual(SEED_PROFILE)
    expect(state.menus).toEqual([SEED_MENU])
    expect(state.perms).toEqual(['system:user:list'])
    const persisted = JSON.parse(localStorage.getItem('lumen-auth') ?? '{}') as {
      state: { token: string | null; menus: unknown[]; perms: string[] }
    }
    expect(persisted.state.token).toBe('t-login')
    expect(persisted.state.menus).toEqual([SEED_MENU])
    expect(persisted.state.perms).toEqual(['system:user:list'])
  })

  it('4. setSession accepts a partial write (e.g. profile/menus/perms refresh keeps the token)', async () => {
    const useAuthStore = await loadStore()
    useAuthStore.getState().setSession({ token: 't-keep', profile: SEED_PROFILE, menus: [], perms: [] })
    useAuthStore.getState().setSession({ profile: { ...SEED_PROFILE, name: '改名' }, menus: [SEED_MENU], perms: ['p'] })
    const state = useAuthStore.getState()
    expect(state.token).toBe('t-keep')
    expect(state.profile?.name).toBe('改名')
    expect(state.menus).toEqual([SEED_MENU])
    expect(state.perms).toEqual(['p'])
  })

  it('5. clear empties the four fields and persists the cleared session', async () => {
    const useAuthStore = await loadStore()
    useAuthStore.getState().setSession({
      token: 't-login',
      profile: SEED_PROFILE,
      menus: [SEED_MENU],
      perms: ['system:user:list'],
    })
    useAuthStore.getState().clear()
    expect(useAuthStore.getState()).toMatchObject({
      token: null,
      profile: null,
      menus: [],
      perms: [],
    })
    const persisted = JSON.parse(localStorage.getItem('lumen-auth') ?? '{}') as {
      state: { token: string | null }
    }
    expect(persisted.state.token).toBeNull()
  })

  it('6. wires the client token getter to the store token', async () => {
    const useAuthStore = await loadStore()
    const { apiRequest } = await loadClient()
    useAuthStore.getState().setSession({ token: 't-wired', profile: SEED_PROFILE, menus: [], perms: [] })
    const fetchMock = stubFetch(async () => okEnvelope(null))
    await apiRequest('/system/me')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).toMatchObject({ Authorization: 'Bearer t-wired' })
  })

  it('7. a "203" envelope clears the store through the wired session-expiry callback', async () => {
    const useAuthStore = await loadStore()
    const { apiRequest } = await loadClient()
    useAuthStore.getState().setSession({
      token: 't-stale',
      profile: SEED_PROFILE,
      menus: [SEED_MENU],
      perms: ['system:user:list'],
    })
    stubFetch(async () => envelopeOf('203', '请求访问：/api/v1/system/users/list，认证失败，无法访问系统资源'))
    await expect(apiRequest('/system/users/list')).rejects.toMatchObject({ code: '203' })
    expect(useAuthStore.getState()).toMatchObject({
      token: null,
      profile: null,
      menus: [],
      perms: [],
    })
    const persisted = JSON.parse(localStorage.getItem('lumen-auth') ?? '{}') as {
      state: { token: string | null }
    }
    expect(persisted.state.token).toBeNull()
  })
})

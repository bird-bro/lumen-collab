import { render, screen } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { useRoutes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { appRoutes } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'
import type { MenuNode } from '@/api/types'

/**
 * Route guard + router restructure (batch 5b of web-foundation-auth-console,
 * task 5.3; spec `specs/web-auth-flow` § Session Guard, design D6):
 *
 * - an unauthenticated visitor lands on /login and ZERO api requests are
 *   issued (the guard reads the store, never the network);
 * - a stored-but-stale token survives the mount, the shell's boot `/me`
 *   refresh receives the "203" envelope, the client's session-expiry callback
 *   clears the store and the guard redirects to /login;
 * - `/` redirects to the first route the menu tree grants (admin fixture:
 *   /system/orgs);
 * - /system/** placeholder routes render an explicit not-implemented state
 *   and issue no request.
 *
 * The route table is exercised through `useRoutes(appRoutes)` inside a
 * MemoryRouter: the table carries no data-router-only features (no loaders),
 * so the MemoryRouter reproduces the same matching while keeping each test's
 * history isolated.
 */

const ADMIN_MENUS: MenuNode[] = [
  {
    mid: 1, title: '系统管理', upId: 0, icon: 'setting', path: '/system', query: null,
    component: null, type: 'M', sort: 1, visible: true, redirect: null, app: null,
    breadcrumb: null, action: null, isCache: null, isFrame: null, perms: null, remark: null,
    children: [
      {
        mid: 13, title: '组织架构', upId: 1, icon: 'bank', path: null, query: null,
        component: null, type: 'M', sort: 1, visible: true, redirect: null, app: null,
        breadcrumb: null, action: null, isCache: null, isFrame: null, perms: null, remark: null,
        children: [
          {
            mid: 7, title: '机构管理', upId: 13, icon: null, path: 'org', query: null,
            component: 'system/org/index', type: 'C', sort: 1, visible: true, redirect: null,
            app: null, breadcrumb: null, action: null, isCache: null, isFrame: null,
            perms: 'system:org:list', remark: null, children: null,
          },
        ],
      },
    ],
  },
]

function okEnvelope(data: unknown) {
  return {
    json: async () => ({ timestamp: '2026-11-04 10:00:00.000', code: '200', msg: 'OK', data }),
  }
}

function unauthorizedEnvelope(uri: string) {
  return {
    json: async () => ({
      timestamp: '2026-11-04 10:00:00.000',
      code: '203',
      msg: `请求访问：${uri}，认证失败，无法访问系统资源`,
      data: null,
    }),
  }
}

function stubFetch(impl: (...args: unknown[]) => Promise<unknown>) {
  const fetchMock = vi.fn(impl)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderRoutes(initialPath: string) {
  function Routed() {
    return useRoutes(appRoutes)
  }
  return render(
    <ConfigProvider theme={themeConfig}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routed />
      </MemoryRouter>
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
  useAuthStore.getState().clear()
})

describe('RequireAuth guard (web-foundation-auth-console 5.3)', () => {
  it('redirects an unauthenticated visitor to /login and issues zero api requests', () => {
    const fetchMock = stubFetch(async () => okEnvelope(null))
    renderRoutes('/system/users')

    expect(screen.getByPlaceholderText('账号')).toBeInTheDocument()
    expect(screen.queryByText(/not yet implemented/i)).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('clears the session and lands on /login when the boot /me refresh returns 203', async () => {
    useAuthStore.getState().setSession({ token: 't-stale', profile: null, menus: [], perms: [] })
    const fetchMock = stubFetch(async (input) =>
      unauthorizedEnvelope(String(input).replace(/^.*\/api\/v1/, '/api/v1').split('?')[0]),
    )
    renderRoutes('/system/users')

    expect(await screen.findByPlaceholderText('账号')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toBe('/api/v1/system/me')
  })

  it('renders /login outside the shell (no header chrome on the login route)', () => {
    stubFetch(async () => okEnvelope(null))
    renderRoutes('/login')

    expect(screen.getByPlaceholderText('账号')).toBeInTheDocument()
    expect(document.querySelector('.ant-layout-header')).toBeNull()
    expect(document.querySelector('.ant-layout-sider')).toBeNull()
  })
})

describe('index redirect (web-foundation-auth-console 5.3)', () => {
  // Batch 6.1 replaced the /system/orgs placeholder with the real org page, so
  // the landing evidence is now the org tree request + the tree panel chrome
  // (the assertion's INTENT is unchanged: / lands on the first granted route).
  it('redirects / to the first route the menu tree grants (admin: /system/orgs)', async () => {
    useAuthStore.getState().setSession({ token: 't-login', profile: null, menus: ADMIN_MENUS, perms: [] })
    const fetchMock = stubFetch(async (input) => {
      const url = String(input)
      if (url.startsWith('/api/v1/system/orgs')) return okEnvelope([])
      return okEnvelope({ profile: null, menus: ADMIN_MENUS, perms: [] })
    })
    renderRoutes('/')

    expect(await screen.findByText('机构')).toBeInTheDocument()
    const urls = fetchMock.mock.calls.map(([input]) => String(input))
    expect(urls).toContain('/api/v1/system/me')
    expect(urls).toContain('/api/v1/system/orgs/tree')
    expect(screen.queryByText(/no granted menu/i)).not.toBeInTheDocument()
  })

  it('shows an explicit no-menu state instead of redirecting when nothing is granted', async () => {
    useAuthStore.getState().setSession({ token: 't-login', profile: null, menus: [], perms: [] })
    stubFetch(async () => okEnvelope({ profile: null, menus: [], perms: [] }))
    renderRoutes('/')

    expect(await screen.findByText(/no granted menu/i)).toBeInTheDocument()
  })
})

describe('/system/** placeholder routes (web-foundation-auth-console 5.3)', () => {
  it('renders the explicit not-implemented state with no api request', async () => {
    useAuthStore.getState().setSession({ token: 't-login', profile: null, menus: ADMIN_MENUS, perms: [] })
    const fetchMock = stubFetch(async () => okEnvelope({ profile: null, menus: ADMIN_MENUS, perms: [] }))
    renderRoutes('/system/dicts')

    expect(await screen.findByText(/not yet implemented/i)).toBeInTheDocument()
    // Only the boot /me refresh ran - the placeholder itself never fetches.
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/system/me')
  })
})

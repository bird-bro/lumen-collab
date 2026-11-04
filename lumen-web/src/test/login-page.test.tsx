import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '@/pages/login'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'
import type { MenuNode } from '@/api/types'

/**
 * Login page (batch 5b of web-foundation-auth-console, task 5.2; spec
 * `specs/web-auth-flow` § Login Page): valid credentials store the session
 * and route into the shell (to the first granted menu route), a wrong
 * password surfaces the A-class `msg` via message.error and keeps the visitor
 * on /login with no session, and the Enter key submits exactly like the
 * button. The transport seam is stubbed at `fetch` (the dev mock's envelope
 * shapes); the router is a MemoryRouter with a marker route standing in for
 * the first granted console page (/system/orgs for the admin fixture tree).
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

const ADMIN_PROFILE = {
  uid: 1, username: 'admin', name: '系统管理员', enable: 1, mustChangePassword: 1,
  roles: ['系统管理员'],
}

function okEnvelope(data: unknown) {
  return {
    json: async () => ({ timestamp: '2026-11-04 10:00:00.000', code: '200', msg: 'OK', data }),
  }
}

function failEnvelope(code: string, msg: string) {
  return {
    json: async () => ({ timestamp: '2026-11-04 10:00:00.000', code, msg, data: null }),
  }
}

function stubFetch(impl: (...args: unknown[]) => Promise<unknown>) {
  const fetchMock = vi.fn(impl)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderLogin() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/system/orgs" element={<div>CONSOLE_ORGS_MARKER</div>} />
        </Routes>
      </MemoryRouter>
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
  useAuthStore.getState().clear()
})

describe('LoginPage (web-foundation-auth-console 5.2)', () => {
  it('stores the session and routes to the first granted menu route on valid credentials', async () => {
    const user = userEvent.setup()
    const fetchMock = stubFetch(async () =>
      okEnvelope({ token: 't-login', profile: ADMIN_PROFILE, menus: ADMIN_MENUS, perms: ['system:org:list'] }),
    )
    renderLogin()

    await user.type(screen.getByPlaceholderText('账号'), 'admin')
    await user.type(screen.getByPlaceholderText('密码'), 'admin123')
    await user.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByText('CONSOLE_ORGS_MARKER')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBe('t-login')
    expect(useAuthStore.getState().profile?.name).toBe('系统管理员')
    expect(useAuthStore.getState().menus).toHaveLength(1)
    expect(useAuthStore.getState().perms).toContain('system:org:list')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/system/login')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ username: 'admin', password: 'admin123' })
  })

  it('surfaces the A-class msg, keeps the visitor on /login and stores no session on a wrong password', async () => {
    const user = userEvent.setup()
    stubFetch(async () => failEnvelope('A0210', '用户密码错误'))
    renderLogin()

    await user.type(screen.getByPlaceholderText('账号'), 'admin')
    await user.type(screen.getByPlaceholderText('密码'), 'wrong-pass')
    await user.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByText('用户密码错误')).toBeInTheDocument()
    expect(screen.queryByText('CONSOLE_ORGS_MARKER')).not.toBeInTheDocument()
    expect(screen.getByPlaceholderText('密码')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBeNull()
    const persisted = JSON.parse(localStorage.getItem('lumen-auth') ?? '{}') as { state?: { token?: string | null } }
    expect(persisted.state?.token ?? null).toBeNull()
  })

  it('submits on Enter in the password field exactly like the button', async () => {
    const user = userEvent.setup()
    const fetchMock = stubFetch(async () =>
      okEnvelope({ token: 't-enter', profile: ADMIN_PROFILE, menus: ADMIN_MENUS, perms: [] }),
    )
    renderLogin()

    await user.type(screen.getByPlaceholderText('账号'), 'admin')
    await user.type(screen.getByPlaceholderText('密码'), 'admin123{enter}')

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    expect(await screen.findByText('CONSOLE_ORGS_MARKER')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBe('t-enter')
  })

  it('shows a loading state on the submit button while the request is in flight', async () => {
    const user = userEvent.setup()
    let resolveLogin: (value: unknown) => void = () => undefined
    stubFetch(
      () =>
        new Promise((resolve) => {
          resolveLogin = resolve
        }),
    )
    renderLogin()

    await user.type(screen.getByPlaceholderText('账号'), 'admin')
    await user.type(screen.getByPlaceholderText('密码'), 'admin123')
    await user.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByRole('button', { name: /登录中/ })).toBeInTheDocument()
    resolveLogin(okEnvelope({ token: 't-slow', profile: ADMIN_PROFILE, menus: ADMIN_MENUS, perms: [] }))
    expect(await screen.findByText('CONSOLE_ORGS_MARKER')).toBeInTheDocument()
  })
})

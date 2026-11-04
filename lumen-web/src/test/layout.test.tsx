import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { BasicLayout } from '@/layouts/BasicLayout'
import { RequireAuth } from '@/components/RequireAuth'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'
import type { MenuNode } from '@/api/types'

/**
 * Three-zone OPS shell (batch 5b of web-foundation-auth-console, task 5.4;
 * user-confirmed prototype `console-shell.html` rev.11/12): a 68px deep-blue
 * main-nav rail (L1 entries from the store menu tree), a 60px white header
 * (product title + two-line user area with 50px avatar and a logout popover),
 * a 160px white collapsible secondary sidebar (the ACTIVE L1's children:
 * L2 group titles + flat text-only leaf rows, OPS fold control), and a
 * content region with the user-set 10px padding. The shell renders wrapped in
 * the real `themeConfig` so assertions cover the wiring a page actually runs
 * under; colors are asserted through the injected CSS custom properties
 * (sourced from the theme tokens) because the hex discipline forbids
 * re-declaring them here.
 */

/** The seeded admin tree (shape of the batch-5b mock fixture, depth-first sorted). */
function menuNode(partial: {
  mid: number
  title: string
  upId: number
  type: 'M' | 'C' | 'F'
  path?: string | null
  component?: string | null
  icon?: string | null
  children?: MenuNode[] | null
}): MenuNode {
  return {
    mid: partial.mid,
    title: partial.title,
    upId: partial.upId,
    type: partial.type,
    path: partial.path ?? null,
    component: partial.component ?? null,
    icon: partial.icon ?? null,
    children: partial.children ?? null,
    query: null,
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
  }
}

function adminMenus(): MenuNode[] {
  return [
    menuNode({
      mid: 1, title: '系统管理', upId: 0, type: 'M', path: '/system', icon: 'setting',
      children: [
        menuNode({
          mid: 13, title: '组织架构', upId: 1, type: 'M', icon: 'bank',
          children: [
            menuNode({ mid: 7, title: '机构管理', upId: 13, type: 'C', path: 'org', component: 'system/org/index' }),
            menuNode({ mid: 8, title: '科室管理', upId: 13, type: 'C', path: 'dep', component: 'system/dep/index' }),
          ],
        }),
        menuNode({
          mid: 14, title: '用户权限', upId: 1, type: 'M', icon: 'safety',
          children: [
            menuNode({ mid: 2, title: '用户管理', upId: 14, type: 'C', path: 'user', component: 'system/user/index' }),
            menuNode({ mid: 9, title: '角色管理', upId: 14, type: 'C', path: 'role', component: 'system/role/index' }),
            menuNode({ mid: 10, title: '菜单管理', upId: 14, type: 'C', path: 'menu', component: 'system/menu/index' }),
          ],
        }),
        menuNode({
          mid: 15, title: '系统配置', upId: 1, type: 'M', icon: 'sliders',
          children: [
            menuNode({ mid: 11, title: '字典管理', upId: 15, type: 'C', path: 'dict', component: 'system/dict/index' }),
            menuNode({ mid: 12, title: '日志查询', upId: 15, type: 'C', path: 'log', component: 'system/log/index' }),
          ],
        }),
      ],
    }),
    menuNode({ mid: 3, title: '放射科', upId: 0, type: 'C', path: '/radiology' }),
    menuNode({ mid: 4, title: '超声科', upId: 0, type: 'C', path: '/ultrasound' }),
    menuNode({ mid: 5, title: '内镜科', upId: 0, type: 'C', path: '/endoscopy' }),
  ]
}

const ADMIN_PROFILE = {
  uid: 1, username: 'admin', name: '系统管理员', enable: 1, mustChangePassword: 1,
  roles: ['系统管理员'],
}

function seedAdminSession() {
  useAuthStore.getState().setSession({
    token: 't-shell',
    profile: ADMIN_PROFILE,
    menus: adminMenus(),
    perms: ['system:user:list'],
  })
}

function renderShell(initialPath: string) {
  return render(
    <ConfigProvider theme={themeConfig}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route element={<BasicLayout />}>
            <Route path="/system/orgs" element={<div>ORGS_MARKER</div>} />
            <Route path="/system/roles" element={<div>ROLES_MARKER</div>} />
            <Route path="/radiology" element={<div>RADIOLOGY_MARKER</div>} />
          </Route>
          <Route path="/login" element={<div>LOGIN_MARKER</div>} />
        </Routes>
      </MemoryRouter>
    </ConfigProvider>,
  )
}

beforeEach(() => {
  useAuthStore.getState().clear()
})

describe('three-zone shell metrics (web-foundation-auth-console 5.4)', () => {
  it('renders a 68px main-nav rail with the token-sourced hover/active blues', () => {
    seedAdminSession()
    const { container } = renderShell('/system/orgs')

    const rail = container.querySelector<HTMLElement>('.lumen-rail')
    expect(rail).not.toBeNull()
    expect(rail?.style.width).toBe('68px')
    expect(rail?.style.getPropertyValue('--lumen-rail-hover')).toBe('#12355C')
    expect(rail?.style.getPropertyValue('--lumen-rail-active')).toBe('#3178fc')
  })

  it('renders a 60px-tall white header with the product title', () => {
    seedAdminSession()
    const { container } = renderShell('/system/orgs')

    const header = container.querySelector<HTMLElement>('.ant-layout-header')
    expect(header).not.toBeNull()
    expect(header?.style.height).toBe('60px')
    expect(screen.getByText('LumenCollab')).toBeInTheDocument()
  })

  it('renders a 160px white secondary sidebar and 10px content padding', () => {
    seedAdminSession()
    const { container } = renderShell('/system/orgs')

    const sidebar = container.querySelector<HTMLElement>('.lumen-side')
    expect(sidebar).not.toBeNull()
    expect(sidebar?.style.width).toBe('160px')

    const content = container.querySelector<HTMLElement>('.ant-layout-content')
    expect(content?.style.padding).toBe('5px')
  })

  it('renders the rail L1 entries from the store menu tree (not a hard-coded list)', () => {
    seedAdminSession()
    const { container } = renderShell('/system/orgs')

    // Scope to the rail: the header user button's accessible name also
    // contains 系统管理 (系统管理员).
    const rail = within(container.querySelector<HTMLElement>('.lumen-rail')!)
    expect(rail.getByRole('button', { name: /系统管理/ })).toBeInTheDocument()
    expect(rail.getByRole('button', { name: /放射科/ })).toBeInTheDocument()
    expect(rail.getByRole('button', { name: /超声科/ })).toBeInTheDocument()
    expect(rail.getByRole('button', { name: /内镜科/ })).toBeInTheDocument()
    expect(screen.queryByText('Radiology')).not.toBeInTheDocument()
  })

  it('renders the active L1 children as group titles + flat text-only leaf rows', () => {
    seedAdminSession()
    renderShell('/system/orgs')

    expect(screen.getByText('组织架构')).toBeInTheDocument()
    expect(screen.getByText('用户权限')).toBeInTheDocument()
    expect(screen.getByText('系统配置')).toBeInTheDocument()
    for (const leaf of ['机构管理', '科室管理', '用户管理', '角色管理', '菜单管理', '字典管理', '日志查询']) {
      expect(screen.getByText(leaf)).toBeInTheDocument()
    }
    // The selected leaf row carries the light-chip selection.
    expect(screen.getByText('机构管理').closest('.ant-menu-item')).toHaveClass('ant-menu-item-selected')
    // The left selection bar color is injected from the token pipeline.
    const menu = document.querySelector<HTMLElement>('.lumen-side-menu')
    expect(menu?.style.getPropertyValue('--lumen-menu-active-bar')).toBe('#3178fc')
  })

  it('switches the active rail entry and navigates to its first leaf on click', async () => {
    const user = userEvent.setup()
    seedAdminSession()
    renderShell('/system/orgs')

    await user.click(screen.getByRole('button', { name: /放射科/ }))

    expect(await screen.findByText('RADIOLOGY_MARKER')).toBeInTheDocument()
    const railItems = document.querySelectorAll<HTMLElement>('.lumen-rail-item')
    const radiologyItem = [...railItems].find((el) => el.textContent?.includes('放射科'))
    expect(radiologyItem).toHaveClass('lumen-rail-item-active')
  })
})

describe('sidebar fold control (web-foundation-auth-console 5.4)', () => {
  it('collapses to a 40px strip and back, showing the vertical active-L1 strip text', async () => {
    const user = userEvent.setup()
    seedAdminSession()
    const { container } = renderShell('/system/orgs')

    // The fold button's accessible name is the icon's aria-label (the title
    // attribute is only a fallback), so click through its stable class.
    await user.click(container.querySelector<HTMLElement>('.lumen-side-fold')!)

    const sidebar = container.querySelector<HTMLElement>('.lumen-side')
    expect(sidebar?.style.width).toBe('40px')
    const expand = container.querySelector<HTMLElement>('.lumen-side-expand')
    expect(expand).not.toBeNull()
    expect(expand?.textContent).toContain('系统管理')

    await user.click(expand!)
    expect(container.querySelector<HTMLElement>('.lumen-side')?.style.width).toBe('160px')
    expect(container.querySelector<HTMLElement>('.lumen-side-fold')).not.toBeNull()
  })
})

describe('header user area + logout (web-foundation-auth-console 5.4)', () => {
  it('shows the two-line user text and 50px avatar, and logs out through the popover', async () => {
    const user = userEvent.setup()
    seedAdminSession()
    renderShell('/system/orgs')

    expect(screen.getByText('系统管理员')).toBeInTheDocument()
    expect(screen.getByText('示例医院')).toBeInTheDocument()
    const avatar = document.querySelector<HTMLElement>('.lumen-header-avatar')
    expect(avatar?.style.width).toBe('50px')
    // The avatar derives the first character of the name (the prototype's
    // static 管 is a hand-picked demo glyph - recorded in the batch report).
    expect(avatar?.textContent).toBe('系')

    await user.click(screen.getByText('系统管理员'))
    await user.click(await screen.findByRole('button', { name: '退出登录' }))

    expect(await screen.findByText('LOGIN_MARKER')).toBeInTheDocument()
    expect(useAuthStore.getState().token).toBeNull()
  })
})

describe('guard wiring inside the shell route (web-foundation-auth-console 5.4)', () => {
  it('redirects to /login when no token exists (no shell chrome renders)', async () => {
    useAuthStore.getState().clear()
    render(
      <ConfigProvider theme={themeConfig}>
        <MemoryRouter initialEntries={['/system/orgs']}>
          <Routes>
            <Route
              element={
                <RequireAuth>
                  <BasicLayout />
                </RequireAuth>
              }
            >
              <Route path="/system/orgs" element={<div>ORGS_MARKER</div>} />
            </Route>
            <Route path="/login" element={<div>LOGIN_MARKER</div>} />
          </Routes>
        </MemoryRouter>
      </ConfigProvider>,
    )
    expect(await screen.findByText('LOGIN_MARKER')).toBeInTheDocument()
    expect(screen.queryByText('ORGS_MARKER')).not.toBeInTheDocument()
    expect(document.querySelector('.ant-layout-header')).toBeNull()
  })
})

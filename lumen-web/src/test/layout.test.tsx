import { render, screen, waitFor, within } from '@testing-library/react'
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
  type: 'M' | 'C' | 'F' | 'W'
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
          mid: 14, title: '用户权限', upId: 1, type: 'M', icon: 'team',
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

/** The admin tree PLUS a radiology L1 (V21 marker) - the multi-station session shape. */
function multiStationMenus(): MenuNode[] {
  return [
    ...adminMenus(),
    menuNode({
      mid: 30, title: '放射工作站', upId: 0, type: 'W', path: '/radiology',
      children: [menuNode({ mid: 31, title: '登记预约', upId: 30, type: 'C', path: '/radiology/register' })],
    }),
    menuNode({ mid: 40, title: '超声工作站', upId: 0, type: 'W', path: '/us' }),
  ]
}

function seedMultiStationSession() {
  useAuthStore.getState().setSession({
    token: 't-shell',
    profile: ADMIN_PROFILE,
    menus: multiStationMenus(),
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
            <Route path="/radiology/register" element={<div>REGISTER_MARKER</div>} />
            <Route path="/radiology/register" element={<div>REGISTER_MARKER</div>} />
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
    // The console page background is the gray layout tint (the theme pins
    // colorBgLayout to the OPS page tint), so the query rows and white table
    // cards float on non-white (2026-11-09).
    expect(content?.style.background).toBe('rgb(243, 245, 247)')
  })

  it('renders the rail L1 entries from the store menu tree (not a hard-coded list)', () => {
    seedAdminSession()
    const { container } = renderShell('/system/orgs')

    // Scope to the rail: the header user button's accessible name also
    // contains 系统管理 (系统管理员).
    const rail = within(container.querySelector<HTMLElement>('.lumen-rail')!)
    expect(rail.getByRole('button', { name: /系统管理/ })).toBeInTheDocument()
    // V10 parked the department placeholder L1s; the admin fixture holds console
    // menus only (department stations are separate V21 roots with their own markers)
    expect(rail.queryByRole('button', { name: /放射科/ })).not.toBeInTheDocument()
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

  it('registers the console-table zebra + row-line vars from the token pipeline (OPS parity 2026-11-09)', () => {
    seedAdminSession()
    renderShell('/system/orgs')

    const layout = document.querySelector<HTMLElement>('.ant-layout')
    // Row ruling = the primary itself (CSS color-mix applies the 15% alpha,
    // mirroring OPS --table-border rgb(22 125 255 / 15%)); the stripe follows
    // colorFillTertiary - AntD 5.29's alpha fill (4% black over white reads
    // like the OPS light stripe, and composites/adapts instead of pinning).
    expect(layout?.style.getPropertyValue('--lumen-row-line')).toBe('#3178fc')
    expect(layout?.style.getPropertyValue('--lumen-row-striped')).toBe('rgba(0,0,0,0.04)')
  })

  it('switches the active rail entry and navigates to its first leaf on click (multi-station session)', async () => {
    const user = userEvent.setup()
    // V21: department L1s are separate station roots - the radiology entry exists in
    // the rail only for a multi-station session; land on a radiology-primary session
    // so the rail carries both L1s from the start (append semantics)
    seedMultiStationSession()
    renderShell('/radiology')

    // the rail button's name is split (svg + label span) - locate by textContent,
    // scoped INSIDE the rail so the chip's dropdown items cannot match
    await waitFor(() => {
      const rail = document.querySelector('.lumen-rail')!
      expect(rail.textContent).toContain('登记预约')
    })
    const rail = document.querySelector('.lumen-rail')!
    const registerBtn = [...rail.querySelectorAll('button')].find((el) => el.textContent?.includes('登记预约'))
    expect(registerBtn).toBeDefined()
    await user.click(registerBtn!)

    // the M-directory L1 navigates to its FIRST LEAF (/radiology/register)
    expect(await screen.findByText('REGISTER_MARKER')).toBeInTheDocument()
    const railItems = document.querySelectorAll<HTMLElement>('.lumen-rail-item')
    const registerItem = [...railItems].find((el) => el.textContent?.includes('登记预约'))
    expect(registerItem).toHaveClass('lumen-rail-item-active')
  })
})

describe('sidebar fold control (web-foundation-auth-console 5.4)', () => {
  it('collapses to a mini icon rail and back, without the vertical strip text', async () => {
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
    // Mini-rail cleanup (user request 2026-11-05): no vertical active-L1 text.
    expect(expand?.textContent).not.toContain('系统管理')
    expect(container.querySelectorAll('.lumen-side-mini-btn').length).toBeGreaterThan(0)

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
    expect(avatar?.textContent).toBe('理员')

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

describe('workstation chip + append sidebar (workstation-context-web 3.1/3.2)', () => {
  it('renders the chip in zone 2 right of the product title when stations are granted', () => {
    seedMultiStationSession()
    const { container } = renderShell('/system/orgs')
    const chip = container.querySelector('[aria-label="工作站切换"]')
    expect(chip).not.toBeNull()
    const header = chip!.closest('.lumen-header') ?? chip!.parentElement
    const title = header!.querySelector('.lumen-header-title')
    // chip sits RIGHT of the product title (user instruction 2026-11-06)
    expect(
      title!.compareDocumentPosition(chip!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })

  it('renders no chip when the workstations payload is absent', () => {
    seedAdminSession()
    const { container } = renderShell('/system/orgs')
    expect(container.querySelector('[aria-label="工作站切换"]')).toBeNull()
  })

  it('lists the granted stations with the active one checked in the dropdown', async () => {
    const user = userEvent.setup()
    seedMultiStationSession()
    renderShell('/radiology')
    await user.click(screen.getByRole('button', { name: /工作站切换/ }))
    await waitFor(() => {
      expect(document.querySelector('.ant-dropdown:not(.ant-dropdown-hidden)')).not.toBeNull()
    })
    const dropdown = document.querySelector<HTMLElement>('.ant-dropdown:not(.ant-dropdown-hidden)')!
    expect(within(dropdown).getByText('放射工作站')).toBeInTheDocument()
    expect(within(dropdown).queryByText('内镜工作站')).not.toBeInTheDocument()
    // only GRANTED W nodes render (内镜工作站 not granted in this session)
    expect(within(dropdown).queryByText('内镜工作站')).not.toBeInTheDocument()
  })

  it('APPEND semantics: the first W is default-active and its children promote beside 系统管理', async () => {
    // seed('admin') derives the active station = the FIRST W node (放射工作站) per
    // the user's display rule - no click needed; the promoted children render beside
    // the resident 系统管理
    seedMultiStationSession()
    const { container } = renderShell('/system/orgs')

    const rail = container.querySelector<HTMLElement>('.lumen-rail')!
    expect(within(rail).getAllByText('系统管理').length).toBeGreaterThan(0)
    expect(within(rail).getAllByText('登记预约').length).toBeGreaterThan(0)
    // the W node itself stays out of the bar (chip-only)
    expect(within(rail).queryByText('放射工作站')).not.toBeInTheDocument()
  })

  it('switching stations via the chip swaps the promoted set', async () => {
    const user = userEvent.setup()
    seedMultiStationSession()
    const { container } = renderShell('/system/orgs')
    // the default-active station is 放射工作站 (first W); pick 超声工作站 -> its
    // (empty) children replace the promoted set
    await user.click(screen.getByRole('button', { name: /工作站切换/ }))
    await user.click(await screen.findByRole('menuitem', { name: /超声工作站/ }))
    await waitFor(() => {
      expect(useAuthStore.getState().activeWorkstation).toBe(40)
    })
    const rail = container.querySelector<HTMLElement>('.lumen-rail')!
    expect(within(rail).getAllByText('系统管理').length).toBeGreaterThan(0)
    expect(within(rail).queryByText('登记预约')).not.toBeInTheDocument() // promoted set swapped
  })

  it('re-login lands on the primary workstation (radiology session starts appended)', () => {
    seedMultiStationSession()
    const { container } = renderShell('/radiology')
    // the RAIL carries 系统管理 (resident) + 放射科 (the primary station) on login
    const rail = container.querySelector<HTMLElement>('.lumen-rail')!
    expect(within(rail).getAllByText('系统管理').length).toBeGreaterThan(0)
    expect(within(rail).getAllByText('登记预约').length).toBeGreaterThan(0)
  })
})

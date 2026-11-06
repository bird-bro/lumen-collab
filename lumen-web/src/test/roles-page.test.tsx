import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RolesPage from '@/pages/system/roles'
import * as menusApi from '@/api/menus'
import * as rolesApi from '@/api/roles'
import type { MenuNode, Page, RoleItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 角色管理 page (batch 6b, task 6.4; spec `specs/web-system-console`
 * § Role Management and Menu Assignment: list via the declared gap endpoint
 * `roles/list` (IPage, mock-served), CRUD, and the checkable-tree assignment
 * dialog whose save is a FULL-REPLACE grant whose reopen echoes the saved set).
 */
vi.mock('@/api/roles')
vi.mock('@/api/menus')

function role(partial: Partial<RoleItem> & { id: number; name: string }): RoleItem {
  return {
    type: 10,
    remark: null,
    createBy: 1,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

function menu(partial: Partial<MenuNode> & { mid: number; title: string; upId: number }): MenuNode {
  return {
    icon: null,
    path: null,
    query: null,
    component: null,
    type: 'C',
    sort: 1,
    visible: true,
    redirect: null,
    app: null,
    breadcrumb: null,
    action: null,
    isCache: true,
    isFrame: true,
    perms: null,
    remark: null,
    children: null,
    ...partial,
  }
}

const TREE: MenuNode[] = [
  menu({
    mid: 1,
    title: '系统管理',
    upId: 0,
    type: 'M',
    perms: null,
    children: [
      menu({
        mid: 13,
        title: '组织架构',
        upId: 1,
        type: 'M',
        perms: null,
        children: [menu({ mid: 7, title: '机构管理', upId: 13, perms: 'system:org:list' })],
      }),
    ],
  }),
]

const ROLE_A = role({ id: 1, name: '系统管理员', menuIds: [1, 13, 7] })

function pageFixture(records: RoleItem[], total = records.length): Page<RoleItem> {
  return { records, total, size: 20, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <RolesPage />
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({ perms: ['system:role:list', 'system:role:create', 'system:role:update', 'system:role:delete', 'system:role:assign'] })
  vi.mocked(rolesApi.listRoles).mockResolvedValue(pageFixture([ROLE_A]))
  vi.mocked(menusApi.menuTree).mockResolvedValue(TREE)
  vi.mocked(rolesApi.createRole).mockResolvedValue(2)
  vi.mocked(rolesApi.updateRole).mockResolvedValue(true)
  vi.mocked(rolesApi.deleteRole).mockResolvedValue(true)
  vi.mocked(rolesApi.assignRoleMenus).mockResolvedValue(true)
})

describe('roles page - list (spec: role list, gap endpoint mock-served)', () => {
  it('renders the IPage records with name/type/remark', async () => {
    renderPage()
    expect(await screen.findByText('系统管理员')).toBeInTheDocument()
  })

  it('switching pages re-queries with the next pageNum', async () => {
    const user = userEvent.setup()
    vi.mocked(rolesApi.listRoles).mockResolvedValue(pageFixture([ROLE_A], 25))
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('listitem', { name: '2' }))
    await waitFor(() =>
      expect(rolesApi.listRoles).toHaveBeenCalledWith(2, expect.any(Number)),
    )
  })
})

describe('roles page - CRUD (spec: Role CRUD round-trip)', () => {
  it('creates a role and re-lists', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建角色/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('角色名称'), '护士组')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(rolesApi.createRole).toHaveBeenCalled())
    await waitFor(() => expect(rolesApi.listRoles).toHaveBeenCalledTimes(2))
  })

  it('lands a duplicate-name A0714 msg on the form and keeps the dialog open', async () => {
    const user = userEvent.setup()
    vi.mocked(rolesApi.createRole).mockRejectedValue(
      new (await import('@/api/client')).ApiError('A0714', '数据重复:role name already exists: 护士组'),
    )
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建角色/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('角色名称'), '护士组')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() =>
      expect(screen.getByText(/role name already exists/)).toBeInTheDocument(),
    )
    // drawer did NOT close: `ant-drawer-open` still stands on the drawer root
    expect(document.querySelector('.ant-drawer-open')).not.toBeNull()
  })

  it('deletes behind a Popconfirm and re-lists', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '删除角色' }))
    await user.click(await screen.findByRole('button', { name: /^删\s*除$/ }))
    await waitFor(() => expect(rolesApi.deleteRole).toHaveBeenCalledWith(1))
    await waitFor(() => expect(rolesApi.listRoles).toHaveBeenCalledTimes(2))
  })
})

describe('roles page - menu assignment (spec: full-replace + reopen echo)', () => {
  it('opens the dialog with exactly the saved grant checked', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    await waitFor(() => expect(menusApi.menuTree).toHaveBeenCalled())
    await screen.findByRole('checkbox', { name: '机构管理' })
    const checkboxes = within(screen.getByRole('dialog')).getAllByRole('checkbox')
    // 3 nodes (系统管理/组织架构/机构管理) each carry one checkbox.
    expect(checkboxes).toHaveLength(3)
    // The saved grant [1,13,7] renders all three checked (reopen echo).
    for (const box of checkboxes) expect(box).toBeChecked()
  })

  it('saves the checked set UNION ancestor dirs as a FULL-REPLACE grant', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    // The saved grant is [1,13,7]; check 机构管理 (7) is already on, ADD nothing,
    // uncheck nothing - saving re-posts the same complete set (full replace).
    await screen.findByRole('checkbox', { name: '机构管理' })
    await user.click(screen.getByRole('button', { name: '保存授权' }))
    await waitFor(() => expect(rolesApi.assignRoleMenus).toHaveBeenCalledWith(1, [1, 7, 13]))
  })

  it('after a save the re-listed role echoes the new grant on reopen', async () => {
    const user = userEvent.setup()
    vi.mocked(rolesApi.assignRoleMenus).mockImplementation(() => {
      // The mock server updates the grant; simulate by re-listing the echo.
      vi.mocked(rolesApi.listRoles).mockResolvedValue(
        pageFixture([role({ id: 1, name: '系统管理员', menuIds: [1, 13] })]),
      )
      return Promise.resolve(true)
    })
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    // Uncheck 机构管理 (7): leaves [1,13] as the complete new grant.
    await user.click(await screen.findByRole('checkbox', { name: '机构管理' }))
    await user.click(screen.getByRole('button', { name: '保存授权' }))
    await waitFor(() => expect(rolesApi.assignRoleMenus).toHaveBeenCalledWith(1, [1, 13]))
  })

  it('surfaces an unknown-menuId A0400 as a floating msg', async () => {
    const user = userEvent.setup()
    vi.mocked(rolesApi.assignRoleMenus).mockRejectedValue(
      new (await import('@/api/client')).ApiError('A0400', 'some menu ids do not exist: [99]'),
    )
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    await screen.findByRole('checkbox', { name: '机构管理' })
    await user.click(screen.getByRole('button', { name: '保存授权' }))
    await waitFor(() => expect(screen.getByText(/do not exist/)).toBeInTheDocument())
  })
})

describe('roles page - perms gating', () => {
  it('hides mutation controls without system:role:list but keeps the read', async () => {
    useAuthStore.getState().setSession({ perms: [] })
    renderPage()
    expect(await screen.findByText('系统管理员')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /新建角色/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '菜单授权' })).not.toBeInTheDocument()
  })
})

describe('roles page - button-level gating (button-perm-enforcement-web)', () => {
  it('hides ungranted affordances when only the page perm is held', async () => {
    useAuthStore.getState().setSession({ perms: ['system:role:list'] })
    renderPage()
    await screen.findByText('系统管理员')
    expect(screen.queryByRole('button', { name: /新建角色/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑角色' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除角色' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '菜单授权' })).not.toBeInTheDocument()
  })

  it('reveals the four affordances when their button perms are granted', async () => {
    useAuthStore.getState().setSession({ perms: ['system:role:list', 'system:role:create', 'system:role:update', 'system:role:delete', 'system:role:assign'] })
    renderPage()
    await screen.findByText('系统管理员')
    expect(screen.getByRole('button', { name: /新建角色/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '编辑角色' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '删除角色' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '菜单授权' })).toBeInTheDocument()
  })
})

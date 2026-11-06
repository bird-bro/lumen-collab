import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MenusPage from '@/pages/system/menus'
import * as menusApi from '@/api/menus'
import { ApiError } from '@/api/client'
import type { MenuNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 菜单管理 page (menu-management-web, tasks 2.1-2.5; spec
 * `specs/web-system-console` § Menu Management: the management table-tree
 * with per-node 新增下级/编辑/删除, the type-aware create/edit dialog
 * authoring button `perms` strings, backend rejection surfacing, and the
 * parent-choice guards). API clients are stubbed at the `vi.mock('@/api/menus')`
 * seam (no mock layer - runtime rides the vite proxy to the real backend).
 */
vi.mock('@/api/menus')

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
    children: [
      menu({
        mid: 14,
        title: '用户权限',
        upId: 1,
        type: 'M',
        children: [
          menu({
            mid: 2,
            title: '用户管理',
            upId: 14,
            perms: 'system:user:list',
            path: 'user',
            component: 'system/user/index',
          }),
          menu({ mid: 6, title: '报告审核', upId: 14, type: 'F', perms: 'system:report:audit' }),
        ],
      }),
    ],
  }),
]

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <MenusPage />
    </ConfigProvider>,
  )
}

/** The table row of the node with the given title. */
function rowOf(title: string): HTMLElement {
  const cell = screen.getByText(title)
  return cell.closest('tr') as HTMLElement
}

async function openParentDropdown(user: ReturnType<typeof userEvent.setup>): Promise<HTMLElement> {
  await user.click(screen.getByRole('combobox', { name: '上级节点' }))
  await waitFor(() => {
    expect(document.querySelector('.ant-select-dropdown')).not.toBeNull()
  })
  await waitFor(() => {
    expect(document.querySelectorAll('.ant-select-tree-treenode').length).toBeGreaterThan(0)
  })
  return document.querySelector('.ant-select-dropdown') as HTMLElement
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({ perms: ['system:menu:list'] })
  vi.mocked(menusApi.menuTree).mockResolvedValue(TREE)
})

describe('menus page - management (spec: Menu Management)', () => {
  it('renders the directory/menu/button hierarchy with type tags and perms strings', async () => {
    renderPage()
    expect(await screen.findByText('系统管理')).toBeInTheDocument()
    expect(screen.getByText('用户权限')).toBeInTheDocument()
    expect(screen.getByText('用户管理')).toBeInTheDocument()
    expect(screen.getByText('报告审核')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('system:user:list')).toBeInTheDocument()
      expect(screen.getByText('system:report:audit')).toBeInTheDocument()
    })
    expect(screen.getAllByText('目录').length).toBeGreaterThan(0)
    expect(screen.getAllByText('菜单').length).toBeGreaterThan(0)
    expect(screen.getAllByText('按钮').length).toBeGreaterThan(0)
  })

  it('creates a button node via 新增下级 and refreshes the tree', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户管理')

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '新增下级' }))
    const drawer = screen.getByRole('dialog')
    // creating under a C menu defaults the type to a button (F)
    expect(within(drawer).getByRole('radio', { name: '按钮' })).toBeChecked()
    await user.type(within(drawer).getByLabelText('标题'), '删除')
    await user.type(within(drawer).getByLabelText(/权限标识/), 'system:role:delete')
    await user.click(within(drawer).getByRole('button', { name: '保 存' }))

    await waitFor(() => {
      expect(menusApi.createMenu).toHaveBeenCalledTimes(1)
    })
    expect(menusApi.createMenu).toHaveBeenCalledWith({
      upId: 2,
      type: 'F',
      title: '删除',
      perms: 'system:role:delete',
      path: null,
      component: null,
      icon: null,
      sort: 1,
      visible: 1,
    })
    await waitFor(() => {
      expect(menusApi.menuTree).toHaveBeenCalledTimes(2)
    })
  })

  it('prefills the edit drawer from the row and PUTs the full field set', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户管理')

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '编辑节点' }))
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByLabelText('标题')).toHaveValue('用户管理')
    expect(within(drawer).getByLabelText(/权限标识/)).toHaveValue('system:user:list')
    expect(within(drawer).getByLabelText('前端路径')).toHaveValue('user')
    expect(within(drawer).getByLabelText('前端组件')).toHaveValue('system/user/index')
    expect(within(drawer).getByRole('radio', { name: '菜单' })).toBeChecked()

    await user.clear(within(drawer).getByLabelText('标题'))
    await user.type(within(drawer).getByLabelText('标题'), '用户管理改')
    await user.click(within(drawer).getByRole('button', { name: '保 存' }))

    await waitFor(() => {
      expect(menusApi.updateMenu).toHaveBeenCalledTimes(1)
    })
    expect(menusApi.updateMenu).toHaveBeenCalledWith(2, {
      upId: 14,
      type: 'C',
      title: '用户管理改',
      perms: 'system:user:list',
      path: 'user',
      component: 'system/user/index',
      icon: null,
      sort: 1,
      visible: 1,
    })
    await waitFor(() => {
      expect(menusApi.menuTree).toHaveBeenCalledTimes(2)
    })
  })

  it('deletes a leaf behind a Popconfirm and refreshes the tree', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('报告审核')

    await user.click(within(rowOf('报告审核')).getByRole('button', { name: '删除节点' }))
    await user.click(await screen.findByRole('button', { name: /^删\s*除$/ }))

    await waitFor(() => {
      expect(menusApi.removeMenu).toHaveBeenCalledWith(6)
    })
    await waitFor(() => {
      expect(menusApi.menuTree).toHaveBeenCalledTimes(2)
    })
  })

  it('surfaces the backend delete-rejection msg and keeps the node', async () => {
    const user = userEvent.setup()
    vi.mocked(menusApi.removeMenu).mockRejectedValue(
      new ApiError('A0400', '存在下级节点，请先处理下级再删除'),
    )
    renderPage()
    await screen.findByText('用户管理')

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '删除节点' }))
    await user.click(await screen.findByRole('button', { name: /^删\s*除$/ }))

    expect(await screen.findByText('存在下级节点，请先处理下级再删除')).toBeInTheDocument()
    expect(screen.getByText('用户管理')).toBeInTheDocument()
    // no refresh attempt: the tree stands unchanged
    expect(menusApi.menuTree).toHaveBeenCalledTimes(1)
  })

  it('offers no F nodes and no own subtree as parent choices when editing', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户权限')

    await user.click(within(rowOf('用户权限')).getByRole('button', { name: '编辑节点' }))
    const dropdown = await openParentDropdown(user)

    // legal: the top directory remains selectable
    expect(within(dropdown).getByText('系统管理')).toBeInTheDocument()
    // excluded: self, descendants, and button nodes
    expect(within(dropdown).queryByText('用户权限')).toBeNull()
    expect(within(dropdown).queryByText('用户管理')).toBeNull()
    expect(within(dropdown).queryByText('报告审核')).toBeNull()
  })

  it('offers no F nodes as parent choices when creating under a menu', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户管理')

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '新增下级' }))
    const dropdown = await openParentDropdown(user)

    expect(within(dropdown).getByText('系统管理')).toBeInTheDocument()
    expect(within(dropdown).getByText('用户权限')).toBeInTheDocument()
    expect(within(dropdown).queryByText('报告审核')).toBeNull()
  })

  it('hides every mutation affordance without the page perm', async () => {
    useAuthStore.getState().setSession({ perms: [] })
    renderPage()
    await screen.findByText('系统管理')

    expect(screen.queryByRole('button', { name: '新增下级' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑节点' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除节点' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /新建根节点/ })).not.toBeInTheDocument()
  })
})

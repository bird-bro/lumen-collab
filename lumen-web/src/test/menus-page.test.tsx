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
 * 菜单管理 page (button-perm-enforcement-web tasks 2.2 + 3.1/3.2; spec
 * `specs/web-system-console` § Menu Management + user instruction 2026-11-06:
 * visual alignment with the 随访 reference `fu_web/src/views/system/menu/index.vue`
 * - 菜单名/类型/路径/组件/权限标识/排序/可见 columns, a menu-name filter tool row,
 * icon actions with per-action perms, and the reference drawer wording). API
 * clients stub at the `vi.mock('@/api/menus')` seam (no mock layer - runtime
 * rides the vite proxy to the real backend).
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
    icon: 'setting',
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
          menu({ mid: 6, title: '报告审核', upId: 14, type: 'F', perms: 'system:report:audit', visible: true }),
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
  await user.click(screen.getByRole('combobox', { name: '上级菜单' }))
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
  useAuthStore.getState().setSession({
    perms: [
      'system:menu:list',
      'system:menu:create',
      'system:menu:update',
      'system:menu:delete',
    ],
  })
  vi.mocked(menusApi.menuTree).mockResolvedValue(TREE)
})

describe('menus page - management (spec: Menu Management)', () => {
  it('renders the 随访-style column set with type tags, perms, and paths', async () => {
    renderPage()
    expect(await screen.findByText('系统管理')).toBeInTheDocument()
    for (const header of ['菜单名', '类型', '路径', '组件', '权限标识', '排序', '可见']) {
      expect(screen.getByText(header)).toBeInTheDocument()
    }
    // cell values: path / component / perms render from the row data
    expect(screen.getByText('user')).toBeInTheDocument()
    expect(screen.getByText('system/user/index')).toBeInTheDocument()
    expect(screen.getByText('system:user:list')).toBeInTheDocument()
    expect(screen.getByText('system:report:audit')).toBeInTheDocument()
    // type tags: 目录 / 页面 / 按钮
    expect(screen.getAllByText('目录').length).toBeGreaterThan(0)
    expect(screen.getAllByText('页面').length).toBeGreaterThan(0)
    expect(screen.getAllByText('按钮').length).toBeGreaterThan(0)
  })

  it('renders the visible column as a tag and shows "-" for button rows', async () => {
    renderPage()
    await screen.findByText('系统管理')
    expect(screen.getAllByText('显示').length).toBeGreaterThan(0)
    const buttonRow = rowOf('报告审核')
    expect(within(buttonRow).getByText('-')).toBeInTheDocument()
  })

  it('filters the tree by 菜单名 on 查询 (subtree keeps, ancestor chain kept)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理')

    await user.type(screen.getByPlaceholderText('菜单名'), '报告')
    await user.click(screen.getByRole('button', { name: /查询/ }))

    expect(screen.getByText('报告审核')).toBeInTheDocument()
    expect(screen.getByText('用户权限')).toBeInTheDocument() // ancestor chain kept
    expect(screen.queryByText('用户管理')).not.toBeInTheDocument() // sibling pruned
  })

  it('creates a button node via 添加子菜单 and refreshes the tree', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户管理')

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '添加子菜单' }))
    const drawer = screen.getByRole('dialog')
    // creating under a C menu defaults the type to a button (F)
    expect(within(drawer).getByRole('radio', { name: '按钮' })).toBeChecked()
    await user.type(within(drawer).getByLabelText('菜单标题'), '删除')
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

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '编辑' }))
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByLabelText('菜单标题')).toHaveValue('用户管理')
    expect(within(drawer).getByLabelText(/权限标识/)).toHaveValue('system:user:list')
    expect(within(drawer).getByLabelText('路径')).toHaveValue('user')
    expect(within(drawer).getByLabelText('组件')).toHaveValue('system/user/index')
    expect(within(drawer).getByRole('radio', { name: '页面' })).toBeChecked()

    await user.clear(within(drawer).getByLabelText('菜单标题'))
    await user.type(within(drawer).getByLabelText('菜单标题'), '用户管理改')
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

    await user.click(within(rowOf('报告审核')).getByRole('button', { name: '删除菜单' }))
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

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '删除菜单' }))
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

    await user.click(within(rowOf('用户权限')).getByRole('button', { name: '编辑' }))
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

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '添加子菜单' }))
    const dropdown = await openParentDropdown(user)

    expect(within(dropdown).getByText('系统管理')).toBeInTheDocument()
    expect(within(dropdown).getByText('用户权限')).toBeInTheDocument()
    expect(within(dropdown).queryByText('报告审核')).toBeNull()
  })

  it('hides every mutation affordance without the matching perm', async () => {
    useAuthStore.getState().setSession({ perms: ['system:menu:list'] })
    renderPage()
    await screen.findByText('系统管理')

    expect(screen.queryByRole('button', { name: '添加子菜单' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除菜单' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /添加/ })).not.toBeInTheDocument()
  })
})

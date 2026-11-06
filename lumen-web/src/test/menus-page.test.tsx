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
 * - 菜单/类型/路径/组件/权限标识/显示/排序 columns, a menu-name filter tool row,
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

const DEEP_TREE: MenuNode[] = [
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
            children: [
              menu({ mid: 7, title: '用户删除', upId: 2, type: 'F', perms: 'system:user:delete' }),
            ],
          }),
        ],
      }),
    ],
  }),
]

const TREE: MenuNode[] = [
  menu({ mid: 30, title: '放射工作站', upId: 0, type: 'W' }),
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
            remark: '用户菜单备注',
          }),
          menu({ mid: 6, title: '报告审核', upId: 14, type: 'F', perms: 'system:report:audit', visible: true }),
        ],
      }),
    ],
  }),
]

/** Expand one row's tree affordance; controlled tree expansion is async-safe. */
async function expandRow(title: string): Promise<void> {
  const icon = rowOf(title).querySelector('.ant-table-row-expand-icon') as HTMLElement | null
  if (!icon) throw new Error(`No expand icon on row: ${title}`)
  await userEvent.setup().click(icon)
}

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
    await expandRow('用户权限')
    const headers = screen.getAllByRole('columnheader').map((header) => header.textContent)
    for (const header of ['菜单', '类型', '路径', '组件', '权限标识', '显示', '排序']) {
      expect(headers).toContain(header)
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

  it('expands only the first tree layer by default', async () => {
    vi.mocked(menusApi.menuTree).mockResolvedValue(DEEP_TREE)
    renderPage()

    // Layer 1 is expanded, so layer 2 is visible.
    expect(await screen.findByText('用户权限')).toBeInTheDocument()
    // Layers 3+ stay folded until the user expands their ancestors.
    expect(screen.queryByText('用户管理')).not.toBeInTheDocument()
    expect(screen.queryByText('用户删除')).not.toBeInTheDocument()

    await userEvent
      .setup()
      .click(rowOf('用户权限').querySelector('.ant-table-row-expand-icon') as HTMLElement)
    expect(screen.getByText('用户管理')).toBeInTheDocument()

    await userEvent
      .setup()
      .click(rowOf('用户管理').querySelector('.ant-table-row-expand-icon') as HTMLElement)
    expect(screen.getByText('用户删除')).toBeInTheDocument()
  })

  it('left-aligns type cells, centers sort/visible/action cells, and centers every header', async () => {
    renderPage()
    await screen.findByText('系统管理')
    await expandRow('用户权限')

    for (const header of ['菜单', '类型', '路径', '组件', '权限标识', '显示', '排序', '操作']) {
      const headerCell = screen
        .getAllByRole('columnheader')
        .find((cell) => cell.textContent === header)
      expect(headerCell).toHaveStyle({ textAlign: 'center' })
    }

    const row = rowOf('用户管理')
    expect(within(row).getByText('页面').closest('td')).toHaveStyle({ textAlign: 'left' })
    expect(within(row).getByText('1').closest('td')).toHaveStyle({ textAlign: 'center' })

    // 工作站 uses its own tag color, distinct from 目录/页面/按钮.
    expect(within(rowOf('放射工作站')).getByText('工作站').closest('.ant-tag')).toHaveClass(
      'ant-tag-purple',
    )
    expect(within(row).getByLabelText('显示').closest('td')).toHaveStyle({ textAlign: 'center' })
    expect(row.querySelector('td:last-child')).toHaveStyle({ textAlign: 'center' })
  })

  it('orders the create/edit type radios as 工作站/页面/目录/按钮', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理')

    await user.click(screen.getByRole('button', { name: /添加$/ }))
    const drawer = screen.getByRole('dialog')
    expect(
      within(drawer)
        .getAllByRole('radio')
        .map((radio) => radio.closest('label')?.textContent),
    ).toEqual(['工作站', '页面', '目录', '按钮'])
  })

  it('orders the columns and refreshes from the action header icon', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理')

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      '菜单',
      '类型',
      '路径',
      '组件',
      '权限标识',
      '显示',
      '排序',
      '操作',
    ])

    await user.click(screen.getByRole('button', { name: '刷新列表' }))
    await waitFor(() => {
      expect(menusApi.menuTree).toHaveBeenCalledTimes(2)
    })
  })

  it('renders the visible column as an icon and shows "-" for button rows', async () => {
    renderPage()
    await screen.findByText('系统管理')
    await expandRow('用户权限')
    expect(screen.getAllByLabelText('显示').length).toBeGreaterThan(0)
    const buttonRow = rowOf('报告审核')
    expect(within(buttonRow).getByText('-')).toBeInTheDocument()
  })

  it('filters the tree by 菜单名 on 查询 (subtree keeps, ancestor chain kept)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理')
    await expandRow('用户权限')

    await user.type(screen.getByPlaceholderText('菜单名'), '报告')
    await user.click(screen.getByRole('button', { name: /查询/ }))

    expect(screen.getByText('报告审核')).toBeInTheDocument()
    expect(screen.getByText('用户权限')).toBeInTheDocument() // ancestor chain kept
    expect(screen.queryByText('用户管理')).not.toBeInTheDocument() // sibling pruned
  })

  it('creates a button node via 添加子菜单 and refreshes the tree', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户权限')
    await expandRow('用户权限')
    expect(await screen.findByText('用户管理')).toBeInTheDocument()

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '添加子菜单' }))
    const drawer = screen.getByRole('dialog')
    // creating under a C menu defaults the type to a button (F)
    expect(within(drawer).getByRole('radio', { name: '按钮' })).toBeChecked()
    await user.type(within(drawer).getByLabelText('菜单标题'), '删除')
    await user.type(within(drawer).getByLabelText(/权限标识/), 'system:role:delete')
    await user.type(within(drawer).getByLabelText('备注'), '按钮备注')
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
      remark: '按钮备注',
    })
    await waitFor(() => {
      expect(menusApi.menuTree).toHaveBeenCalledTimes(2)
    })
  })

  it('prefills the edit drawer from the row and PUTs the full field set', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户权限')
    await expandRow('用户权限')
    expect(await screen.findByText('用户管理')).toBeInTheDocument()

    await user.click(within(rowOf('用户管理')).getByRole('button', { name: '编辑' }))
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByLabelText('菜单标题')).toHaveValue('用户管理')
    expect(within(drawer).getByLabelText(/权限标识/)).toHaveValue('system:user:list')
    expect(within(drawer).getByLabelText('路径')).toHaveValue('user')
    expect(within(drawer).getByLabelText('组件')).toHaveValue('system/user/index')
    expect(within(drawer).getByRole('radio', { name: '页面' })).toBeChecked()
    expect(within(drawer).getByLabelText('备注')).toHaveValue('用户菜单备注')
    expect(within(drawer).getByText(/页面权限串/)).toHaveStyle({ fontSize: '12px' })
    const visibleRow = within(drawer)
      .getByRole('switch', { name: '是否显示' })
      .closest('[data-menu-visible-row]')
    expect(visibleRow).toHaveStyle({ display: 'flex' })

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
      remark: '用户菜单备注',
    })
    await waitFor(() => {
      expect(menusApi.menuTree).toHaveBeenCalledTimes(2)
    })
  })

  it('deletes a leaf behind a Popconfirm and refreshes the tree', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('用户权限')
    await expandRow('用户权限')
    expect(await screen.findByText('报告审核')).toBeInTheDocument()

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
    await screen.findByText('用户权限')
    await expandRow('用户权限')
    expect(await screen.findByText('用户管理')).toBeInTheDocument()

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
    await screen.findByText('用户权限')
    await expandRow('用户权限')
    expect(await screen.findByText('用户管理')).toBeInTheDocument()

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

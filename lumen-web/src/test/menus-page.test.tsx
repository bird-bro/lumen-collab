import { render, screen, waitFor, within } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MenusPage from '@/pages/system/menus'
import * as menusApi from '@/api/menus'
import type { MenuNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 菜单管理 page (batch 6b, task 6.5; spec `specs/web-system-console`
 * § Menu Tree Visibility: a READ-ONLY tree of the directory/menu/button
 * structure with every node's `perms` string visible - no mutation surface).
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
          menu({ mid: 2, title: '用户管理', upId: 14, perms: 'system:user:list' }),
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

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({ perms: ['system:menu:list'] })
  vi.mocked(menusApi.menuTree).mockResolvedValue(TREE)
})

describe('menus page - read-only tree (spec: Menu tree renders)', () => {
  it('renders the directory/menu/button hierarchy from the menu tree', async () => {
    renderPage()
    expect(await screen.findByText('系统管理')).toBeInTheDocument()
    expect(screen.getByText('用户权限')).toBeInTheDocument()
    expect(screen.getByText('用户管理')).toBeInTheDocument()
    expect(screen.getByText('报告审核')).toBeInTheDocument()
  })

  it('shows each node\'s perms string', async () => {
    renderPage()
    await screen.findByText('系统管理')
    await waitFor(() => {
      expect(screen.getByText('system:user:list')).toBeInTheDocument()
      expect(screen.getByText('system:report:audit')).toBeInTheDocument()
    })
  })

  it('offers no mutation controls (read-only surface)', async () => {
    renderPage()
    await screen.findByText('系统管理')
    expect(screen.queryByRole('button', { name: '新建' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /删除/ })).not.toBeInTheDocument()
    const tree = screen.getByRole('tree')
    expect(within(tree).queryAllByRole('checkbox')).toHaveLength(0)
  })

  it('marks the node types (目录/菜单/按钮)', async () => {
    renderPage()
    await screen.findByText('系统管理')
    await waitFor(() => {
      expect(screen.getAllByText('目录').length).toBeGreaterThan(0)
      expect(screen.getAllByText('菜单').length).toBeGreaterThan(0)
      expect(screen.getAllByText('按钮').length).toBeGreaterThan(0)
    })
  })
})

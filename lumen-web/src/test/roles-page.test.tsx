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
vi.mock('@/api/dicts')
// The utils RESOLVER is mocked at this seam (not @/api/dicts): the real utils
// module carries a session cache whose state would leak across tests; the cache
// semantics are covered by dict-items.test.ts with fresh modules per test.
vi.mock('@/utils/dict-items', () => ({
  ROLE_TYPE_DICT_NAME: '角色类型',
  dictItemsByName: vi.fn(),
  resolveDictLabel: (items: { code: number; value: string }[], code: number) =>
    items.find((item) => item.code === code)?.value ?? String(code),
}))

function role(partial: Partial<RoleItem> & { id: number; name: string }): RoleItem {
  return {
    type: 10,
    enable: 1,
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

import * as dictUtils from '@/utils/dict-items'

const ROLE_TYPE_DICT = [
  { code: 10, value: '系统角色' },
  { code: 11, value: 'RCP角色' },
]

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(dictUtils.dictItemsByName).mockResolvedValue(ROLE_TYPE_DICT)
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

describe('roles page - list columns (UI polish 2026-11-09)', () => {
  it('does not show a 序号 column (user ruling 2026-11-11)', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([role({ id: 7, name: '审计员', createBy: 2, remark: '只读审计', menuIds: [1, 13] })]),
    )
    renderPage()
    await screen.findByText('审计员')
    expect(screen.queryByText('序号')).not.toBeInTheDocument()
    expect(screen.queryByText('7')).not.toBeInTheDocument()
  })

  it('labels the type tag (10 系统角色 / 11 RCP角色) instead of the raw number', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([role({ id: 8, name: '护士组', type: 11 })]),
    )
    renderPage()
    await screen.findByText('护士组')
    expect(screen.getByText('RCP角色')).toBeInTheDocument()
    expect(screen.queryByText('11')).not.toBeInTheDocument()
  })

  it('drops the 授权菜单数 column (user ruling 2026-11-09: weak signal)', async () => {
    renderPage()
    await screen.findByText('系统管理员')
    expect(screen.queryByText('授权菜单数')).not.toBeInTheDocument()
  })

  it('renders 类型 as its own column with a blue tag for 系统角色', async () => {
    renderPage()
    await screen.findByText('系统管理员')
    const tag = screen.getByText('系统角色')
    expect(tag.closest('td')).not.toBeNull()
    expect(tag).toHaveClass('ant-tag-blue')
    expect(tag).toHaveClass('ant-tag-borderless')
  })

  it('styles RCP角色 cyan in the same column', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([role({ id: 8, name: '护士组', type: 11 })]),
    )
    renderPage()
    await screen.findByText('护士组')
    expect(screen.getByText('RCP角色')).toHaveClass('ant-tag-cyan')
  })

  it('carries the shield-check glyph on 菜单授权, not apartment tree or key (rulings 2026-11-09: tree read as org structure; key read as a magnifier)', async () => {
    renderPage()
    await screen.findByText('系统管理员')
    const assignBtn = screen.getByRole('button', { name: '菜单授权' })
    expect(assignBtn.querySelector('.anticon-apartment')).toBeNull()
    expect(assignBtn.querySelector('.anticon-key')).toBeNull()
    expect(assignBtn.querySelector('.anticon-search')).toBeNull()
    expect(assignBtn.querySelector('.anticon-safety-certificate')).not.toBeNull()
  })

  it('stacks 创建人 / 创建时间 in one column', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([role({ id: 9, name: '会诊组', createBy: 3, remark: '院内会诊', menuIds: [1] })]),
    )
    renderPage()
    await screen.findByText('会诊组')
    expect(screen.getByText('UID 3')).toBeInTheDocument()
    expect(screen.getByText('2026-11-04 09:00:00')).toBeInTheDocument()
  })

  it('falls back to — when the audit creator is null', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([role({ id: 10, name: '远程组', createBy: null, remark: '外部协作', menuIds: [1] })]),
    )
    renderPage()
    await screen.findByText('远程组')
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('renders the name cell without the team icon (user ruling 2026-11-09)', async () => {
    renderPage()
    await screen.findByText('系统管理员')
    expect(document.querySelector('.anticon-team')).toBeNull()
  })

  it('bounds the remark column with ellipsis', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([role({ id: 11, name: '超长组', remark: 'x'.repeat(80) })]),
    )
    renderPage()
    await screen.findByText('超长组')
    const cell = screen.getByText(/^x{80}$/).closest('td')
    expect(cell).toHaveClass('ant-table-cell-ellipsis')
  })
})

describe('roles page - dict-driven type vocabulary (role-type-dict-web)', () => {
  it('renders 类型 labels from the dict with the code-10 blue / others cyan chrome', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([
        role({ id: 21, name: '系统侧', type: 10 }),
        role({ id: 22, name: '业务侧', type: 11 }),
        role({ id: 23, name: '外来侧', type: 42 }),
      ]),
    )
    renderPage()
    await screen.findByText('系统侧')
    const blue = screen.getByText('系统角色')
    expect(blue).toHaveClass('ant-tag-blue')
    const cyan = screen.getByText('RCP角色')
    expect(cyan).toHaveClass('ant-tag-cyan')
    // Unknown code: dict miss falls back to the RAW number (cyan, no blank cell).
    const fallback = screen.getByText('42')
    expect(fallback).toHaveClass('ant-tag-cyan')
  })

  it('dict fetch failure degrades: one message, raw codes, CRUD intact', async () => {
    vi.mocked(dictUtils.dictItemsByName).mockRejectedValue(
      new (await import('@/api/client')).ApiError('A0301', 'denied'),
    )
    renderPage()
    await screen.findByText('系统管理员')
    // Raw codes render (the seeded admin role is type 10).
    expect(screen.getByText('10')).toBeInTheDocument()
    // One error message surfaced.
    await waitFor(() => expect(screen.getByText(/denied/)).toBeInTheDocument())
    // The CRUD round-trip still works (create drawer opens, save posts).
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /新建角色/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('角色名称'), '降级组')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(rolesApi.createRole).toHaveBeenCalled())
  })

  it('one resolver call per mount: re-lists reuse the loaded dict', async () => {
    renderPage()
    await screen.findByText('系统管理员')
    await screen.findByText('系统角色')
    // Reload the list (refresh button) - the dict must NOT re-resolve (the
    // mount-only effect owns the load; the wire-level caching is the utils layer,
    // covered by dict-items.test.ts).
    vi.mocked(rolesApi.listRoles).mockClear()
    await userEvent.setup().click(screen.getByRole('button', { name: '刷新列表' }))
    await waitFor(() => expect(rolesApi.listRoles).toHaveBeenCalledTimes(1))
    expect(dictUtils.dictItemsByName).toHaveBeenCalledTimes(1)
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

describe('roles page - dict-driven drawer Select (role-type-dict-web)', () => {
  it('create drawer 类型 is a dict Select with 系统角色 preselected', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建角色/ }))
    const dialog = within(screen.getByRole('dialog'))
    // The Select carries the dict options; 系统角色 (code 10) is the initialValue.
    // Scope to the VISIBLE dropdown portal - AntD also renders a hidden
    // measurement listbox (#type_list) whose options cannot be clicked.
    await user.click(dialog.getByRole('combobox', { name: '类型' }))
    const dropdown = await waitFor(() => {
      const el = document.querySelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
      expect(el).not.toBeNull()
      return el as HTMLElement
    })
    expect(within(dropdown).getByText('系统角色')).toBeInTheDocument()
    expect(within(dropdown).getByText('RCP角色')).toBeInTheDocument()
    // The preselected value displays its dict label.
    await waitFor(() =>
      expect(screen.getByRole('dialog').textContent).toContain('系统角色'),
    )
  })

  it('selecting a category posts its numeric code unchanged', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建角色/ }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('角色名称'), '护士组')
    await user.click(dialog.getByRole('combobox', { name: '类型' }))
    const dropdown = await waitFor(() => {
      const el = document.querySelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
      expect(el).not.toBeNull()
      return el as HTMLElement
    })
    await user.click(within(dropdown).getByText('RCP角色'))
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() =>
      expect(rolesApi.createRole).toHaveBeenCalledWith(
        expect.objectContaining({ type: 11 }),
      ),
    )
  })

  it('edit drawer reflects the row code in the Select', async () => {
    const user = userEvent.setup()
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([role({ id: 31, name: '业务组', type: 11 })]),
    )
    renderPage()
    await screen.findByText('业务组')
    await user.click(screen.getByRole('button', { name: '编辑角色' }))
    await within(screen.getByRole('dialog')).findByText('RCP角色')
  })
})

describe('roles page - drawer remark textarea (user request 2026-11-09)', () => {
  it('pins the remark textarea at 130px in both drawers', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建角色/ }))
    const remark = within(screen.getByRole('dialog')).getByPlaceholderText('选填')
    expect(remark).toHaveStyle({ height: '130px' })
  })
})

describe('roles page - enable switch (role-type-dict-web D7)', () => {
  it('renders the 状态 switch reflecting the enable flag', async () => {
    vi.mocked(rolesApi.listRoles).mockResolvedValue(
      pageFixture([
        role({ id: 41, name: '在用组', enable: 1 }),
        role({ id: 42, name: '停用组', enable: 0 }),
      ]),
    )
    renderPage()
    await screen.findByText('在用组')
    const on = screen.getByRole('switch', { name: '启用 在用组' })
    expect(on).toHaveAttribute('aria-checked', 'true')
    const off = screen.getByRole('switch', { name: '启用 停用组' })
    expect(off).toHaveAttribute('aria-checked', 'false')
  })

  it('toggling posts the enable command and re-lists', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('switch', { name: '启用 系统管理员' }))
    await waitFor(() => expect(rolesApi.toggleRoleEnable).toHaveBeenCalledWith(1, 0))
    // The list re-reads the persisted value.
    await waitFor(() => expect(rolesApi.listRoles).toHaveBeenCalledTimes(2))
  })

  it('a rejected toggle surfaces the message and the re-list restores the state', async () => {
    const user = userEvent.setup()
    vi.mocked(rolesApi.toggleRoleEnable).mockRejectedValue(
      new (await import('@/api/client')).ApiError('A0400', '内置角色不允许停用'),
    )
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('switch', { name: '启用 系统管理员' }))
    await waitFor(() => expect(screen.getByText(/内置角色不允许停用/)).toBeInTheDocument())
    // The re-list restores the persisted state (no optimistic flip).
    await waitFor(() => expect(rolesApi.listRoles).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('switch', { name: '启用 系统管理员' })).toHaveAttribute('aria-checked', 'true')
  })
})

describe('roles page - menu assignment (spec: full-replace + reopen echo)', () => {
  /** rc-tree checkboxes carry no checkbox role - assert via the node wrapper. */
  function nodeCheckbox(title: string): HTMLElement | null | undefined {
    return screen.getByText(title).closest('.ant-tree-treenode')?.querySelector('.ant-tree-checkbox')
  }

  it('renders the grant tree expanded to level 3 with no type tags (user rulings 2026-11-09)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    await waitFor(() => expect(menusApi.menuTree).toHaveBeenCalled())
    // Level 1 + 2 + 3 all visible on open - no switcher clicks needed.
    expect(await screen.findByText('机构管理')).toBeInTheDocument()
    expect(screen.getByText('组织架构')).toBeInTheDocument()
    // The type tags (目录/按钮/工作站) are gone - plain titles only.
    expect(screen.queryByText('目录')).not.toBeInTheDocument()
    expect(screen.queryByText('工作站')).not.toBeInTheDocument()
    // The hint paragraph is gone.
    expect(screen.queryByText(/勾选集即该角色的/)).not.toBeInTheDocument()
  })

  it('opens the dialog with exactly the saved grant checked', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    await screen.findByText('机构管理')
    // 3 nodes (系统管理/组织架构/机构管理) each carry one checkbox.
    const dialog = screen.getByRole('dialog')
    expect(dialog.querySelectorAll('.ant-tree-checkbox')).toHaveLength(3)
    // The saved grant [1,13,7] renders all three checked (reopen echo).
    for (const title of ['系统管理', '组织架构', '机构管理']) {
      expect(nodeCheckbox(title)).toHaveClass('ant-tree-checkbox-checked')
    }
  })

  it('checking a parent cascades to all descendants; unchecking it clears them', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    await screen.findByText('机构管理')
    // All checked from the echo; uncheck the root → children clear too.
    await user.click(nodeCheckbox('系统管理')!)
    expect(nodeCheckbox('组织架构')).not.toHaveClass('ant-tree-checkbox-checked')
    expect(nodeCheckbox('机构管理')).not.toHaveClass('ant-tree-checkbox-checked')
    // Re-check the root → every descendant checks again; save posts the union.
    await user.click(nodeCheckbox('系统管理')!)
    await user.click(screen.getByRole('button', { name: '保存授权' }))
    await waitFor(() => expect(rolesApi.assignRoleMenus).toHaveBeenCalledWith(1, [1, 7, 13]))
  })

  it('unchecking a leaf unchecks its ancestor path and posts the remaining grant', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    await screen.findByText('机构管理')
    // Uncheck the button leaf (7): the ancestor path (13 → 1) follows - with
    // this single-child chain no indeterminate remains, both dirs clear.
    await user.click(nodeCheckbox('机构管理')!)
    expect(nodeCheckbox('组织架构')).not.toHaveClass('ant-tree-checkbox-checked')
    expect(nodeCheckbox('系统管理')).not.toHaveClass('ant-tree-checkbox-checked')
    await user.click(screen.getByRole('button', { name: '保存授权' }))
    await waitFor(() => expect(rolesApi.assignRoleMenus).toHaveBeenCalledWith(1, []))
  })

  it('saves the checked set UNION ancestor dirs as a FULL-REPLACE grant', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    // The saved grant is [1,13,7]; ADD nothing, uncheck nothing - saving
    // re-posts the same complete set (full replace).
    await screen.findByText('机构管理')
    await user.click(screen.getByRole('button', { name: '保存授权' }))
    await waitFor(() => expect(rolesApi.assignRoleMenus).toHaveBeenCalledWith(1, [1, 7, 13]))
  })

  it('surfaces an unknown-menuId A0400 as a floating msg', async () => {
    const user = userEvent.setup()
    vi.mocked(rolesApi.assignRoleMenus).mockRejectedValue(
      new (await import('@/api/client')).ApiError('A0400', 'some menu ids do not exist: [99]'),
    )
    renderPage()
    await screen.findByText('系统管理员')
    await user.click(screen.getByRole('button', { name: '菜单授权' }))
    await screen.findByText('机构管理')
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

import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UsersPage from '@/pages/system/users'
import * as usersApi from '@/api/users'
import * as rolesApi from '@/api/roles'
import { ApiError } from '@/api/client'
import type { RoleItem, UserItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 用户管理 page (batch 6a, task 6.3; spec `specs/web-system-console`
 * § User Management: `GET /system/users/list` returns a PLAIN list the view
 * paginates client-side, create, password reset, enable/disable, and a
 * duplicate username surfaces the backend A-class `msg` ON THE FORM with the
 * dialog staying open).
 *
 * Layout source: `prototype/console-shell.html` `.main > .panel` (the users
 * table page) - `.search` tool row (label + select + 查询 on the left, the
 * `.push` primary 新建用户 on the right), `.tbl` with 用户名/姓名/角色/启用/
 * 创建时间/操作 (`.th-refresh` glyph), `.tag` chips, the `.switch` in the 启用
 * column, the centered 60px `.pager` with `共 N 条`, and the `.modal` create
 * dialog whose 用户名 helper line states the duplicate-username contract
 * verbatim. The prototype's 编辑/删除 row glyphs are NOT implemented: the api
 * delta offers no user edit or delete endpoint (`specs/web-app-shell` § API
 * Client Seam Without Invented Endpoints) - the operation column carries the
 * documented 重置密码 verb only.
 *
 * The api seam is stubbed with `vi.mock` (design D9).
 */
vi.mock('@/api/users')
vi.mock('@/api/roles')

function user(partial: Partial<UserItem> & { uid: number; username: string }): UserItem {
  return {
    name: '同事',
    enable: 1,
    mustChangePassword: 0,
    createTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

const ADMIN = user({ uid: 1, username: 'admin', name: '系统管理员', mustChangePassword: 1 })
const WANG = user({ uid: 2, username: 'wangq', name: '王倩', createTime: '2026-10-28 14:32:07' })
const LI = user({ uid: 3, username: 'lwb', name: '李文博', createTime: '2026-11-01 10:15:44' })

function role(partial: Partial<RoleItem> & { id: number; name: string }): RoleItem {
  return {
    type: 11,
    remark: null,
    createBy: null,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <UsersPage />
    </ConfigProvider>,
  )
}

/** jsdom starts no CSS animation: a closed AntD modal lingers mid-leave, so the
 *  observable close signal is the `*-leave` motion class (see the orgs tests). */
const DIALOG_LEAVING = 'ant-zoom-leave'

async function expectDialogClosed() {
  const dialog = screen.getByRole('dialog')
  await waitFor(() => expect(dialog).toHaveClass(DIALOG_LEAVING))
}

/**
 * Row of a given username, scoped to the TABLE: jsdom never finishes a modal's
 * leave animation, so a dialog that mentions the username would otherwise make
 * `getByText` ambiguous.
 */
function rowOf(username: string): HTMLElement {
  const table = document.querySelector('.ant-table') as HTMLElement
  const cell = within(table).getByText(username)
  const row = cell.closest('tr') as HTMLElement
  expect(row).toBeTruthy()
  return row
}

beforeEach(() => {
  vi.clearAllMocks()
  // The documented perms string of this page (SEED_MENUS id 2 = the V2 seed row,
  // also the backend @PreAuthorize on the user list). No new perms code.
  useAuthStore.getState().setSession({ perms: ['system:user:list'] })
  vi.mocked(usersApi.listUsers).mockResolvedValue([ADMIN, WANG, LI])
  vi.mocked(rolesApi.listRoles).mockResolvedValue({
    records: [role({ id: 1, name: '系统管理员', type: 10 })],
    total: 1,
    size: 20,
    current: 1,
    pages: 1,
  })
  vi.mocked(usersApi.createUser).mockResolvedValue(4)
  vi.mocked(usersApi.resetPassword).mockResolvedValue(true)
  vi.mocked(usersApi.enableUser).mockResolvedValue(true)
})

describe('users page - paged list (spec: Paged user list)', () => {
  it('renders the plain array with username / name / enable switch / create time', async () => {
    renderPage()
    await screen.findByText('admin')
    expect(screen.getByText('系统管理员')).toBeInTheDocument()
    expect(screen.getByText('王倩')).toBeInTheDocument()
    expect(usersApi.listUsers).toHaveBeenCalledTimes(1)

    const adminSwitch = within(rowOf('admin')).getByRole('switch')
    expect(adminSwitch).toBeChecked()
    expect(screen.getByText('2026-10-28 14:32:07')).toBeInTheDocument()
    // mustChangePassword = 1 is echoed as a chip (the reset contract's visible side)
    expect(within(rowOf('admin')).getByText('首次登录需改密')).toBeInTheDocument()
    expect(within(rowOf('wangq')).queryByText('首次登录需改密')).not.toBeInTheDocument()
  })

  it('paginates client-side: one request, then page switches slice the list', async () => {
    const many = Array.from({ length: 12 }, (_, index) =>
      user({ uid: index + 1, username: `user${index + 1}`, name: `同事${index + 1}` }),
    )
    vi.mocked(usersApi.listUsers).mockResolvedValue(many)
    const user_ = userEvent.setup()
    renderPage()
    await screen.findByText('user1')

    expect(screen.getByText('共 12 条')).toBeInTheDocument()
    expect(screen.queryByText('user11')).not.toBeInTheDocument()

    await user_.click(screen.getByText('2'))
    await screen.findByText('user11')
    expect(screen.queryByText('user1')).not.toBeInTheDocument()
    // no extra request - the array is paged in the view
    expect(usersApi.listUsers).toHaveBeenCalledTimes(1)
  })

  it('the 查询 control filters the loaded users by username/name without re-requesting', async () => {
    const user_ = userEvent.setup()
    renderPage()
    await screen.findByText('admin')

    await user_.type(screen.getByLabelText('用户名 / 姓名'), 'wang')
    await user_.click(screen.getByRole('button', { name: /查询/ }))

    expect(screen.getByText('wangq')).toBeInTheDocument()
    expect(screen.queryByText('lwb')).not.toBeInTheDocument()
    expect(screen.queryByText('admin')).not.toBeInTheDocument()
    expect(screen.getByText('共 1 条')).toBeInTheDocument()
    // spec § Paged user list: a filter change re-queries, the filtering itself
    // happens in the view over the plain array.
    await waitFor(() => expect(usersApi.listUsers).toHaveBeenCalledTimes(2))
  })

  it('re-queries the list from the 操作 header refresh glyph', async () => {
    const user_ = userEvent.setup()
    renderPage()
    await screen.findByText('admin')

    await user_.click(screen.getByRole('button', { name: '刷新列表' }))
    await waitFor(() => expect(usersApi.listUsers).toHaveBeenCalledTimes(2))
  })
})

describe('users page - create (spec: Duplicate username feedback)', () => {
  it('creates a user with the documented body, closes the dialog and re-lists', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.listUsers)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([ADMIN, WANG, LI, user({ uid: 4, username: 'chenz', name: '陈志远' })])
    renderPage()
    await screen.findByText('admin')

    await user_.click(screen.getByRole('button', { name: /新建用户/ }))
    const dialog = await screen.findByRole('dialog', { name: '新建用户' })
    await user_.type(within(dialog).getByLabelText('用户名'), 'chenz')
    await user_.type(within(dialog).getByLabelText('姓名'), '陈志远')
    await user_.type(within(dialog).getByLabelText('初始密码'), 'lumen123')
    // the prototype modal's 角色 row (CreateUserBody.roleIds - documented, optional)
    await user_.click(within(dialog).getByLabelText('角色'))
    // rc-select renders both an a11y listbox option (whose text is the VALUE)
    // and the visual option; the click target is the visual one.
    await user_.click(
      await screen.findByText('系统管理员', { selector: '.ant-select-item-option-content' }),
    )
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() =>
      expect(usersApi.createUser).toHaveBeenCalledWith({
        username: 'chenz',
        name: '陈志远',
        password: 'lumen123',
        roleIds: [1],
      }),
    )
    await expectDialogClosed()
    await waitFor(() => expect(usersApi.listUsers).toHaveBeenCalledTimes(2))
    await screen.findByText('陈志远')
  })

  it('shows the backend duplicate-username msg on the form and keeps the dialog open', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.createUser).mockRejectedValue(new ApiError('A0111', '用户名已存在'))
    renderPage()
    await screen.findByText('admin')

    await user_.click(screen.getByRole('button', { name: /新建用户/ }))
    const dialog = await screen.findByRole('dialog', { name: '新建用户' })
    await user_.type(within(dialog).getByLabelText('用户名'), 'admin')
    await user_.type(within(dialog).getByLabelText('姓名'), '重名同事')
    await user_.type(within(dialog).getByLabelText('初始密码'), 'lumen123')
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    // form-level feedback (the prototype's helper line promises exactly this)
    const fieldError = await within(dialog).findByText('用户名已存在')
    expect(fieldError).toBeInTheDocument()
    expect(dialog).not.toHaveClass(DIALOG_LEAVING)
    expect(within(dialog).getByLabelText('用户名')).toHaveValue('admin')
    // and the list was NOT re-queried (nothing was created)
    expect(usersApi.listUsers).toHaveBeenCalledTimes(1)
  })
})

describe('users page - password reset (spec: Password and enable actions)', () => {
  it('resets via PUT /users/{id}/password and the re-list echoes mustChangePassword', async () => {
    const user_ = userEvent.setup()
    const adminAfterReset = { ...ADMIN, mustChangePassword: 1 }
    const wangAfterReset = { ...WANG, mustChangePassword: 1 }
    vi.mocked(usersApi.listUsers)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([adminAfterReset, wangAfterReset, LI])
    renderPage()
    await screen.findByText('wangq')
    expect(within(rowOf('wangq')).queryByText('首次登录需改密')).not.toBeInTheDocument()

    await user_.click(within(rowOf('wangq')).getByRole('button', { name: '重置密码' }))
    const dialog = await screen.findByRole('dialog', { name: '重置密码' })
    expect(within(dialog).getByText('wangq')).toBeInTheDocument()
    await user_.type(within(dialog).getByLabelText('新密码'), 'Reset2026!')
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() => expect(usersApi.resetPassword).toHaveBeenCalledWith(2, 'Reset2026!'))
    await expectDialogClosed()
    await waitFor(() => expect(usersApi.listUsers).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(within(rowOf('wangq')).getByText('首次登录需改密')).toBeInTheDocument())
  })

  it('surfaces an A-class reset failure as a floating msg and keeps the dialog open', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.resetPassword).mockRejectedValue(new ApiError('A0400', 'newPassword must not be blank'))
    renderPage()
    await screen.findByText('admin')

    await user_.click(within(rowOf('admin')).getByRole('button', { name: '重置密码' }))
    const dialog = await screen.findByRole('dialog', { name: '重置密码' })
    // the client-side rule blocks an empty submit first: no request, dialog open
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))
    expect(await within(dialog).findByText('请输入新密码')).toBeInTheDocument()
    expect(usersApi.resetPassword).not.toHaveBeenCalled()

    await user_.type(within(dialog).getByLabelText('新密码'), 'Reset2026!')
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))
    // A-class failure: the msg floats (brief: A 类错误 message.error), dialog stays open
    expect(await screen.findByText('newPassword must not be blank')).toBeInTheDocument()
    expect(dialog).not.toHaveClass(DIALOG_LEAVING)
  })
})

describe('users page - enable toggle (spec: Password and enable actions)', () => {
  it('toggles with PUT /users/{id}/enable and the switch follows the re-listed state', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.listUsers)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([ADMIN, { ...WANG, enable: 0 }, LI])
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('switch'))
    await waitFor(() => expect(usersApi.enableUser).toHaveBeenCalledWith(2, 0))
    await waitFor(() => expect(usersApi.listUsers).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(within(rowOf('wangq')).getByRole('switch')).not.toBeChecked())
  })

  it('an out-of-range rejection surfaces the msg and restores the switch to the server state', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.enableUser).mockRejectedValue(new ApiError('A0400', 'enable must be 0 or 1'))
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('switch'))
    expect(await screen.findByText('enable must be 0 or 1')).toBeInTheDocument()
    // the optimistic flip is rolled back by the re-query
    await waitFor(() => expect(usersApi.listUsers).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(within(rowOf('wangq')).getByRole('switch')).toBeChecked())
  })
})

describe('users page - perms gating', () => {
  it('hides the create and reset controls without system:user:list, disables the switch, keeps the read', async () => {
    useAuthStore.getState().setSession({ perms: ['system:role:list'] })
    renderPage()
    await screen.findByText('admin')

    expect(screen.queryByRole('button', { name: /新建用户/ })).not.toBeInTheDocument()
    expect(within(rowOf('admin')).queryByRole('button', { name: '重置密码' })).not.toBeInTheDocument()
    expect(within(rowOf('admin')).getByRole('switch')).toBeDisabled()
    expect(usersApi.listUsers).toHaveBeenCalledTimes(1)
  })
})

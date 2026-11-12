import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UsersPage from '@/pages/system/users'
import * as usersApi from '@/api/users'
import * as rolesApi from '@/api/roles'
import * as depsApi from '@/api/org-deps'
import * as dictsApi from '@/api/dicts'
import { ApiError } from '@/api/client'
import type { OrgDep, RoleItem, UserItem } from '@/api/types'
import { invalidateDictItemsCache } from '@/utils/dict-items'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 用户管理 page (user-edit-delete-profile-web; spec `specs/web-system-console`
 * § User Management): the RCP-shaped list (`GET /system/users/list/profile`)
 * with the 姓名+账号/角色/科室/职称/手机号/邮箱/创建时间/操作 columns, the
 * roleNames chips for ANY row, the shared create+edit drawer (the profile form,
 * dict-driven 性别/职称/检查类型/审核权限, the deps select, the signature
 * upload), the Popconfirm delete, and the 修改/删除 affordances gated by the
 * seeded `system:user:update`/`system:user:delete` button perms. 重置密码 and
 * the 启用 switch stay (password-transport + button-perm precedents).
 *
 * The api seam is stubbed with `vi.mock` (house discipline - unit tests stub
 * API client modules at the seam, never serving page data at runtime).
 */
vi.mock('@/api/users')
vi.mock('@/api/roles')
vi.mock('@/api/org-deps')
vi.mock('@/api/dicts')
vi.mock('@/api/common')

function user(partial: Partial<UserItem> & { uid: number; username: string }): UserItem {
  return {
    name: '同事',
    phone: null,
    email: null,
    gender: null,
    title: null,
    depId: null,
    depName: null,
    modalitys: null,
    repAudit: null,
    signature: null,
    roleNames: [],
    enable: 1,
    mustChangePassword: 0,
    createBy: null,
    createTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

const ADMIN = user({ uid: 1, username: 'admin', name: '系统管理员', mustChangePassword: 1 })
const WANG = user({
  uid: 2,
  username: 'wangq',
  name: '王倩',
  phone: '13800001111',
  email: 'wang@example.com',
  gender: 100402,
  title: 100503,
  depId: 5,
  depName: '放射科',
  modalitys: 'CT,MR',
  repAudit: 2,
  roleNames: ['系统管理员'],
})
const LI = user({ uid: 3, username: 'lwb', name: '李文博', createBy: 1, createTime: '2026-11-01 10:15:44' })

function role(partial: Partial<RoleItem> & { id: number; name: string }): RoleItem {
  return {
    type: 11,
    enable: 1,
    remark: null,
    createBy: null,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

function dep(partial: Partial<OrgDep> & { id: number; name: string }): OrgDep {
  return {
    orgId: 3,
    code: 'dep_ut',
    status: 1,
    ...partial,
  } as OrgDep
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <UsersPage />
    </ConfigProvider>,
  )
}

/** jsdom starts no CSS animation: the observable close signal for an AntD
 *  drawer is its root dropping `ant-drawer-open` (role=dialog sits inside). */
async function expectDialogClosed() {
  await waitFor(() => expect(document.querySelector('.ant-drawer-open')).toBeNull())
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

/** Dict fixture: the {code, value}[] the by-name lookup returns. */
function dictItems(items: Array<[number, string]>) {
  return items.map(([code, value]) => ({ code, value, sort: 1, enable: 1 }))
}

const FULL_PERMS = [
  'system:user:list',
  'system:user:create',
  'system:user:reset-pwd',
  'system:user:enable',
  'system:user:update',
  'system:user:delete',
  'system:user:assign-ws',
]

beforeEach(() => {
  vi.clearAllMocks()
  // the dict util's session cache is module-level - a previous suite's warmed
  // catalogs would leak into these mocks (the role-type-dict hook's own tests
  // exercise the same seam)
  invalidateDictItemsCache()
  useAuthStore.getState().setSession({ perms: FULL_PERMS })
  vi.mocked(usersApi.listUsersWithProfile).mockResolvedValue([ADMIN, WANG, LI])
  vi.mocked(rolesApi.listRoles).mockResolvedValue({
    records: [role({ id: 1, name: '系统管理员', type: 10 })],
    total: 1,
    size: 20,
    current: 1,
    pages: 1,
  })
  vi.mocked(dictsApi.fetchDictItemsByName).mockImplementation(async (name: string) => {
    if (name === '性别') return dictItems([[100401, '男'], [100402, '女']])
    if (name === '职称') return dictItems([[100501, '主任医师'], [100503, '主治医师']])
    if (name === '设备类型') return dictItems([[1, 'DR'], [2, 'CT'], [3, 'MR'], [5, 'US']])
    if (name === '审核权限') return dictItems([[0, '不参与'], [1, '报告医生'], [2, '初审医生'], [3, '终审医生']])
    return []
  })
  vi.mocked(depsApi.listDeps).mockResolvedValue([dep({ id: 5, name: '放射科' }), dep({ id: 6, name: '超声科' })])
  vi.mocked(usersApi.createUser).mockResolvedValue(4)
  vi.mocked(usersApi.resetPassword).mockResolvedValue(true)
  vi.mocked(usersApi.enableUser).mockResolvedValue(true)
  vi.mocked(usersApi.updateUser).mockResolvedValue(true)
  vi.mocked(usersApi.deleteUser).mockResolvedValue(true)
  vi.mocked(usersApi.getUserDetail).mockResolvedValue({
    uid: 2,
    username: 'wangq',
    name: '王倩',
    phone: '13800001111',
    email: 'wang@example.com',
    gender: 100402,
    title: 100503,
    depId: 5,
    // the CSV carries the unified 设备类型 (1024) dict CODES (CT=2, MR=3)
    modalitys: '2,3',
    repAudit: 2,
    signature: '/profile/avatar/2026/11/11/sig.png',
    roleIds: [1],
  })
})

// ===================== list (spec: Paged user list + List columns + Role chips) =====================

describe('users page - profile list (spec: Paged user list / List columns / Role chips)', () => {
  it('renders the profile payload: 姓名+账号 / roleNames chips / 科室 / 职称 / 手机号 / 邮箱', async () => {
    renderPage()
    await screen.findByText('wangq')
    expect(usersApi.listUsersWithProfile).toHaveBeenCalledTimes(1)
    // the 姓名 column: display name ABOVE the account (two cells, one column)
    expect(within(rowOf('wangq')).getByText('王倩')).toBeInTheDocument()
    expect(within(rowOf('wangq')).getByText('wangq')).toBeInTheDocument()
    // role chips FROM THE ROW - and admin's render even though the session user is admin
    expect(within(rowOf('wangq')).getByText('系统管理员')).toBeInTheDocument()
    expect(within(rowOf('wangq')).getByText('放射科')).toBeInTheDocument()
    expect(within(rowOf('wangq')).getByText('138****1111')).toBeInTheDocument()
    expect(within(rowOf('wangq')).getByText('w***@example.com')).toBeInTheDocument()
  })

  it('renders the requested user-list layout and centers numeric/status cells', async () => {
    vi.mocked(usersApi.listUsersWithProfile).mockResolvedValue([
      ADMIN,
      { ...WANG, createBy: 1 },
      LI,
    ])
    renderPage()
    await screen.findByText('wangq')

    const headers = Array.from(document.querySelectorAll('.ant-table-thead th'))
      .map((cell) => cell.textContent)
    expect(headers).toEqual([
      '用户', '启用', '角色', '科室', '职称', '手机号', '邮箱', '创建人 / 创建时间', '操作',
    ])

    const wangRow = rowOf('wangq')
    expect(within(wangRow).getByRole('cell', { name: '138****1111' }).style.textAlign).toBe('center')
    const enableCell = within(wangRow).getByRole('switch').closest('td')
    expect(enableCell?.style.textAlign).toBe('center')
    const roleTag = wangRow.querySelector<HTMLElement>('.ant-tag')
    expect(roleTag?.style.fontSize).toBe('18px')
    // The creator name resolves from the same authoritative user list.
    expect(within(wangRow).getAllByText('系统管理员').length).toBe(2)
    expect(within(wangRow).getByText('2026-11-04 09:00:00')).toBeInTheDocument()
  })

  it('falls back to the creator UID when the creator is absent from the live list', async () => {
    const orphanCreator = user({ uid: 9, username: 'orphan', name: '孤儿创建用户', createBy: 4242 })
    vi.mocked(usersApi.listUsersWithProfile).mockResolvedValue([orphanCreator])
    renderPage()
    await screen.findByText('orphan')
    expect(within(rowOf('orphan')).getByText('UID 4242')).toBeInTheDocument()
  })

  it('orders the operation verbs 重置密码 / 修改 / 删除 and keeps hover hints', async () => {
    renderPage()
    await screen.findByText('wangq')

    const actionCell = rowOf('wangq').querySelector('td:last-child') as HTMLElement
    const actionNames = Array.from(actionCell.querySelectorAll('button'))
      .map((button) => button.getAttribute('aria-label'))
    expect(actionNames).toEqual(['重置密码', '修改', '删除'])
    await userEvent.setup().hover(within(rowOf('wangq')).getByRole('button', { name: '重置密码' }))
    const tooltip = await screen.findByText('重置密码')
    expect(tooltip.closest('.ant-tooltip-inner')).toHaveStyle({ fontSize: '18px' })
  })

  it('renders dict labels for the title code and falls back to the raw code', async () => {
    renderPage()
    await screen.findByText('wangq')
    // 100503 -> 主治医师 (dict-driven); gender has no list column (form-only)
    expect(within(rowOf('wangq')).getByText('主治医师')).toBeInTheDocument()
    // a code the dict lacks -> the RAW code renders (never a blank cell)
    const odd = user({ uid: 9, username: 'oddc', name: '怪码用户', title: 999902 })
    vi.mocked(usersApi.listUsersWithProfile).mockResolvedValue([odd])
    await userEvent.setup().click(screen.getByRole('button', { name: '刷新列表' }))
    await screen.findByText('oddc')
    expect(within(rowOf('oddc')).getByText('999902')).toBeInTheDocument()
  })

  it('renders an empty dash for unbound values (never an error, never a blank)', async () => {
    const bare = user({ uid: 9, username: 'barec', name: '裸用户' })
    vi.mocked(usersApi.listUsersWithProfile).mockResolvedValue([bare])
    renderPage()
    await screen.findByText('barec')
    const row = rowOf('barec')
    // no roleNames -> no chip; the unbound cells render the dash
    expect(within(row).queryByText('未提供')).not.toBeInTheDocument()
    const dashes = within(row).getAllByText('—')
    expect(dashes.length).toBeGreaterThanOrEqual(4)
  })

  it('a failed dict fetch degrades with one message and never blocks the list', async () => {
    vi.mocked(dictsApi.fetchDictItemsByName).mockRejectedValue(new ApiError('B0005', '查询失败'))
    renderPage()
    await screen.findByText('wangq')
    // the list still renders; title codes fall back to raw
    expect(await screen.findByText('100503')).toBeInTheDocument()
  })

  it('paginates client-side: one request, then page switches slice the list', async () => {
    const many = Array.from({ length: 22 }, (_, index) =>
      user({ uid: index + 1, username: `user${index + 1}`, name: `同事${index + 1}` }),
    )
    vi.mocked(usersApi.listUsersWithProfile).mockResolvedValue(many)
    const user_ = userEvent.setup()
    renderPage()
    await screen.findByText('user1')

    expect(screen.getByText('共 22 条')).toBeInTheDocument()
    expect(screen.queryByText('user21')).not.toBeInTheDocument()

    await user_.click(screen.getByText('2'))
    await screen.findByText('user21')
    expect(screen.queryByText('user1')).not.toBeInTheDocument()
    expect(usersApi.listUsersWithProfile).toHaveBeenCalledTimes(1)
  })

  it('the 查询 control filters by username/name and re-issues the read (spec: re-queries on filter)', async () => {
    const user_ = userEvent.setup()
    renderPage()
    await screen.findByText('admin')

    await user_.type(screen.getByLabelText('用户名 / 姓名'), 'wang')
    await user_.click(screen.getByRole('button', { name: /查询/ }))

    expect(screen.getByText('wangq')).toBeInTheDocument()
    expect(screen.queryByText('lwb')).not.toBeInTheDocument()
    expect(screen.getByText('共 1 条')).toBeInTheDocument()
    await waitFor(() => expect(usersApi.listUsersWithProfile).toHaveBeenCalledTimes(2))
  })
})

// ===================== create dialog (spec: Create dialog carries the profile form) =====================

describe('users page - create (spec: Create dialog carries the profile form)', () => {
  it('opens with the full profile form and submits username/password/name/roles + profile fields', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.listUsersWithProfile)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([ADMIN, WANG, LI, user({ uid: 4, username: 'chenz', name: '陈志远' })])
    renderPage()
    await screen.findByText('admin')

    await user_.click(screen.getByRole('button', { name: /新建用户/ }))
    const dialog = await screen.findByRole('dialog', { name: '新建用户' })
    // the documented field set (spec: the profile form in both dialogs)
    for (const label of ['登录账号', '姓名', '手机号', '邮箱', '性别', '职称', '所属科室', '角色', '检查类型', '审核权限', '初始密码']) {
      expect(within(dialog).getByLabelText(label)).toBeInTheDocument()
    }
    // the signature control: its Form.Item label renders, and the control carries
    // its own aria labels (the upload trigger / the preview) - a div is not
    // natively labellable, the association goes through the control's own labels.
    expect(within(dialog).getByText('签名')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('上传签名')).toBeInTheDocument()

    await user_.type(within(dialog).getByLabelText('登录账号'), 'chenz')
    await user_.type(within(dialog).getByLabelText('姓名'), '陈志远')
    await user_.type(within(dialog).getByLabelText('初始密码'), 'lumen123')
    await user_.type(within(dialog).getByLabelText('手机号'), '13900002222')
    await user_.click(within(dialog).getByLabelText('角色'))
    await user_.click(await screen.findByText('系统管理员', { selector: '.ant-select-item-option-content' }))
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() =>
      expect(usersApi.createUser).toHaveBeenCalledWith(
        expect.objectContaining({ username: 'chenz', name: '陈志远', roleIds: [1], phone: '13900002222' }),
      ),
    )
    await expectDialogClosed()
    await waitFor(() => expect(usersApi.listUsersWithProfile).toHaveBeenCalledTimes(2))
  })

  it('shows the backend duplicate-username msg on the form and keeps the dialog open', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.createUser).mockRejectedValue(new ApiError('A0111', '用户名已存在'))
    renderPage()
    await screen.findByText('admin')

    await user_.click(screen.getByRole('button', { name: /新建用户/ }))
    const dialog = await screen.findByRole('dialog', { name: '新建用户' })
    await user_.type(within(dialog).getByLabelText('登录账号'), 'admin')
    await user_.type(within(dialog).getByLabelText('姓名'), '重名同事')
    await user_.type(within(dialog).getByLabelText('初始密码'), 'lumen123')
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    const fieldError = await within(dialog).findByText('用户名已存在')
    expect(fieldError).toBeInTheDocument()
    expect(document.querySelector('.ant-drawer-open')).not.toBeNull()
  })
})

// ===================== edit (spec: Edit opens prefilled / Edit failure keeps the dialog) =====================

describe('users page - edit (spec: Edit opens prefilled with detail / Edit failure keeps the dialog)', () => {
  it('opens prefilled from the detail endpoint with 登录账号 disabled and submits the update', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.listUsersWithProfile)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([ADMIN, { ...WANG, phone: '13900003333' }, LI])
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('button', { name: '修改' }))
    const dialog = await screen.findByRole('dialog', { name: '编辑用户' })
    expect(usersApi.getUserDetail).toHaveBeenCalledWith(2)
    // the prefill: username visible but DISABLED (immutable), fields echo the detail
    expect(within(dialog).getByLabelText('登录账号')).toHaveValue('wangq')
    expect(within(dialog).getByLabelText('登录账号')).toBeDisabled()
    expect(within(dialog).getByLabelText('姓名')).toHaveValue('王倩')
    expect(within(dialog).getByLabelText('手机号')).toHaveValue('13800001111')

    await user_.clear(within(dialog).getByLabelText('手机号'))
    await user_.type(within(dialog).getByLabelText('手机号'), '13900003333')
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() => expect(usersApi.updateUser).toHaveBeenCalled())
    const [id, body] = vi.mocked(usersApi.updateUser).mock.calls[0]
    expect(id).toBe(2)
    expect(body).toEqual(expect.objectContaining({ name: '王倩', phone: '13900003333', roleIds: [1] }))
    await expectDialogClosed()
  })

  it('the modalitys CSV splits to the multi-select and joins back on submit', async () => {
    const user_ = userEvent.setup()
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('button', { name: '修改' }))
    const dialog = await screen.findByRole('dialog', { name: '编辑用户' })
    // the CSV '2,3' (the unified 设备类型 dict CODES, CT=2/MR=3) prefills as CT
    // and MR. The label resolves to the inner search INPUT (no text content ever)
    // - the selected items render as sibling spans inside .ant-select, so assert
    // on the ROOT.
    const multi = within(dialog).getByLabelText('检查类型').closest('.ant-select') as HTMLElement
    expect(multi).toHaveTextContent('CT')
    expect(multi).toHaveTextContent('MR')

    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(usersApi.updateUser).toHaveBeenCalled())
    const body = vi.mocked(usersApi.updateUser).mock.calls[0][1]
    // the wire shape is the dict-CODE CSV (the data spec's storage shape)
    expect(body.modalitys).toBe('2,3')
  })

  it('an update rejection keeps the dialog open with the backend msg on the form', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.updateUser).mockRejectedValue(new ApiError('A0400', 'department id 99 does not exist'))
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('button', { name: '修改' }))
    const dialog = await screen.findByRole('dialog', { name: '编辑用户' })
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    expect(await screen.findByText('department id 99 does not exist')).toBeInTheDocument()
    expect(document.querySelector('.ant-drawer-open')).not.toBeNull()
    expect(usersApi.listUsersWithProfile).toHaveBeenCalledTimes(1)
  })
})

// ===================== delete (spec: Delete confirms then removes / Protected-account rejection) =====================

describe('users page - delete (spec: Delete confirms then removes / Protected-account rejection)', () => {
  it('confirms with the Popconfirm, calls DELETE and refreshes the list', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.listUsersWithProfile)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([ADMIN, LI])
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('button', { name: '删除' }))
    const confirm = await screen.findByRole('tooltip')
    await user_.click(within(confirm).getByRole('button', { name: /确\s*定|OK|删/ }))

    await waitFor(() => expect(usersApi.deleteUser).toHaveBeenCalledWith(2))
    await waitFor(() => expect(usersApi.listUsersWithProfile).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByText('wangq')).not.toBeInTheDocument())
  })

  it('a protected-account rejection surfaces the backend msg as a toast and the row stays', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.deleteUser).mockRejectedValue(new ApiError('A0400', 'the current account cannot delete itself'))
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('button', { name: '删除' }))
    const confirm = await screen.findByRole('tooltip')
    await user_.click(within(confirm).getByRole('button', { name: /确\s*定|OK|删/ }))

    expect(await screen.findByText('the current account cannot delete itself')).toBeInTheDocument()
    expect(screen.getByText('wangq')).toBeInTheDocument()
  })
})

// ===================== reset + enable (spec: Password and enable actions) =====================

describe('users page - password reset (spec: Password and enable actions)', () => {
  it('resets via PUT /users/{id}/password and the re-list echoes mustChangePassword', async () => {
    const user_ = userEvent.setup()
    const adminAfterReset = { ...ADMIN, mustChangePassword: 1 }
    const wangAfterReset = { ...WANG, mustChangePassword: 1 }
    vi.mocked(usersApi.listUsersWithProfile)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([adminAfterReset, wangAfterReset, LI])
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('button', { name: '重置密码' }))
    const dialog = await screen.findByRole('dialog', { name: '重置密码' })
    await user_.type(within(dialog).getByLabelText('新密码'), 'Reset2026!')
    await user_.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() => expect(usersApi.resetPassword).toHaveBeenCalledWith(2, 'Reset2026!'))
    await expectDialogClosed()
    await waitFor(() => expect(within(rowOf('wangq')).getByText('首次登录需改密')).toBeInTheDocument())
  })
})

describe('users page - enable toggle (spec: Password and enable actions)', () => {
  it('toggles with PUT /users/{id}/enable and the switch follows the re-listed state', async () => {
    const user_ = userEvent.setup()
    vi.mocked(usersApi.listUsersWithProfile)
      .mockResolvedValueOnce([ADMIN, WANG, LI])
      .mockResolvedValue([ADMIN, { ...WANG, enable: 0 }, LI])
    renderPage()
    await screen.findByText('wangq')

    await user_.click(within(rowOf('wangq')).getByRole('switch'))
    await waitFor(() => expect(usersApi.enableUser).toHaveBeenCalledWith(2, 0))
    await waitFor(() => expect(within(rowOf('wangq')).getByRole('switch')).not.toBeChecked())
  })
})

// ===================== perms (spec: Edit and delete follow the new button permissions) =====================

describe('users page - perms gating (spec: Edit and delete follow the new button permissions)', () => {
  it('hides 修改/删除 without their button perms (the other affordances unchanged)', async () => {
    useAuthStore.getState().setSession({ perms: ['system:user:list', 'system:user:create', 'system:user:reset-pwd', 'system:user:enable'] })
    renderPage()
    await screen.findByText('admin')

    expect(within(rowOf('wangq')).queryByRole('button', { name: '修改' })).not.toBeInTheDocument()
    expect(within(rowOf('wangq')).queryByRole('button', { name: '删除' })).not.toBeInTheDocument()
    expect(within(rowOf('wangq')).getByRole('button', { name: '重置密码' })).toBeInTheDocument()
    expect(within(rowOf('wangq')).getByRole('switch')).toBeEnabled()
  })

  it('reveals 修改/删除 when the session holds the seeded perms', async () => {
    renderPage()
    await screen.findByText('admin')
    expect(within(rowOf('wangq')).getByRole('button', { name: '修改' })).toBeInTheDocument()
    expect(within(rowOf('wangq')).getByRole('button', { name: '删除' })).toBeInTheDocument()
  })
})

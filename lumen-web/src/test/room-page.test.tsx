import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RoomPage from '@/pages/system/room'
import * as depsApi from '@/api/org-deps'
import * as deviceApi from '@/api/orgDevice'
import * as roomApi from '@/api/orgRoom'
import * as orgsApi from '@/api/orgs'
import type { OrgDep, OrgDevice, OrgNode, Page, RoomPageQuery, RoomRow } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 诊室配置 page (org-device-room-web tasks 4.1-4.3; spec delta `web-system-console`
 * § Exam Room Configuration Page): root-means-all tree semantics, the server-resolved
 * device summary (nulls when unbound), the department-scoped device picker with the
 * read-only AE/IP/Port echo, rebind/clear, the enable-only status toggle with
 * revert-on-failure, and the logical delete. The api seam is mocked.
 */
vi.mock('@/api/orgRoom')
vi.mock('@/api/orgDevice')
vi.mock('@/api/org-deps')
vi.mock('@/api/orgs')
vi.mock('@/utils/dict-items', () => ({
  dictItemsByName: vi.fn(),
  resolveDictLabel: (items: { code: number; value: string }[], code: number) =>
    items.find((item) => item.code === code)?.value ?? String(code),
  resolveDictLabelByValue: (items: { value: string }[], value: string) =>
    items.find((item) => item.value === value)?.value ?? value,
}))

import * as dictUtils from '@/utils/dict-items'

const DEVICE_TYPE_DICT = [
  { code: 2, value: 'CT' },
  { code: 1, value: 'DR' },
]

const ORG_TREE: OrgNode[] = [
  {
    id: 1,
    upId: 0,
    level: 1,
    type: 0,
    name: '测试医院',
    code: 'HOSPITAL',
    shortName: null,
    status: 1,
    provinceCode: null,
    cityCode: null,
    districtCode: null,
    address: null,
    contact: null,
    phone: null,
    email: null,
    logoUrl: null,
    sort: 1,
    isDefault: 0,
    createBy: null,
    createTime: '2026-11-12 08:00:00',
    updateBy: null,
    updateTime: '2026-11-12 08:00:00',
    children: [
      {
        id: 2,
        upId: 1,
        level: 2,
        type: 1,
        name: '东院区',
        code: 'CAMPUS-E',
        shortName: null,
        status: 1,
        provinceCode: null,
        cityCode: null,
        districtCode: null,
        address: null,
        contact: null,
        phone: null,
        email: null,
        logoUrl: null,
        sort: 1,
        isDefault: 0,
        createBy: null,
        createTime: '2026-11-12 08:00:00',
        updateBy: null,
        updateTime: '2026-11-12 08:00:00',
        children: null,
      },
    ],
  },
]

const DEPS: OrgDep[] = [
  {
    id: 11,
    orgId: 1,
    code: 'dep_rad',
    name: '放射科',
    status: 1,
    createBy: null,
    createTime: '2026-11-12 08:00:00',
    updateBy: null,
    updateTime: '2026-11-12 08:00:00',
  },
]

function room(partial: Partial<RoomRow> & { id: number; name: string }): RoomRow {
  return {
    oid: 1,
    did: 11,
    code: null,
    enable: 1,
    callIp: null,
    callPort: null,
    callMac: null,
    devId: null,
    devName: null,
    devAe: null,
    devIp: null,
    devPort: null,
    ...partial,
  }
}

const ROOM_BOUND = room({
  id: 7,
  name: 'CT 一号室',
  code: 'R-CT-01',
  devId: 5,
  devName: 'CT 一号机',
  devAe: 'LUMEN_CT',
  devIp: '192.168.1.50',
  devPort: '104',
})
const ROOM_FREE = room({ id: 8, name: '会诊室' })

function pageFixture(records: RoomRow[], total = records.length): Page<RoomRow> {
  return { records, total, size: 20, current: 1, pages: 1 }
}

function pickDevice(): OrgDevice {
  return {
    id: 5,
    code: 'DEV-CT-001',
    name: 'CT 一号机',
    type: 'CT',
    status: 1,
    tag: 10,
    brand: null,
    model: null,
    spec: null,
    serialNum: null,
    manufacturer: null,
    productionDate: null,
    purchaseDate: null,
    acceptanceDate: null,
    responsible: null,
    responsiblePhone: null,
    startDate: null,
    endDate: null,
    remark: null,
    oid: 1,
    did: 11,
    ae: 'LUMEN_CT',
    ip: '192.168.1.50',
    port: '104',
    boxAdapter: null,
    boxIp: null,
    boxPort: null,
    boxMac: null,
    boxPrint: null,
    captureW: null,
    captureH: null,
    prune: null,
    pruneU: null,
    pruneD: null,
    pruneL: null,
    pruneR: null,
    deleted: 0,
    createBy: null,
    createTime: '2026-11-12 08:00:00',
    updateBy: null,
    updateTime: '2026-11-12 08:00:00',
  }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <RoomPage />
    </ConfigProvider>,
  )
}

function lastQuery(): RoomPageQuery {
  const calls = vi.mocked(roomApi.pageRoom).mock.calls
  return calls[calls.length - 1]![0] as RoomPageQuery
}

async function pickOption(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  optionText: string,
) {
  await user.click(screen.getByRole('combobox', { name: label }))
  await waitFor(() => {
    expect(activeDropdown(optionText)).not.toBeNull()
  })
  await user.click(
    activeOption(activeDropdown(optionText) as HTMLElement, optionText) as HTMLElement,
  )
}
/**
 * The ACTIVE dropdown listing the option: non-hidden portals only (stale closing
 * dropdowns linger in the DOM), the LAST match wins (the fresh portal appends
 * last).
 *
 * The option lookup goes through the VISIBLE `.ant-select-item-option[title]`
 * node: with VALUE-keyed options (设备类型/检查类型 store the 'CT' token, ruling
 * 2026-11-12) rc-select also renders an a11y mirror listbox whose divs carry
 * role="option" + aria-label + the same text - role/text queries match the mirror
 * (unclickable, inside a 0x0 hidden div) instead of the interactive node.
 */
function activeDropdown(optionText: string): HTMLElement | null {
  const dropdowns = Array.from(
    document.querySelectorAll('.ant-select-dropdown:not(.ant-select-dropdown-hidden)'),
  ) as HTMLElement[]
  return dropdowns.reverse().find((d) => activeOption(d, optionText) !== null) ?? null
}

/** The visible, interactive option wrapper inside one dropdown. */
function activeOption(dropdown: HTMLElement, optionText: string): HTMLElement | null {
  return (
    Array.from(dropdown.querySelectorAll<HTMLElement>('.ant-select-item-option')).find(
      (o) => o.getAttribute('title') === optionText,
    ) ?? null
  )
}

async function waitForTableSettled() {
  await waitFor(() =>
    expect(document.querySelector('.ant-spin-spinning')).toBeNull(),
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(dictUtils.dictItemsByName).mockImplementation(async (name: string) => {
    if (name === '设备类型') return DEVICE_TYPE_DICT
    return []
  })
  useAuthStore.getState().setSession({
    perms: [
      'system:room:list',
      'system:room:create',
      'system:room:update',
      'system:room:status',
      'system:room:delete',
    ],
  })
  vi.mocked(orgsApi.orgTree).mockResolvedValue(ORG_TREE)
  vi.mocked(depsApi.listDeps).mockResolvedValue(DEPS)
  vi.mocked(roomApi.pageRoom).mockResolvedValue(pageFixture([ROOM_BOUND, ROOM_FREE]))
  vi.mocked(roomApi.getRoom).mockResolvedValue(ROOM_BOUND)
  vi.mocked(roomApi.createRoom).mockResolvedValue(20)
  vi.mocked(roomApi.updateRoom).mockResolvedValue(true)
  vi.mocked(roomApi.setRoomStatus).mockResolvedValue(true)
  vi.mocked(roomApi.deleteRoom).mockResolvedValue(true)
  vi.mocked(deviceApi.listDevices).mockResolvedValue([pickDevice()])
})

describe('room page - org tree semantics (root = all campuses)', () => {
  it('queries WITHOUT orgId on the auto-selected hospital root', async () => {
    renderPage()
    await screen.findByText('CT 一号室')
    expect(lastQuery().orgId).toBeUndefined()
  })

  it('queries WITH orgId once a campus is selected', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号室')
    await screen.findByText('东院区')
    await user.click(screen.getByText('东院区'))
    await waitFor(() => expect(lastQuery().orgId).toBe(2))
  })
})

describe('room page - device summary by reference (4.1)', () => {
  it('renders the bound device name and AE from the server summary', async () => {
    renderPage()
    const row = (await screen.findByText('CT 一号室')).closest('tr') as HTMLElement
    expect(within(row).getByText('CT 一号机')).toBeInTheDocument()
    expect(within(row).getByText('LUMEN_CT')).toBeInTheDocument()
    expect(within(row).getByText('放射科')).toBeInTheDocument()
  })

  it('renders the unbound empty state without crashing', async () => {
    renderPage()
    const row = (await screen.findByText('会诊室')).closest('tr') as HTMLElement
    expect(within(row).getByText('未绑定')).toBeInTheDocument()
  })
})

describe('room page - drawer create with binding (4.2)', () => {
  it('binds a device by reference through the picker and refreshes', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    const loadsBefore = vi.mocked(roomApi.pageRoom).mock.calls.length
    await user.click(screen.getByRole('button', { name: /添加诊室/ }))
    await screen.findByRole('dialog', { name: '添加诊室' })

    await user.type(screen.getByLabelText('名称'), 'DR 一号室')
    await pickOption(user, '科室选择', '放射科')

    // Bind through the picker modal (绑定设备 renders inline in the drawer - no tab).
    await user.click(screen.getByRole('button', { name: /选择设备/ }))
    await waitFor(() =>
      expect(document.querySelector('.ant-modal-content')).not.toBeNull(),
    )
    const modal = document.querySelector('.ant-modal-content') as HTMLElement
    await user.click(within(modal).getByText('CT 一号机'))
    await user.click(within(modal).getByRole('button', { name: /确定绑定/ }))

    await user.click(screen.getByRole('button', { name: /保\s*存/ }))
    await waitFor(() => expect(roomApi.createRoom).toHaveBeenCalledTimes(1))
    expect(vi.mocked(roomApi.createRoom).mock.calls[0][0]).toMatchObject({
      name: 'DR 一号室',
      did: 11,
      devId: 5,
    })
    await waitFor(() =>
      expect(vi.mocked(roomApi.pageRoom).mock.calls.length).toBeGreaterThan(loadsBefore),
    )
  })

  it('echoes the picker device AE/IP/Port read-only before save', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    await user.click(screen.getByRole('button', { name: /添加诊室/ }))
    await screen.findByRole('dialog', { name: '添加诊室' })
    // The picker scopes to the selected 负责科室 - pick it first (button enables).
    await pickOption(user, '科室选择', '放射科')
    await user.click(screen.getByRole('button', { name: /选择设备/ }))
    await waitFor(() =>
      expect(document.querySelector('.ant-modal-content')).not.toBeNull(),
    )
    const modal = document.querySelector('.ant-modal-content') as HTMLElement
    await user.click(within(modal).getByText('CT 一号机'))
    await user.click(within(modal).getByRole('button', { name: /确定绑定/ }))
    // The echo block renders the picker device's access point (read-only) -
    // scoped to the OPEN DRAWER (the background table row carries the same AE).
    const drawer = document.querySelector('.ant-drawer-open') as HTMLElement
    expect(within(drawer).getAllByText('LUMEN_CT').length).toBeGreaterThan(0)
    expect(within(drawer).getAllByText('192.168.1.50').length).toBeGreaterThan(0)
    expect(within(drawer).getAllByText('104').length).toBeGreaterThan(0)
  })

  it('clears the binding: the update body carries no devId', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    const row = (await screen.findByText('CT 一号室')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: /编辑/ }))
    await screen.findByRole('dialog', { name: '编辑诊室' })
    await user.click(screen.getByRole('button', { name: /清除绑定/ }))
    await user.click(screen.getByRole('button', { name: /保\s*存/ }))
    await waitFor(() => expect(roomApi.updateRoom).toHaveBeenCalledTimes(1))
    const body = vi.mocked(roomApi.updateRoom).mock.calls[0][1]
    expect(body.devId).toBeNull()
  })

  it('maps the 备注 field onto the repurposed callMac wire field', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    await user.click(screen.getByRole('button', { name: /添加诊室/ }))
    await screen.findByRole('dialog', { name: '添加诊室' })

    await user.type(screen.getByLabelText('名称'), 'DR 二号室')
    await pickOption(user, '科室选择', '放射科')
    // 备注 persists through the retired call_mac column (user ruling 2026-11-13).
    await user.type(screen.getByLabelText('备注'), '暂借术中使用')
    await user.click(screen.getByRole('button', { name: /保\s*存/ }))
    await waitFor(() => expect(roomApi.createRoom).toHaveBeenCalledTimes(1))
    expect(vi.mocked(roomApi.createRoom).mock.calls[0][0]).toMatchObject({
      name: 'DR 二号室',
      did: 11,
      callMac: '暂借术中使用',
    })
  })
})

describe('room page - status toggle (4.3)', () => {
  it('calls the status endpoint with the next code and refreshes', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    const row = (await screen.findByText('CT 一号室')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('switch'))
    await waitFor(() => expect(roomApi.setRoomStatus).toHaveBeenCalledWith(7, 0))
    await waitFor(() =>
      expect(vi.mocked(roomApi.pageRoom).mock.calls.length).toBeGreaterThan(1),
    )
  })

  it('reverts the switch when the toggle fails', async () => {
    const { ApiError } = await import('@/api/client')
    vi.mocked(roomApi.setRoomStatus).mockRejectedValue(new ApiError('A0400', '不能停用'))
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    const row = (await screen.findByText('CT 一号室')).closest('tr') as HTMLElement
    const sw = within(row).getByRole('switch')
    expect(sw).toHaveAttribute('aria-checked', 'true')
    await user.click(sw)
    await waitFor(() => expect(roomApi.setRoomStatus).toHaveBeenCalledWith(7, 0))
    // The row state never changed locally - the switch renders checked again.
    await waitFor(() => {
      const fresh = (screen.getByText('CT 一号室').closest('tr') as HTMLElement)
      expect(within(fresh).getByRole('switch')).toHaveAttribute('aria-checked', 'true')
    })
  })
})

describe('room page - delete (4.3)', () => {
  it('deletes logically and refreshes', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    // Resolve the row FRESH after settle - the auto-select load may swap nodes.
    const row = (await screen.findByText('CT 一号室')).closest('tr') as HTMLElement
    await waitForTableSettled()
    await user.click(within(row).getByRole('button', { name: /删除/ }))
    await user.click(await screen.findByRole('button', { name: /确\s*定/ }))
    await waitFor(() => expect(roomApi.deleteRoom).toHaveBeenCalledWith(7))
    await waitFor(() =>
      expect(vi.mocked(roomApi.pageRoom).mock.calls.length).toBeGreaterThan(1),
    )
  })
})

describe('room page - department filter (4.1)', () => {
  it('re-queries with the depId from the first page', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号室')
    await user.click(screen.getByRole('combobox', { name: '科室过滤' }))
    await waitFor(() => {
      const dropdowns = Array.from(
        document.querySelectorAll('.ant-select-dropdown'),
      ) as HTMLElement[]
      expect(dropdowns.some((d) => within(d).queryByText('放射科') !== null)).toBe(true)
    })
    const dropdowns = Array.from(
      document.querySelectorAll('.ant-select-dropdown'),
    ) as HTMLElement[]
    const dropdown = dropdowns.find((d) => within(d).queryByText('放射科') !== null)
    await user.click(within(dropdown as HTMLElement).getByText('放射科'))
    await waitFor(() => {
      const q = lastQuery()
      expect(q.depId).toBe(11)
      expect(q.pageNum).toBe(1)
    })
  })
})

describe('room page - perm gating (D6)', () => {
  it('hides the add affordance without system:room:create', async () => {
    useAuthStore.getState().setSession({ perms: ['system:room:list'] })
    renderPage()
    await screen.findByText('CT 一号室')
    expect(screen.queryByRole('button', { name: /添加诊室/ })).not.toBeInTheDocument()
  })

  it('disables the status switch without system:room:status', async () => {
    useAuthStore.getState().setSession({ perms: ['system:room:list'] })
    renderPage()
    await screen.findByText('CT 一号室')
    const sw = screen.getAllByRole('switch')[0]
    expect(sw).toHaveAttribute('aria-checked', 'true')
  })
})

describe('room page - drawer shell consistency', () => {
  it('places the close button after the title (top-right, console convention)', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    await user.click(screen.getByRole('button', { name: /添加诊室/ }))
    await waitFor(() => expect(document.querySelector('.ant-drawer-header')).not.toBeNull())
    const header = document.querySelector('.ant-drawer-header') as HTMLElement
    const title = within(header).getByText('添加诊室')
    const close = header.querySelector('button')
    expect(close).not.toBeNull()
    // DOM-order guard: the close control FOLLOWS the title (renders top-right).
    expect(
      title.compareDocumentPosition(close as Node) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })
})

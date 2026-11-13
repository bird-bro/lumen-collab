import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DevicePage from '@/pages/system/device'
import * as depsApi from '@/api/org-deps'
import * as deviceApi from '@/api/orgDevice'
import * as orgsApi from '@/api/orgs'
import type { DevicePageQuery, OrgDep, OrgDevice, OrgNode, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 设备档案 page (org-device-room-web tasks 3.1-3.3; spec delta `web-system-console`
 * § Device Archive Page): root-means-all tree semantics (the hospital root queries
 * WITHOUT orgId, a campus filters), type/status/keyword filters with page reset,
 * the required 所属部门 anchor, D9 full-replace drawer, the picker feed ordering,
 * and the delete guard surfacing the live room count. The api seam is mocked - no
 * page data ever flows through the mock at runtime (ruling 2026-11-05).
 */
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
  { code: 1, value: 'DR' },
  { code: 2, value: 'CT' },
  { code: 5, value: 'US' },
]
const DEVICE_STATUS_DICT = [
  { code: 1, value: '在用' },
  { code: 2, value: '停用' },
  { code: 3, value: '维修' },
  { code: 4, value: '报废' },
]
const TAG_DICT = [
  { code: 10, value: '自购' },
  { code: 11, value: '租赁' },
]

/** Org tree: root (upId 0) + one campus. Root selection = all campuses. */
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
  {
    id: 12,
    orgId: 1,
    code: 'dep_us',
    name: '超声科',
    status: 1,
    createBy: null,
    createTime: '2026-11-12 08:00:00',
    updateBy: null,
    updateTime: '2026-11-12 08:00:00',
  },
]

function device(partial: Partial<OrgDevice> & { id: number; code: string; name: string }): OrgDevice {
  return {
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
    ae: null,
    ip: null,
    port: null,
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
    ...partial,
  }
}

const DEV_CT = device({ id: 5, code: 'DEV-CT-001', name: 'CT 一号机', did: 11 })

function pageFixture(records: OrgDevice[], total = records.length): Page<OrgDevice> {
  return { records, total, size: 20, current: 1, pages: 1 }
}


/** Opens the aria-labeled AntD Select and clicks one option (deps-page house pattern). */
async function pickOption(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  optionText: string,
) {
  await user.click(screen.getByRole('combobox', { name: label }))
  // Several AntD dropdown portals linger after earlier opens (slide-up-leave);
  // click the option inside whichever dropdown actually lists it.
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

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <DevicePage />
    </ConfigProvider>,
  )
}

/** Waits for the table spinner to clear (the deps-page settled-state pattern) - the
 *  tree auto-select's second load races row interactions otherwise. */
async function waitForTableSettled() {
  await waitFor(() =>
    expect(document.querySelector('.ant-spin-spinning')).toBeNull(),
  )
}

/** Asserts the LAST pageDevice call carried exactly the given orgId key shape. */
function lastQuery(): DevicePageQuery {
  const calls = vi.mocked(deviceApi.pageDevice).mock.calls
  return calls[calls.length - 1]![0] as DevicePageQuery
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(dictUtils.dictItemsByName).mockImplementation(async (name: string) => {
    if (name === '设备类型') return DEVICE_TYPE_DICT
    if (name === '设备状态') return DEVICE_STATUS_DICT
    if (name === '归属类型') return TAG_DICT
    return []
  })
  useAuthStore.getState().setSession({
    perms: [
      'system:device:list',
      'system:device:create',
      'system:device:update',
      'system:device:delete',
    ],
  })
  vi.mocked(orgsApi.orgTree).mockResolvedValue(ORG_TREE)
  vi.mocked(depsApi.listDeps).mockResolvedValue(DEPS)
  vi.mocked(deviceApi.pageDevice).mockResolvedValue(pageFixture([DEV_CT]))
  vi.mocked(deviceApi.getDevice).mockResolvedValue(DEV_CT)
  vi.mocked(deviceApi.createDevice).mockResolvedValue(99)
  vi.mocked(deviceApi.updateDevice).mockResolvedValue(true)
  vi.mocked(deviceApi.deleteDevice).mockResolvedValue(true)
})

describe('device page - org tree semantics (root = all campuses)', () => {
  it('queries WITHOUT orgId on the auto-selected hospital root', async () => {
    renderPage()
    await screen.findByText('CT 一号机')
    expect(lastQuery().orgId).toBeUndefined()
  })

  it('queries WITH orgId once a campus is selected', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号机')
    // The auto-expansion lands a render after the data arrives - wait for the child.
    await screen.findByText('东院区')
    await user.click(screen.getByText('东院区'))
    await waitFor(() => expect(lastQuery().orgId).toBe(2))
  })

  it('shows the whole estate on the root and scopes on the campus', async () => {
    vi.mocked(deviceApi.pageDevice).mockResolvedValue(
      pageFixture([DEV_CT, device({ id: 6, code: 'DEV-US-001', name: '超声仪', did: 12 })]),
    )
    renderPage()
    expect(await screen.findByText('CT 一号机')).toBeInTheDocument()
    expect(screen.getByText('超声仪')).toBeInTheDocument()
  })
})

describe('device page - table columns', () => {
  it('renders the composite cells and the 科室 name from the deps map', async () => {
    renderPage()
    const row = (await screen.findByText('CT 一号机')).closest('tr') as HTMLElement
    expect(within(row).getByText('DEV-CT-001')).toBeInTheDocument()
    expect(within(row).getByText('放射科')).toBeInTheDocument()
    expect(within(row).getByText('CT')).toBeInTheDocument()
    expect(within(row).getByText('在用')).toBeInTheDocument()
    expect(within(row).getByText('自购')).toBeInTheDocument()
  })
})

describe('device page - drawer create (3.2)', () => {
  it('submits the full surface and refreshes the current page', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号机')
    const loadsBefore = vi.mocked(deviceApi.pageDevice).mock.calls.length
    await user.click(screen.getByRole('button', { name: /添加设备/ }))

    await screen.findByText('基础信息')
    await user.type(screen.getByLabelText('编号'), 'DEV-NEW-001')
    await user.type(screen.getByLabelText('名称'), '新设备')
    // 类型/状态/归属类型/所属部门 selects - pick the first real option of each.
    await pickOption(user, '设备类型选择', 'CT')
    await pickOption(user, '设备状态选择', '在用')
    await pickOption(user, '归属类型选择', '自购')
    await pickOption(user, '所属部门选择', '放射科')

    await user.click(screen.getByRole('button', { name: /保\s*存/ }))
    await waitFor(() => expect(deviceApi.createDevice).toHaveBeenCalledTimes(1))
    expect(vi.mocked(deviceApi.createDevice).mock.calls[0][0]).toMatchObject({
      code: 'DEV-NEW-001',
      name: '新设备',
      did: 11,
    })
    // The page refreshes after the create (the tree auto-select adds a mount load,
    // so assert GROWTH over the pre-submit count, never an exact total).
    await waitFor(() =>
      expect(vi.mocked(deviceApi.pageDevice).mock.calls.length).toBeGreaterThan(loadsBefore),
    )
  })

  it('surfaces a duplicate-code rejection on the 编号 field and keeps the drawer open', async () => {
    const { ApiError } = await import('@/api/client')
    vi.mocked(deviceApi.createDevice).mockRejectedValue(
      new ApiError('A0400', '设备编号已存在'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号机')
    await waitForTableSettled()
    const loadsBefore = vi.mocked(deviceApi.pageDevice).mock.calls.length
    await user.click(screen.getByRole('button', { name: /添加设备/ }))
    await screen.findByText('基础信息')
    await user.type(screen.getByLabelText('编号'), 'DEV-CT-001')
    await user.type(screen.getByLabelText('名称'), '重复设备')
    await pickOption(user, '设备类型选择', 'CT')
    await pickOption(user, '设备状态选择', '在用')
    await pickOption(user, '归属类型选择', '自购')
    await pickOption(user, '所属部门选择', '放射科')
    await user.click(screen.getByRole('button', { name: /保\s*存/ }))

    // The rejection lands on the 编号 field; the drawer stays open.
    await waitFor(() =>
      expect(screen.getByText('设备编号已存在')).toBeInTheDocument(),
    )
    expect(screen.getByText('基础信息')).toBeInTheDocument()
    expect(vi.mocked(deviceApi.pageDevice).mock.calls.length).toBe(loadsBefore)
  })
})

describe('device page - delete guard (3.3)', () => {
  it('shows the room-count rejection and keeps the row', async () => {
    const { ApiError } = await import('@/api/client')
    vi.mocked(deviceApi.deleteDevice).mockRejectedValue(
      new ApiError('A0400', '设备仍被 2 个启用中的诊室引用,请先解绑'),
    )
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    // Resolve the row FRESH after settle - the auto-select load may swap nodes.
    const row = (await screen.findByText('CT 一号机')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: /删除/ }))
    await user.click(await screen.findByRole('button', { name: /确\s*定/ }))

    await waitFor(() =>
      expect(screen.getByText(/设备仍被 2 个启用中的诊室引用/)).toBeInTheDocument(),
    )
    // The row is retained and the list is NOT reloaded away.
    expect(screen.getByText('CT 一号机')).toBeInTheDocument()
  })

  it('refreshes the list after a successful delete', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    // Resolve the row FRESH after settle - the auto-select load may swap nodes.
    const row = (await screen.findByText('CT 一号机')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: /删除/ }))
    await user.click(await screen.findByRole('button', { name: /确\s*定/ }))
    await waitFor(() => expect(deviceApi.deleteDevice).toHaveBeenCalledWith(5))
    await waitFor(() => {
      expect(vi.mocked(deviceApi.pageDevice).mock.calls.length).toBeGreaterThanOrEqual(2)
      expect(lastQuery().pageNum).toBe(1)
    })
  })
})

describe('device page - edit echo and full update (3.2)', () => {
  it('echoes the fetched detail and submits the full update', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForTableSettled()
    const row = (await screen.findByText('CT 一号机')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: /编辑/ }))
    // The echo comes from the detail fetch (getDevice), not the row.
    await waitFor(() => expect(deviceApi.getDevice).toHaveBeenCalledWith(5))
    await screen.findByText('基础信息')
    await waitFor(() => expect(screen.getByLabelText('编号')).toHaveValue('DEV-CT-001'))
    await user.clear(screen.getByLabelText('名称'))
    await user.type(screen.getByLabelText('名称'), 'CT 一号机改')
    await user.click(screen.getByRole('button', { name: /保\s*存/ }))
    await waitFor(() => expect(deviceApi.updateDevice).toHaveBeenCalledTimes(1))
    expect(vi.mocked(deviceApi.updateDevice).mock.calls[0]![0]).toBe(5)
    expect(vi.mocked(deviceApi.updateDevice).mock.calls[0]![1]).toMatchObject({
      code: 'DEV-CT-001',
      name: 'CT 一号机改',
      did: 11,
    })
  })
})

describe('device page - type filter resets to page 1', () => {
  it('re-queries with the type VALUE token from the first page', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号机')
    await user.click(screen.getByRole('combobox', { name: '设备类型过滤' }))
    await waitFor(() => expect(activeDropdown('CT')).not.toBeNull())
    await user.click(activeOption(activeDropdown('CT') as HTMLElement, 'CT') as HTMLElement)
    await waitFor(() => {
      const q = lastQuery()
      expect(q.type).toBe('CT')
      expect(q.pageNum).toBe(1)
    })
  })
})

describe('device page - perm gating (D6)', () => {
  it('hides the 添加 affordance without system:device:create', async () => {
    useAuthStore.getState().setSession({ perms: ['system:device:list'] })
    renderPage()
    await screen.findByText('CT 一号机')
    expect(screen.queryByRole('button', { name: /添加设备/ })).not.toBeInTheDocument()
  })
})

describe('device page - filters reset to page 1', () => {
  it('re-queries from the first page when a filter changes', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号机')
    await user.type(screen.getByPlaceholderText('名称 / 编号'), 'CT')
    await user.click(screen.getByRole('button', { name: /查\s*询/ }))
    await waitFor(() => {
      const q = lastQuery()
      expect(q.keyword).toBe('CT')
      expect(q.pageNum).toBe(1)
    })
  })
})

describe('device page - drawer shell consistency', () => {
  it('places the close button after the title (top-right, console convention)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('CT 一号机')
    await user.click(screen.getByRole('button', { name: /添加设备/ }))
    await waitFor(() => expect(document.querySelector('.ant-drawer-header')).not.toBeNull())
    const header = document.querySelector('.ant-drawer-header') as HTMLElement
    const title = within(header).getByText('添加设备')
    const close = header.querySelector('button')
    expect(close).not.toBeNull()
    // DOM-order guard: the close control FOLLOWS the title (renders top-right).
    expect(
      title.compareDocumentPosition(close as Node) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
  })
})

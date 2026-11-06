import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrgsPage from '@/pages/system/orgs'
import * as orgsApi from '@/api/orgs'
import * as dictsApi from '@/api/dicts'
import * as commonApi from '@/api/common'
import { ApiError } from '@/api/client'
import type { CityDict, OrgNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 机构管理 page rewrite (org-management-redesign-web, tasks 2.1-2.6; spec
 * delta `web-system-console` § Hospital Profile and Campus Table Management /
 * § First-run Organization Profile Completion / § Campus Profile Management).
 *
 * The old generic multi-root tree tests are REPLACED here: the page is now the
 * dictionary-like layout - left hospital profile card + edit action, right
 * campus table (campus-name/status filters, create/edit/delete), no
 * generic root creation, no reparenting/type controls.
 *
 * The api seam is stubbed with `vi.mock` (unit-test seam only - the runtime
 * page always talks to the real backend through the typed client, no mock
 * layer), so every assertion is on the documented client call plus the
 * orgTree() re-query that must follow a successful mutation.
 */
vi.mock('@/api/orgs')
vi.mock('@/api/dicts')
vi.mock('@/api/common')

function orgNode(partial: Partial<OrgNode> & { id: number; name: string }): OrgNode {
  return {
    upId: 0,
    level: 1,
    type: 0,
    code: '',
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
    sort: 0,
    isDefault: 0,
    createBy: null,
    createTime: '2026-11-06 10:00:00',
    updateBy: null,
    updateTime: '2026-11-06 10:00:00',
    children: null,
    ...partial,
  }
}

/** The completed hospital the administrators converge on after first-run. */
const HOSPITAL = orgNode({
  id: 1,
  name: '示例医院',
  code: 'HOSP-001',
  shortName: '示例医',
  address: '上海市测试路100号',
  contact: '张管理',
  phone: '021-80000000',
  children: [],
})

/** Option B first-run bootstrap hospital (V16 normalization target). */
const BOOTSTRAP_HOSPITAL = orgNode({ id: 1, name: '未命名医院', code: 'HOSPITAL', children: [] })

const CAMPUS_A = orgNode({
  id: 274,
  upId: 1,
  level: 2,
  type: 1,
  name: '总院区',
  code: 'CAMPUS-1',
  isDefault: 1,
  status: 1,
})

const CAMPUS_B = orgNode({
  id: 275,
  upId: 1,
  level: 2,
  type: 1,
  name: '东院区',
  code: 'CAMPUS-EAST',
  isDefault: 0,
  status: 0,
})

function hospitalWith(...campuses: OrgNode[]): OrgNode {
  return { ...HOSPITAL, children: campuses.length > 0 ? campuses : null }
}

/** Successive tree payloads: after a mutation the refreshed tree must differ. */
function treeFixture(...rounds: OrgNode[][]) {
  let call = 0
  vi.mocked(orgsApi.orgTree).mockImplementation(() => {
    const round = rounds[Math.min(call, rounds.length - 1)]
    call += 1
    return Promise.resolve(round)
  })
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <OrgsPage />
    </ConfigProvider>,
  )
}

/** AntD's Table spin container carries pointer-events:none - row-action clicks
 * must wait for the CURRENT load to finish, or userEvent refuses under load. */
async function waitForTableReady() {
  await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull())
  // The tree's auto-select effect fires a second identical load right after the
  // first settles - require the calm to HOLD across a macrotask boundary or the
  // click lands on the new spin's pointer-events:none container.
  await new Promise((resolve) => setTimeout(resolve, 60))
  await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull())
}

async function waitForCampusRow(name: string) {
  await waitFor(() => expect(screen.getByText(name)).toBeInTheDocument())
}

/**
 * jsdom starts no CSS animation, so a CLOSED AntD drawer keeps lingering in the
 * DOM mid-leave and `queryByRole('dialog')` cannot tell "open" from "closed".
 * 「成功关闭」 = the drawer root dropped `ant-drawer-open`; 「弹窗不关闭」 = it
 * keeps the class and the typed value still stands.
 */
async function expectDialogClosed() {
  await waitFor(() => expect(document.querySelector('.ant-drawer-open')).toBeNull())
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
}

/** The campus table row of the given campus name. */
function rowOf(campusName: string): HTMLElement {
  const cell = screen.getByText(campusName)
  return cell.closest('tr') as HTMLElement
}

async function openCampusDropdown(user: ReturnType<typeof userEvent.setup>): Promise<HTMLElement> {
  await user.click(screen.getByRole('combobox', { name: '状态过滤' }))
  await waitFor(() => {
    expect(document.querySelector('.ant-select-dropdown')).not.toBeNull()
  })
  return document.querySelector('.ant-select-dropdown') as HTMLElement
}

/** dict_city row (levels 1-3 only; the region cascade never touches the root). */
function cityDict(
  partial: Partial<CityDict> & Pick<CityDict, 'id' | 'upId' | 'code' | 'name' | 'level'>,
): CityDict {
  return {
    abbreviation: null,
    cityCode: null,
    zipCode: null,
    merger: '',
    pinyin: '',
    lng: '',
    lat: '',
    initial: null,
    children: null,
    ...partial,
  }
}

const PROVINCES = [
  cityDict({ id: 110000, upId: 100000, code: '110000', name: '北京', level: 1 }),
  cityDict({ id: 310000, upId: 100000, code: '310000', name: '上海', level: 1 }),
]
const CITIES_BY_UP: Record<number, CityDict[]> = {
  110000: [cityDict({ id: 110100, upId: 110000, code: '110100', name: '北京市', level: 2 })],
  310000: [cityDict({ id: 310100, upId: 310000, code: '310100', name: '上海市', level: 2 })],
}
const DISTRICTS_310100 = [cityDict({ id: 310101, upId: 310100, code: '310101', name: '黄浦区', level: 3 })]

/** Full region routing for the cascade: provinces on level=1, one node's children on upId. */
function cityDictFixture() {
  vi.mocked(dictsApi.listCityDicts).mockImplementation((params) => {
    if (params.level === 1) return Promise.resolve(PROVINCES)
    if (params.upId === 310100) return Promise.resolve(DISTRICTS_310100)
    return Promise.resolve(CITIES_BY_UP[params.upId ?? -1] ?? [])
  })
}

/** Open the named AntD Select and return its OPEN dropdown (the :not(-hidden)
 * guard excludes earlier, already-closed dropdown portals). */
async function openSelectDropdown(
  user: ReturnType<typeof userEvent.setup>,
  name: string,
): Promise<HTMLElement> {
  await user.click(screen.getByRole('combobox', { name }))
  return waitFor(() => {
    const dropdown = document.querySelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
    expect(dropdown).not.toBeNull()
    return dropdown as HTMLElement
  })
}

/** Choose one region option row. AntD keeps the previous dropdown in the DOM
 * while it animates closed; region fixture labels are unique, so select by the
 * option title after the named combobox opens. */
async function pickRegion(user: ReturnType<typeof userEvent.setup>, name: string, label: string) {
  await openSelectDropdown(user, name)
  const option = await waitFor(() => {
    const els = Array.from(document.querySelectorAll(`.ant-select-item-option[title="${label}"]`))
    if (els.length === 0) throw new Error(`region option ${label} not found for ${name}`)
    return els.at(-1) as HTMLElement
  })
  await user.click(option)
}

beforeEach(() => {
  vi.clearAllMocks()
  // The documented perms string of this page (seed menu id 7); no new perms
  // code is invented - the mutation surface rides on it (design D6).
  useAuthStore.getState().setSession({ perms: ['system:org:list', 'system:org:create', 'system:org:update', 'system:org:delete'] })
  vi.mocked(orgsApi.createOrg).mockResolvedValue(9)
  vi.mocked(dictsApi.listCityDicts).mockResolvedValue([
    {
      id: 310000,
      upId: 0,
      code: '310000',
      name: '上海市',
      abbreviation: '沪',
      level: 1,
      cityCode: '310000',
      zipCode: '200000',
      merger: '310000',
      pinyin: 'shanghai',
      lng: '121.473701',
      lat: '31.230416',
      initial: 'S',
      children: null,
    },
  ])
  vi.mocked(orgsApi.updateOrg).mockResolvedValue(true)
  vi.mocked(orgsApi.deleteOrg).mockResolvedValue(true)
  vi.mocked(commonApi.uploadImage).mockResolvedValue({ path: '/profile/avatar/2026/11/06/uploaded.png' })
  treeFixture([hospitalWith(CAMPUS_A, CAMPUS_B)])
})

describe('org page - hospital card and campus table render (delta: Hospital and campuses render)', () => {
  it('renders the left hospital profile card and the right campus table rows', async () => {
    renderPage()
    await waitForCampusRow('总院区')

    // Left card: key profile fields visible.
    expect(screen.getByText('示例医院')).toBeInTheDocument()
    expect(screen.getByText('ID')).toBeInTheDocument()
    expect(screen.getByText('上海市测试路100号')).toBeInTheDocument()
    expect(screen.getByText(/张管理/)).toBeInTheDocument()

    // Right table: one row per campus with code, default state, status switch.
    const rowA = rowOf('总院区')
    expect(within(rowA).getByText('CAMPUS-1')).toBeInTheDocument()
    expect(within(rowA).getByText('默认院区')).toBeInTheDocument()
    const statusSwitchA = within(rowA).getByRole('switch', { name: '启用 总院区' })
    expect(statusSwitchA).toBeChecked()
    expect(statusSwitchA).toHaveTextContent('✓')
    expect(statusSwitchA).not.toHaveTextContent('启')
    const rowB = rowOf('东院区')
    expect(within(rowB).getByText('CAMPUS-EAST')).toBeInTheDocument()
    expect(within(rowB).queryByText('默认院区')).toBeNull()
    const statusSwitchB = within(rowB).getByRole('switch', { name: '启用 东院区' })
    expect(statusSwitchB).not.toBeChecked()
    expect(statusSwitchB).toHaveTextContent('✕')
    expect(statusSwitchB).not.toHaveTextContent('停')
  })

  it('offers no generic root-creation control anywhere on the page', async () => {
    renderPage()
    await waitForCampusRow('总院区')
    expect(screen.queryByText('新建根机构')).toBeNull()
    expect(screen.queryByText('新建机构')).toBeNull()
  })
})

describe('org page - merged campus 地址 cell (user direction 2026-11-06)', () => {
  it('renders the detailed address above the resolved 地区 label as two lines', async () => {
    cityDictFixture()
    const campusWithRegion = orgNode({
      id: 274,
      upId: 1,
      level: 2,
      type: 1,
      name: '总院区',
      code: 'CAMPUS-1',
      isDefault: 1,
      provinceCode: '310000',
      cityCode: '310100',
      districtCode: '310101',
      address: '测试大道1号',
    })
    treeFixture([hospitalWith(campusWithRegion, CAMPUS_B)])
    renderPage()
    await waitForCampusRow('总院区')

    const rowA = rowOf('总院区')
    // Line 1: the stored detailed address stays in the same cell.
    expect(within(rowA).getByText('测试大道1号')).toBeInTheDocument()
    // Line 2: the chained divisions lookup resolves 省 市 区 (level=1 then upId×2).
    expect(await within(rowA).findByText('上海 上海市 黄浦区')).toBeInTheDocument()
  })

  it('renders the 地区 line alone for a campus without a detailed address', async () => {
    cityDictFixture()
    const regionOnly = orgNode({
      id: 274,
      upId: 1,
      level: 2,
      type: 1,
      name: '总院区',
      code: 'CAMPUS-1',
      provinceCode: '310000',
      cityCode: '310100',
      address: null,
    })
    treeFixture([hospitalWith(regionOnly)])
    renderPage()
    await waitForCampusRow('总院区')

    const rowA = rowOf('总院区')
    expect(await within(rowA).findByText('上海 上海市')).toBeInTheDocument()
  })

  it('keeps the em-dash placeholder for a campus with neither region nor address', async () => {
    // createBy set so the row's only '—' is the 地址 cell (creator shows UID 7).
    const bare = orgNode({
      id: 275,
      upId: 1,
      level: 2,
      type: 1,
      name: '东院区',
      code: 'CAMPUS-EAST',
      createBy: 7,
    })
    treeFixture([hospitalWith(CAMPUS_A, bare)])
    renderPage()

    await waitForCampusRow('东院区')
    expect(within(rowOf('东院区')).getByText('—')).toBeInTheDocument()
  })
})

describe('org page - campus filtering (delta: campus table keyword/status filtering)', () => {
  it('queries campus rows by name through the backend', async () => {
    const user = userEvent.setup()
    treeFixture([hospitalWith(CAMPUS_A, CAMPUS_B)], [hospitalWith(CAMPUS_B)])
    renderPage()
    await waitForCampusRow('总院区')
    await user.type(screen.getByPlaceholderText('院区名称'), '东')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    expect(orgsApi.orgTree).toHaveBeenNthCalledWith(2, { name: '东' })
    expect(screen.getByText('东院区')).toBeInTheDocument()
    expect(screen.queryByText('总院区')).toBeNull()
  })

  it('filters the campus rows by status and resets to unfiltered afterwards', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForCampusRow('总院区')
    const dropdown = await openCampusDropdown(user)
    await user.click(within(dropdown).getByText('停用'))
    expect(screen.getByText('东院区')).toBeInTheDocument()
    expect(screen.queryByText('总院区')).toBeNull()
  })
})

describe('org page - campus CRUD round-trip (delta: Campus CRUD round-trip)', () => {
  it('creates a campus through the documented POST and reflects it after refresh', async () => {
    const user = userEvent.setup()
    const created = orgNode({
      id: 276, upId: 1, level: 2, type: 1, name: '浦东分院', code: 'CAMPUS-PD', isDefault: 0, status: 1,
    })
    treeFixture([hospitalWith(CAMPUS_A, CAMPUS_B)], [hospitalWith(CAMPUS_A, CAMPUS_B, created)])
    renderPage()
    await waitForCampusRow('总院区')

    await user.click(screen.getByRole('button', { name: /新建院区/ }))
    await user.type(screen.getByLabelText('机构代码'), 'CAMPUS-PD')
    await user.type(screen.getByLabelText('机构名称'), '浦东分院')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await expectDialogClosed()
    expect(orgsApi.createOrg).toHaveBeenCalledWith(
      expect.objectContaining({ upId: 1, code: 'CAMPUS-PD', name: '浦东分院' }),
    )
    await waitFor(() => expect(screen.getByText('浦东分院')).toBeInTheDocument())
  })

  it('edits a campus profile through PUT and reflects the refreshed values', async () => {
    const user = userEvent.setup()
    const renamed = { ...CAMPUS_A, name: '总院区改名', shortName: '总院' }
    treeFixture([hospitalWith(CAMPUS_A, CAMPUS_B)], [hospitalWith(renamed, CAMPUS_B)])
    renderPage()
    await waitForCampusRow('总院区')

    await waitForTableReady()
    await user.click(within(rowOf('总院区')).getByTitle('编辑院区'))
    expect(screen.getByLabelText('机构代码')).toHaveValue('CAMPUS-1')
    await user.clear(screen.getByLabelText('机构名称'))
    await user.type(screen.getByLabelText('机构名称'), '总院区改名')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await expectDialogClosed()
    expect(orgsApi.updateOrg).toHaveBeenCalledWith(274, expect.objectContaining({ name: '总院区改名' }))
    await waitFor(() => expect(screen.getByText('总院区改名')).toBeInTheDocument())
  })

  it('deletes an empty campus after confirmation and refreshes the table', async () => {
    const user = userEvent.setup()
    treeFixture([hospitalWith(CAMPUS_A, CAMPUS_B)], [hospitalWith(CAMPUS_A)])
    renderPage()
    await waitForCampusRow('东院区')

    await waitForTableReady()
    await user.click(within(rowOf('东院区')).getByTitle('删除院区'))
    await user.click(screen.getByRole('button', { name: '删 除' }))
    await waitFor(() => expect(orgsApi.deleteOrg).toHaveBeenCalledWith(275))
    await waitFor(() => expect(screen.queryByText('东院区')).toBeNull())
  })

  it('toggles campus status through PUT and reflects the refreshed tree', async () => {
    const user = userEvent.setup()
    treeFixture([hospitalWith(CAMPUS_A, CAMPUS_B)], [hospitalWith({ ...CAMPUS_A, status: 0 }, CAMPUS_B)])
    renderPage()
    await waitForCampusRow('总院区')

    await waitForTableReady()
    const row = rowOf('总院区')
    await user.click(within(row).getByRole('switch', { name: '启用 总院区' }))
    await waitFor(() => expect(orgsApi.setOrgStatus).toHaveBeenCalledWith(274, 0))
    await waitFor(() =>
      expect(within(rowOf('总院区')).getByRole('switch', { name: '启用 总院区' })).not.toBeChecked(),
    )
  })
})

describe('org page - rejection surfaces (delta: Organization rejection surfaces)', () => {
  it('keeps the dialog open and shows the backend message when a create is rejected', async () => {
    const user = userEvent.setup()
    vi.mocked(orgsApi.createOrg).mockRejectedValue(
      new ApiError('A0712', 'organization code already exists: CAMPUS-1'),
    )
    renderPage()
    await waitForCampusRow('总院区')

    await user.click(screen.getByRole('button', { name: /新建院区/ }))
    await user.type(screen.getByLabelText('机构代码'), 'CAMPUS-1')
    await user.type(screen.getByLabelText('机构名称'), '重复院区')
    await user.click(screen.getByRole('button', { name: '保 存' }))

    expect(await screen.findByText('organization code already exists: CAMPUS-1')).toBeInTheDocument()
    // The dialog STAYS open and the table is unchanged until a successful mutation.
    expect(document.querySelector('.ant-drawer-open')).not.toBeNull()
    expect(screen.getByText('总院区')).toBeInTheDocument()
    expect(screen.queryByText('重复院区')).toBeNull()
  })

  it('surfaces the delete guard message and keeps the row when the campus is not empty', async () => {
    const user = userEvent.setup()
    vi.mocked(orgsApi.deleteOrg).mockRejectedValue(
      new ApiError('A0400', 'organization still has live departments'),
    )
    renderPage()
    await waitForCampusRow('东院区')

    await waitForTableReady()
    await user.click(within(rowOf('东院区')).getByTitle('删除院区'))
    await user.click(screen.getByRole('button', { name: '删 除' }))

    expect(await screen.findByText('organization still has live departments')).toBeInTheDocument()
    expect(screen.getByText('东院区')).toBeInTheDocument()
  })
})

describe('org page - profile form (delta: Campus Profile Management)', () => {
  it('blocks submission without a code and never calls the endpoint', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForCampusRow('总院区')

    await user.click(screen.getByRole('button', { name: /新建院区/ }))
    await user.type(screen.getByLabelText('机构名称'), '无代码院区')
    await user.click(screen.getByRole('button', { name: '保 存' }))

    expect(await screen.findByText('请输入机构代码')).toBeInTheDocument()
    expect(orgsApi.createOrg).not.toHaveBeenCalled()
  })

  it('offers no parent selector or type control in the dialogs (D2/D3)', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForCampusRow('总院区')

    await user.click(screen.getByRole('button', { name: /新建院区/ }))
    expect(screen.queryByLabelText('上级机构')).toBeNull()
    expect(screen.queryByLabelText('机构类型')).toBeNull()
    // Campus ownership is implicit; no hierarchy context/control is shown.
    const drawer = document.querySelector('.ant-drawer-open') as HTMLElement
    expect(within(drawer).queryByText(/上级机构:/)).not.toBeInTheDocument()
  })

  it('submits a full campus profile: region cascade, address, contact, status', async () => {
    const user = userEvent.setup()
    cityDictFixture()
    renderPage()
    await waitForCampusRow('总院区')

    await user.click(screen.getByRole('button', { name: /新建院区/ }))
    await user.type(screen.getByLabelText('机构代码'), 'CAMPUS-W')
    await user.type(screen.getByLabelText('机构名称'), '西院区')

    // 地区 cascade: the province choice loads its cities by upId, the city
    // choice loads its districts - the model keeps the three GB codes.
    await pickRegion(user, '省份', '上海')
    await pickRegion(user, '城市', '上海市')
    await pickRegion(user, '区县', '黄浦区')

    await user.type(screen.getByLabelText('地址'), '西区测试路3号')
    await user.type(screen.getByLabelText('联系人'), '李院办')
    await user.click(screen.getByRole('button', { name: '保 存' }))

    await expectDialogClosed()
    expect(dictsApi.listCityDicts).toHaveBeenCalledWith({ level: 1 })
    expect(dictsApi.listCityDicts).toHaveBeenCalledWith({ upId: 310000 })
    expect(dictsApi.listCityDicts).toHaveBeenCalledWith({ upId: 310100 })
    expect(orgsApi.createOrg).toHaveBeenCalledWith(
      expect.objectContaining({
        upId: 1,
        code: 'CAMPUS-W',
        name: '西院区',
        provinceCode: '310000',
        cityCode: '310100',
        districtCode: '310101',
        address: '西区测试路3号',
        contact: '李院办',
      }),
    )
  })

  it('backfills the region cascade from stored codes when editing', async () => {
    const user = userEvent.setup()
    const regionCampus = { ...CAMPUS_A, provinceCode: '310000', cityCode: '310100', districtCode: '310101' }
    treeFixture([hospitalWith(regionCampus, CAMPUS_B)])
    cityDictFixture()
    renderPage()
    await waitForCampusRow('总院区')

    await waitForTableReady()
    await user.click(within(rowOf('总院区')).getByTitle('编辑院区'))

    // The cascade chains by up_id: 310000 -> its cities, 310100 -> its districts.
    await waitFor(() => expect(dictsApi.listCityDicts).toHaveBeenCalledWith({ upId: 310000 }))
    await waitFor(() => expect(dictsApi.listCityDicts).toHaveBeenCalledWith({ upId: 310100 }))
    expect(screen.getByText('上海市')).toBeInTheDocument()
    expect(screen.getByText('黄浦区')).toBeInTheDocument()
  })

  it('clears the downstream selects when the province changes and submits the clear form', async () => {
    const user = userEvent.setup()
    const regionCampus = { ...CAMPUS_A, provinceCode: '310000', cityCode: '310100', districtCode: '310101' }
    treeFixture([hospitalWith(regionCampus, CAMPUS_B)])
    cityDictFixture()
    renderPage()
    await waitForCampusRow('总院区')

    await waitForTableReady()
    await user.click(within(rowOf('总院区')).getByTitle('编辑院区'))
    await waitFor(() => expect(dictsApi.listCityDicts).toHaveBeenCalledWith({ upId: 310100 }))

    await pickRegion(user, '省份', '北京')
    await waitFor(() => expect(dictsApi.listCityDicts).toHaveBeenCalledWith({ upId: 110000 }))

    await user.click(screen.getByRole('button', { name: '保 存' }))
    await expectDialogClosed()
    // Cleared selects submit '' - the only wire form the backend's NOT-NULL
    // update strategy accepts as a column clear.
    expect(orgsApi.updateOrg).toHaveBeenCalledWith(
      274,
      expect.objectContaining({ provinceCode: '110000', cityCode: '', districtCode: '' }),
    )
  })
})

describe('org page - first-run completion (delta: First-run Organization Profile Completion)', () => {
  it('shows the completion hint on the bootstrap hospital without a root-creation control', async () => {
    treeFixture([{ ...BOOTSTRAP_HOSPITAL, children: [CAMPUS_A] }])
    renderPage()
    await waitForCampusRow('总院区')
    expect(screen.getByText(/首次部署/)).toBeInTheDocument()
    expect(screen.queryByText('新建根机构')).toBeNull()
  })

  it('completes the profile through the hospital edit form and the hint disappears', async () => {
    const user = userEvent.setup()
    const completed = orgNode({ id: 1, name: '协和医院', code: 'HOSP-XYZ', children: [CAMPUS_A] })
    treeFixture([{ ...BOOTSTRAP_HOSPITAL, children: [CAMPUS_A] }], [completed])
    renderPage()
    await waitForCampusRow('总院区')

    await user.click(screen.getByRole('button', { name: '编辑' }))
    await user.clear(screen.getByLabelText('机构名称'))
    await user.type(screen.getByLabelText('机构名称'), '协和医院')
    await user.clear(screen.getByLabelText('机构代码'))
    await user.type(screen.getByLabelText('机构代码'), 'HOSP-XYZ')
    await user.click(screen.getByRole('button', { name: '保 存' }))

    await expectDialogClosed()
    expect(orgsApi.updateOrg).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ name: '协和医院', code: 'HOSP-XYZ' }),
    )
    await waitFor(() => expect(screen.queryByText(/首次部署/)).toBeNull())
    expect(screen.getByText('协和医院')).toBeInTheDocument()
  })
})

describe('org page - perms gating (design D6)', () => {
  it('hides every mutation control without system:org:list but keeps the page readable', async () => {
    useAuthStore.getState().setSession({ perms: ['system:user:list'] })
    renderPage()
    await waitForCampusRow('总院区')

    expect(screen.queryByRole('button', { name: '新建院区' })).toBeNull()
    expect(screen.queryByRole('button', { name: '编辑' })).toBeNull()
    expect(screen.queryByTitle('编辑院区')).toBeNull()
    expect(screen.queryByTitle('删除院区')).toBeNull()
    expect(screen.queryByTitle('设为默认')).toBeNull()
    // Read-only stays: card + rows still render.
    expect(screen.getByText('示例医院')).toBeInTheDocument()
    expect(screen.getByText('总院区')).toBeInTheDocument()
  })
})

describe('orgs page - button-level gating (button-perm-enforcement-web)', () => {
  it('hides ungranted affordances when only the page perm is held', async () => {
    useAuthStore.getState().setSession({ perms: ['system:org:list'] })
    renderPage()
    await screen.findByText('示例医院')
    expect(screen.queryByRole('button', { name: /新建院区/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument()
    expect(screen.queryByTitle('删除院区')).not.toBeInTheDocument()
  })

  it('disables the campus status switch without system:org:update', async () => {
    useAuthStore.getState().setSession({ perms: ['system:org:list', 'system:org:create'] })
    renderPage()
    await screen.findByText('示例医院')
    expect(screen.getByRole('switch', { name: '启用 总院区' })).toBeDisabled()
  })

  it('gates 设为默认 by system:org:update (it is an updateOrg call - D7a resolution)', async () => {
    // page + create only: 设为默认 must stay hidden (it rides PUT /orgs/{id})
    useAuthStore.getState().setSession({ perms: ['system:org:list', 'system:org:create'] })
    renderPage()
    await screen.findByText('示例医院')
    expect(screen.queryAllByTitle('编辑院区').length).toBe(0)
    // The default marker is display-only on this page; no row default action.
    expect(screen.queryAllByTitle('设为默认').length).toBe(0)
    expect(screen.getByRole('button', { name: /新建院区/ })).toBeInTheDocument()

    // + update: the edit affordance appears; no default action is reintroduced
    useAuthStore.getState().setSession({ perms: ['system:org:list', 'system:org:create', 'system:org:update'] })
    renderPage()
    await screen.findByText('示例医院')
    expect(screen.getAllByTitle('编辑院区').length).toBeGreaterThan(0)
    expect(screen.queryAllByTitle('设为默认').length).toBe(0)
  })

  it('reveals the campus affordances when their button perms are granted', async () => {
    useAuthStore.getState().setSession({ perms: ['system:org:list', 'system:org:create', 'system:org:update', 'system:org:delete'] })
    renderPage()
    await screen.findByText('示例医院')
    expect(screen.getByRole('button', { name: /新建院区/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '编辑' })).toBeInTheDocument()
    expect(screen.getAllByTitle('删除院区').length).toBeGreaterThan(0)
  })
})

describe('org drawer logo upload (org-logo-upload-web 2.1)', () => {
  /** The completed hospital carrying a legacy stored-path logo. */
  function hospitalWithLogo(logoUrl: string): OrgNode {
    return { ...HOSPITAL, logoUrl, children: [] }
  }

  async function openHospitalEdit(): Promise<ReturnType<typeof userEvent.setup>> {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('示例医院')
    await waitForTableReady()
    await user.click(screen.getByRole('button', { name: '编辑' }))
    await screen.findByText('编辑医院')
    return user
  }

  function drawerFileInput(): HTMLInputElement {
    const input = document.querySelector('.ant-drawer input[type="file"]')
    expect(input).not.toBeNull()
    return input as HTMLInputElement
  }

  it('renders the current logo as the tile image in the drawer', async () => {
    treeFixture([hospitalWithLogo('/profile/avatar/2026/11/06/legacy.png')])
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('示例医院')
    await waitForTableReady()
    await user.click(screen.getByRole('button', { name: '编辑' }))
    const drawer = await screen.findByRole('dialog')
    expect(within(drawer).getByAltText('Logo 预览')).toHaveAttribute(
      'src',
      '/profile/avatar/2026/11/06/legacy.png',
    )
    expect(commonApi.uploadImage).not.toHaveBeenCalled()
  })

  it('uploads a picked png, stores the returned path, and submits it with the org body', async () => {
    const user = await openHospitalEdit()
    const png = new File([new Uint8Array([137, 80, 78, 71])], 'new-logo.png', { type: 'image/png' })
    await user.upload(drawerFileInput(), png)

    await waitFor(() => expect(commonApi.uploadImage).toHaveBeenCalledTimes(1))
    expect(commonApi.uploadImage).toHaveBeenCalledWith(png)
    await waitFor(() =>
      expect(within(screen.getByRole('dialog')).getByAltText('Logo 预览')).toHaveAttribute(
        'src',
        '/profile/avatar/2026/11/06/uploaded.png',
      ),
    )

    // AntD inserts a space between a two-character CJK button label - the accessible
    // name is `保 存` (the house query used by the rejection tests below).
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '保 存' }))
    await expectDialogClosed()
    const body = vi.mocked(orgsApi.updateOrg).mock.calls[0][1] as { logoUrl?: string | null }
    expect(body.logoUrl).toBe('/profile/avatar/2026/11/06/uploaded.png')
  })

  it('rejects a .gif client-side with a warning and fires no request', async () => {
    const user = await openHospitalEdit()
    const gif = new File([new Uint8Array([71, 73, 70])], 'logo.gif', { type: 'image/gif' })
    await user.upload(drawerFileInput(), gif)
    await screen.findByText('仅支持 png/jpg/jpeg/webp 图片')
    expect(commonApi.uploadImage).not.toHaveBeenCalled()
  })

  it('keeps the stored logo and surfaces the backend message when the upload fails', async () => {
    treeFixture([hospitalWithLogo('/profile/avatar/2026/11/06/legacy.png')])
    vi.mocked(commonApi.uploadImage).mockRejectedValueOnce(new ApiError('A0400', '图片大小不能超过 2MB'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('示例医院')
    await waitForTableReady()
    await user.click(screen.getByRole('button', { name: '编辑' }))
    await screen.findByText('编辑医院')

    const big = new File([new Uint8Array([137, 80, 78, 71])], 'big.png', { type: 'image/png' })
    await user.upload(drawerFileInput(), big)
    await screen.findByText('图片大小不能超过 2MB')
    expect(within(screen.getByRole('dialog')).getByAltText('Logo 预览')).toHaveAttribute(
      'src',
      '/profile/avatar/2026/11/06/legacy.png',
    )
  })
})

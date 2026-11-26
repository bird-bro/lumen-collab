import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import dayjs from 'dayjs'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UsRegistrationPage, { diffYmd } from '@/pages/us/registration'
import * as usOrdersApi from '@/api/us-orders'
import type { UsOrderStatus, UsOrderView } from '@/api/us-orders'
import * as examItemApi from '@/api/exam-item'
import * as searchFiltersApi from '@/api/search-filters'
import * as dictsApi from '@/api/dicts'
import { invalidateDictItemsCache } from '@/utils/dict-items'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'
import { resolveMenuRoute } from '@/routes/menu-paths'

/**
 * US 工作站两态工作台 (us-registration-redesign tasks 1.2-1.3; spec delta
 * `web-us-station` § Registration Page Shell — the 拍板 01-B two-state shell):
 * ONE route renders the COMPACT state by default (392px 今日登记单 rail + the
 * registration workbench form, NO query row) and the edge toggle swaps to the
 * WIDE-LIST state (full-width table + the query row, the datenav hidden — time
 * owned by the apply-time range). The api seam is mocked - no page data flows
 * through a mock at runtime (ruling 2026-11-05).
 */
vi.mock('@/api/us-orders')
vi.mock('@/api/search-filters')
vi.mock('@/api/exam-item', async (importOriginal) => ({
  ...(await importOriginal<typeof examItemApi>()),
  pageExamItems: vi.fn(),
}))
vi.mock('@/api/dicts')

const FULL_PERMS = ['us:order:list', 'us:order:create', 'us:order:update', 'us:order:cancel']

function orderView(partial: Partial<UsOrderView> & { orderId: number }): UsOrderView {
  return {
    accessionNo: 'US26000001',
    status: 'REGISTERED',
    patientUid: 101,
    patientSnapshot: { name: '张三', patId: 'P00000100', gender: 1, phone: '13800000001' },
    examItems: [{ itemId: 1, itemName: '腹部彩超' }],
    emgFlag: 0,
    cancelReason: null,
    createTime: '2026-11-20 15:00:00',
    createBy: null,
    registrationType: null,
    bookingTime: null,
    pregnancyStatus: null,
    gestationalWeeks: null,
    note: null,
    applyDept: null,
    applyDoctor: null,
    applyTime: null,
    applyDeptId: null,
    applyDoctorId: null,
    ...partial,
  }
}

function pageFixture(records: UsOrderView[]) {
  return { records, total: records.length, size: 20, current: 1, pages: 1 }
}

const ZERO_COUNTS: Record<UsOrderStatus, number> = {
  REGISTERED: 0,
  IN_EXAM: 0,
  IN_DIAGNOSIS: 0,
  COMPLETED: 0,
  PRINTED: 0,
  CANCELLED: 0,
}

const US_ITEMS = [
  { id: 1, exam: '腹部彩超', modality: 'US', isCommon: true, bodyName: '肝胆胰脾' },
  { id: 2, exam: '心脏彩超', modality: 'US', isCommon: false, bodyName: '心脏' },
  { id: 3, exam: '阑尾超声', modality: 'US', isCommon: false, bodyName: '右下腹' },
]

/** The booking dialog's D+1 default (us-booking-slot-picker 2026-11-24 redesign). */
const TOMORROW = dayjs().add(1, 'day').format('YYYY-MM-DD')

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <UsRegistrationPage />
    </ConfigProvider>,
  )
}

describe('US workstation two-state workbench (spec: web-us-station)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    invalidateDictItemsCache()
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    // The dict transport stub: 1004 性别 / 1006 证件类型 (the V6 seed's stable
    // rcp ca_id anchors); unknown catalogs resolve empty.
    // the booking dialog's slot grid stub (us-booking-slot-picker): 09:30 rides
    // tight (仅剩 1 号) and 10:00 at capacity - the 满员标灰 render asserted
    vi.mocked(usOrdersApi.listSlotOptions).mockResolvedValue({
      configured: true,
      slots: [
        { start: '09:00', end: '09:30', booked: 0, capacity: 4 },
        { start: '09:30', end: '10:00', booked: 3, capacity: 4 },
        { start: '10:00', end: '10:30', booked: 4, capacity: 4 },
      ],
    })
    vi.mocked(dictsApi.fetchDictItemsById).mockImplementation(async (catalId: number) => {
      if (catalId === 1004) {
        return [
          { code: 100401, value: '男' },
          { code: 100402, value: '女' },
          { code: 100403, value: '未知' },
        ]
      }
      if (catalId === 1006) {
        return [
          { code: 100601, value: '居民身份证' },
          { code: 100602, value: '护照' },
        ]
      }
      if (catalId === 1023) {
        return [
          { code: 1, value: '门诊' },
          { code: 2, value: '住院' },
          { code: 3, value: '急诊' },
          { code: 4, value: '体检' },
        ]
      }
      if (catalId === 1002) {
        return [
          { code: 1, value: '未孕' },
          { code: 2, value: '早孕' },
          { code: 3, value: '中晚孕' },
          { code: 4, value: '未知' },
        ]
      }
      // the 1031 登记单状态 catalog (registration-order-status-dict-sev V55): the
      // six machine statuses as code 103101-103106 with the DB-configured colors
      if (catalId === 1031) {
        return [
          { code: 103101, value: '已登记', tag: 'blue' },
          { code: 103102, value: '检查中', tag: 'amber' },
          { code: 103103, value: '诊断中', tag: 'amber' },
          { code: 103104, value: '已完成', tag: 'green' },
          { code: 103105, value: '已打印', tag: 'green' },
          { code: 103106, value: '已取消', tag: 'gray' },
        ]
      }
      return []
    })
    vi.mocked(examItemApi.pageExamItems).mockResolvedValue({
      records: US_ITEMS,
      total: US_ITEMS.length,
      size: 20,
      current: 1,
      pages: 1,
    } as never)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({ orderId: 11, status: 'REGISTERED', bookingTime: '2026-11-25 09:17:00' }),
        orderView({ orderId: 12, status: 'REGISTERED', accessionNo: 'US26000002' }),
      ]) as never,
    )
    vi.mocked(usOrdersApi.statusCounts).mockResolvedValue({
      ...ZERO_COUNTS,
      REGISTERED: 2,
    })
  })

  it('maps the seeded component key to the /us/registration route', () => {
    expect(
      resolveMenuRoute({ mid: 1, type: 'C', path: 'registration', component: 'us/registration/index' } as never),
    ).toBe('/us/registration')
  })

  it('renders the COMPACT state by default — rail + workbench form, NO query row', async () => {
    renderPage()
    // the rail head + the form sections mount before the wide query row
    expect(await screen.findByLabelText('日期导航')).toBeInTheDocument()
    expect(screen.getByText('患者信息')).toBeInTheDocument()
    expect(screen.getByText('请检信息')).toBeInTheDocument()
    expect(screen.getByText('检查信息')).toBeInTheDocument()
    // the compact state carries NO query row (拍板: the vertical space belongs to the list)
    expect(screen.queryByText(/更多条件/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText('检查号条件')).not.toBeInTheDocument()
    // the datenav shows in the compact head
    expect(screen.getByLabelText('日期导航')).toBeInTheDocument()
  })

  it('the rail foot rides the shared ConsolePagination in both states (共 N 条, no hand-rolled ‹ ›)', async () => {
    renderPage()
    // the standard pager (unified with the setting/system pages) replaces the prototype's ‹ 1 › foot
    expect(await screen.findByText('共 2 条')).toBeInTheDocument()
    expect(document.querySelector('.ant-pagination')).toBeInTheDocument()
    expect(screen.queryByText(/· 2 单/)).not.toBeInTheDocument()
    // the pager survives the wide toggle (the shared list keeps its paging controls)
    fireEvent.click(screen.getByLabelText('工作台切换'))
    expect(screen.getByText('共 2 条')).toBeInTheDocument()
    expect(document.querySelector('.ant-pagination')).toBeInTheDocument()
  })

  it('the six status chips share one grouped row (head row 1 = datenav + autorefresh)', async () => {
    renderPage()
    await screen.findByLabelText('日期导航')
    // the mocked counts seed the chip labels (ZERO_COUNTS + REGISTERED = 2); the
    // labels ride the dict-1031 resolver in the V55 seed's sort order
    const chips = ['已登记 2', '检查中 0', '诊断中 0', '已完成 0', '已打印 0', '已取消 0']
      .map((label) => screen.getByLabelText(label))
    // all six live in ONE group container - no chip scattered onto the head row
    expect(new Set(chips.map((chip) => chip.parentElement)).size).toBe(1)
    // the group is its own row, not the rail head itself (datenav stays off the chips row)
    expect(chips[0].parentElement).not.toBe(screen.getByLabelText('日期导航').parentElement)
    expect(screen.getByLabelText('自动刷新').parentElement!.parentElement).not.toBe(chips[0].parentElement)
  })

  it('the edge toggle swaps to the WIDE state — query row + table, datenav hidden', async () => {
    renderPage()
    await screen.findByLabelText('日期导航')
    fireEvent.click(screen.getByLabelText('工作台切换'))
    // the wide state exposes the query row (the RCP∪EIS union, 更多条件 collapse)
    expect(await screen.findByText(/更多条件/)).toBeInTheDocument()
    expect(screen.getByLabelText('检查号条件')).toBeInTheDocument()
    // the datenav hides in the wide state (time owned by the apply-time range)
    expect(screen.queryByLabelText('日期导航')).not.toBeInTheDocument()
    // the wide table renders the shared list data
    expect(screen.getAllByText('US26000001').length).toBeGreaterThan(0)
    // toggling back restores the compact state
    fireEvent.click(screen.getByLabelText('工作台切换'))
    expect(await screen.findByLabelText('日期导航')).toBeInTheDocument()
    expect(screen.queryByText(/更多条件/)).not.toBeInTheDocument()
  })

  it('the submit affordance gates on us:order:create', async () => {
    useAuthStore.getState().setSession({ perms: ['us:order:list'] })
    renderPage()
    await screen.findByLabelText('日期导航')
    expect(screen.queryByLabelText('提交登记')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('解析查询')).not.toBeInTheDocument()
  })

  it('both states render the shared list data against the real seam', async () => {
    renderPage()
    // the rail rows ride the shared fetch (the wide table re-renders the same rows)
    expect((await screen.findAllByText(/US26000001/)).length).toBeGreaterThan(0)
    await waitFor(() => {
      expect(usOrdersApi.pageOrders).toHaveBeenCalled()
      expect(usOrdersApi.statusCounts).toHaveBeenCalled()
    })
    fireEvent.click(screen.getByLabelText('工作台切换'))
    expect(screen.getAllByText(/US26000001/).length).toBeGreaterThan(0)
  })
})

/** The house combobox option picker: the rc-select dropdown renders the option
 *  nodes with title attributes - retry until present, take the LAST (the antd
 *  closing-animation keeps the previous dropdown in the DOM). */
async function pickOption(title: string): Promise<HTMLElement> {
  return waitFor(() => {
    const els = Array.from(document.querySelectorAll(`.ant-select-item-option[title="${title}"]`))
    if (els.length === 0) throw new Error(`option ${title} not found`)
    return els.at(-1) as HTMLElement
  })
}

/** The required-demographics fill (ruling 2026-11-24: 姓名/性别/年龄/就诊卡号/患者来源
 *  mandatory - 患者来源 rides its first-option default, the rest fill here). */
function fillRequiredDemo() {
  // the gender Select (2026-11-25: the radio triple squeezed on small screens):
  // open + click the 男 option - the sync rc-select jsdom recipe (mouseDown opens)
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '性别' }))
  const male = Array.from(document.querySelectorAll('.ant-select-item-option'))
    .find((el) => el.textContent === '男') as HTMLElement
  fireEvent.click(male)
  fireEvent.change(screen.getByLabelText('年龄-岁'), { target: { value: '3' } })
  fireEvent.change(screen.getByLabelText('卡号'), { target: { value: 'CARD-1' } })
}

describe('compact workbench behaviors (us-registration-redesign batch 2)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    invalidateDictItemsCache()
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    // The dict transport stub: 1004 性别 / 1006 证件类型 (the V6 seed's stable
    // rcp ca_id anchors); unknown catalogs resolve empty.
    // the booking dialog's slot grid stub (us-booking-slot-picker): 09:30 rides
    // tight (仅剩 1 号) and 10:00 at capacity - the 满员标灰 render asserted
    vi.mocked(usOrdersApi.listSlotOptions).mockResolvedValue({
      configured: true,
      slots: [
        { start: '09:00', end: '09:30', booked: 0, capacity: 4 },
        { start: '09:30', end: '10:00', booked: 3, capacity: 4 },
        { start: '10:00', end: '10:30', booked: 4, capacity: 4 },
      ],
    })
    vi.mocked(dictsApi.fetchDictItemsById).mockImplementation(async (catalId: number) => {
      if (catalId === 1004) {
        return [
          { code: 100401, value: '男' },
          { code: 100402, value: '女' },
          { code: 100403, value: '未知' },
        ]
      }
      if (catalId === 1006) {
        return [
          { code: 100601, value: '居民身份证' },
          { code: 100602, value: '护照' },
        ]
      }
      if (catalId === 1023) {
        return [
          { code: 1, value: '门诊' },
          { code: 2, value: '住院' },
          { code: 3, value: '急诊' },
          { code: 4, value: '体检' },
        ]
      }
      if (catalId === 1002) {
        return [
          { code: 1, value: '未孕' },
          { code: 2, value: '早孕' },
          { code: 3, value: '中晚孕' },
          { code: 4, value: '未知' },
        ]
      }
      // the 1031 登记单状态 catalog (registration-order-status-dict-sev V55): the
      // six machine statuses as code 103101-103106 with the DB-configured colors
      if (catalId === 1031) {
        return [
          { code: 103101, value: '已登记', tag: 'blue' },
          { code: 103102, value: '检查中', tag: 'amber' },
          { code: 103103, value: '诊断中', tag: 'amber' },
          { code: 103104, value: '已完成', tag: 'green' },
          { code: 103105, value: '已打印', tag: 'green' },
          { code: 103106, value: '已取消', tag: 'gray' },
        ]
      }
      return []
    })
    vi.mocked(examItemApi.pageExamItems).mockResolvedValue({
      records: US_ITEMS,
      total: US_ITEMS.length,
      size: 20,
      current: 1,
      pages: 1,
    } as never)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({ orderId: 11, status: 'REGISTERED', bookingTime: '2026-11-25 09:17:00' }),
        orderView({ orderId: 12, status: 'REGISTERED', accessionNo: 'US26000002' }),
      ]) as never,
    )
    vi.mocked(usOrdersApi.statusCounts).mockResolvedValue({
      ...ZERO_COUNTS,
      REGISTERED: 2,
    })
    vi.mocked(usOrdersApi.resolvePatients).mockResolvedValue({
      result: 'NONE',
      identity: null,
      candidates: null,
    })
    vi.mocked(usOrdersApi.createOrder).mockResolvedValue({
      orderId: 99,
      accessionNo: 'US26000099',
      status: 'REGISTERED',
      registrationType: 'WALK_IN',
      bookingTime: null,
    })
    vi.mocked(usOrdersApi.cancelOrder).mockResolvedValue(true)
  })

  it('the chips carry the counts and multi-select toggles the IN-filter', async () => {
    renderPage()
    await screen.findByLabelText('日期导航')
    // us-prereg-as-registered: the default selection rides 已登记 only (the six-state
    // machine's desk default); the dict-1031 labels ride the resolver
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.status?.join(',')).toBe('REGISTERED')
    })
    // the chips render the zero-filled meter with the dict-configured hue attributes;
    // the checkbox affordance makes the selection explicit (aria-pressed + the check
    // glyph) - color alone was ruled not intuitive enough (user ruling 2026-11-26)
    const registeredChip = screen.getByRole('button', { name: '已登记 2' })
    expect(registeredChip.getAttribute('data-status-hue')).toBe('blue')
    expect(registeredChip.getAttribute('aria-pressed')).toBe('true')
    expect(registeredChip.querySelector('svg')).not.toBeNull()
    const examChip = screen.getByRole('button', { name: '检查中 0' })
    expect(examChip.getAttribute('data-status-hue')).toBe('amber')
    expect(examChip.getAttribute('aria-pressed')).toBe('false')
    expect(examChip.querySelector('svg')).toBeNull()
    expect(screen.getByRole('button', { name: '已完成 0' }).getAttribute('data-status-hue')).toBe('green')
    // toggling the 检查中 chip adds IN_EXAM into the IN-filter and flips its checkbox
    fireEvent.click(screen.getByRole('button', { name: '检查中 0' }))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '检查中 0' }).getAttribute('aria-pressed')).toBe('true')
    })
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.status?.join(',')).toBe('REGISTERED,IN_EXAM')
    })
  })

  it('the rail renders the per-order card flow with the cancel action (NO 登记 affordance)', async () => {
    renderPage()
    // us-prereg-as-registered: the arrival confirmation retires — 预登记 rows enter
    // 已登记 at creation (预约=预登记), so no card carries a 登记 action; 取消 stays.
    // The compact list is the card flow (user-approved prototype 2026-11-26) —
    // the facts stack per order panel, no table rows.
    const bookedRow = (await screen.findAllByText('张三'))[0].closest('.us-ocard') as HTMLElement
    // the six-state model: the pre-registered row IS 已登记 (预约=预登记) - the
    // status pill renders the dict-1031 label with its DB-configured hue
    expect(within(bookedRow).getByText('已登记')).toBeInTheDocument()
    expect(within(bookedRow).getByText('已登记').getAttribute('data-status-hue')).toBe('blue')
    expect(within(bookedRow).queryByRole('button', { name: /^登记/ })).not.toBeInTheDocument()
    expect(within(bookedRow).getByRole('button', { name: /^取消/ })).toBeInTheDocument()
  })

  it('the cancel affordance rides the machine live set (已登记/检查中/诊断中)', async () => {
    // the six-state edges: CANCELLED fires from the three live states only -
    // COMPLETED/PRINTED rows render stateless (the em-dash placeholder)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({ orderId: 21, status: 'REGISTERED' }),
        orderView({ orderId: 22, status: 'IN_EXAM' }),
        orderView({ orderId: 23, status: 'IN_DIAGNOSIS' }),
        orderView({ orderId: 24, status: 'COMPLETED' }),
        orderView({ orderId: 25, status: 'PRINTED' }),
      ]) as never,
    )
    renderPage()
    await screen.findByLabelText('日期导航')
    const cards = (await screen.findAllByText('张三')).map(
      (node) => node.closest('.us-ocard') as HTMLElement,
    )
    expect(cards.length).toBe(5)
    const cancelable = cards.filter(
      (card) => within(card).queryByRole('button', { name: /^取消/ }),
    )
    expect(cancelable.length).toBe(3)
  })

  it('a dict-1031 failure degrades to the bridge labels + one error message (meter survives)', async () => {
    // the coordination doc's display-vocabulary deviation: unlike the form
    // vocabularies (empty options), the chips fall back to the bridge's 中文
    // labels with no hue so the desk meter never blanks
    vi.mocked(dictsApi.fetchDictItemsById).mockImplementation(async (catalId: number) => {
      if (catalId === 1031) {
        throw new Error('dict wire down')
      }
      if (catalId === 1004) {
        return [
          { code: 100401, value: '男' },
          { code: 100402, value: '女' },
          { code: 100403, value: '未知' },
        ]
      }
      return []
    })
    renderPage()
    await screen.findByLabelText('日期导航')
    // the bridge fallback labels render in code order, hue-less
    const registered = await screen.findByRole('button', { name: '已登记 2' })
    expect(registered.getAttribute('data-status-hue')).toBeNull()
    expect(screen.getByRole('button', { name: '检查中 0' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '已打印 0' }).getAttribute('data-status-hue')).toBeNull()
    // one error message surfaces
    expect(await screen.findByText('状态字典加载失败')).toBeInTheDocument()
  })

  it('the category tag reads the server-stamped registrationType (现场/预约)', async () => {
    // us-prereg-as-registered: the category is a business fact from the backend —
    // row 11 is a PRE_REGISTERED order that has ALREADY entered 已登记 (预约=预登记),
    // so the only 预约 text in its row is the category tag; row 12 is a walk-in.
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({
          orderId: 11,
          status: 'REGISTERED',
          registrationType: 'PRE_REGISTERED',
          bookingTime: '2026-11-25 09:17:00',
        }),
        orderView({ orderId: 12, status: 'REGISTERED', registrationType: 'WALK_IN' }),
      ]) as never,
    )
    renderPage()
    const preRegRow = (await screen.findAllByText('张三'))[0].closest('.us-ocard') as HTMLElement
    expect(within(preRegRow).getByText('预约')).toBeInTheDocument()
    const walkInRow = (await screen.findAllByText('张三'))[1].closest('.us-ocard') as HTMLElement
    expect(within(walkInRow).getByText('现场')).toBeInTheDocument()
    // the wide table carries the same tag beside the status chip
    fireEvent.click(screen.getByLabelText('工作台切换'))
    const widePreReg = (await screen.findAllByText('张三'))[0].closest('tr') as HTMLElement
    expect(within(widePreReg).getByText('预约')).toBeInTheDocument()
  })

  it('the bench foot carries NO arrival-confirmation copy (预约=预登记)', async () => {
    renderPage()
    await screen.findByLabelText('日期导航')
    // us-prereg-as-registered: the pre-registered patient goes straight into the exam —
    // the retired long hint (到检后在列表点登记确认) stays gone entirely (the 2026-11-24
    // ruling removed it); only 重置/提交登记 remain
    expect(screen.getByLabelText('提交登记')).toBeInTheDocument()
    expect(screen.queryByText(/到检后在列表点/)).not.toBeInTheDocument()
    expect(screen.queryByText(/登记确认/)).not.toBeInTheDocument()
  })

  it('the resolve bar drives the three outcomes (exact fills / candidates dialog / none)', async () => {
    renderPage()
    const input = screen.getByLabelText('解析键值')
    fireEvent.change(input, { target: { value: '110101199001011234' } })
    // MATCHED fills the demographics section
    vi.mocked(usOrdersApi.resolvePatients).mockResolvedValueOnce({
      result: 'MATCHED',
      identity: { patUid: 101, patId: 'P00000100', name: '张三', gender: 1 } as never,
      candidates: null,
    })
    fireEvent.click(screen.getByRole('button', { name: '解析查询' }))
    await waitFor(() => {
      expect((screen.getByLabelText('姓名') as HTMLInputElement).value).toBe('张三')
    })
    // CANDIDATES opens the 就诊人候选 dialog; picking fills
    vi.mocked(usOrdersApi.resolvePatients).mockResolvedValueOnce({
      result: 'CANDIDATES',
      identity: null,
      candidates: [
        { patUid: 201, patId: 'P00000201', name: '李四', gender: 2 } as never,
        { patUid: 202, patId: 'P00000202', name: '王五', gender: 1 } as never,
      ],
    })
    fireEvent.change(input, { target: { value: '500' } })
    fireEvent.click(screen.getByRole('button', { name: '解析查询' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('李四')).toBeInTheDocument()
    fireEvent.click(within(dialog).getByText('李四')) // the pcard select
    fireEvent.click(within(dialog).getByRole('button', { name: '是' })) // the RCP confirm pair
    await waitFor(() => {
      expect((screen.getByLabelText('姓名') as HTMLInputElement).value).toBe('李四')
    })
    // NONE: the form opens empty for a new archive (no client 急诊占位 switch)
    fireEvent.change(input, { target: { value: '600' } })
    fireEvent.click(screen.getByRole('button', { name: '解析查询' }))
    await waitFor(() => {
      expect((screen.getByLabelText('姓名') as HTMLInputElement).value).toBe('')
    })
    expect(screen.queryByLabelText('急诊占位')).not.toBeInTheDocument()
  })

  it('section 2 hosts the 急诊 switch, the booking trigger, and 临床诊断', async () => {
    renderPage()
    await screen.findByText('请检信息')
    expect(screen.getByLabelText('急诊')).toBeInTheDocument()
    expect(screen.getByLabelText('临床诊断')).toBeInTheDocument()
    // the booking cell collapses into ONE trigger + dialog (2026-11-24 redesign) -
    // the raw DatePicker/Select pair is gone; NO resting text until a pick (ruling 2026-11-24)
    const bookingTrigger = screen.getByRole('button', { name: '选择预约时段' })
    await waitFor(() => expect(bookingTrigger).toBeEnabled())
    expect(screen.queryByLabelText('预约日期')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '预约时段' })).not.toBeInTheDocument()
  })

  it('the pregnancy cell is gender-conditional (female only, weeks disabled when 未孕/未知)', async () => {
    renderPage()
    await screen.findByText('请检信息')
    // default gender unset: the pregnancy cell stays hidden
    expect(screen.queryByLabelText('妊娠状态')).not.toBeInTheDocument()
    // female: the cell appears (the gender radio rides the dict 1004 options)
    const user = userEvent.setup()
    // the gender Select (2026-11-25): open + click the 女 option
    await user.click(screen.getByRole('combobox', { name: '性别' }))
    await user.click(await pickOption('女'))
    expect((await screen.findAllByLabelText('妊娠状态')).length).toBeGreaterThan(0)
    // 未孕 disables the weeks input
    await user.click(screen.getAllByRole('combobox', { name: '妊娠状态' })[0])
    await user.click(await pickOption('未孕'))
    expect(screen.getAllByLabelText('孕周')[0]).toBeDisabled()
  })

  it('性别 and 证件类型 resolve from the dict catalogs (1004/1006, id-anchored)', async () => {
    renderPage()
    await screen.findByText('请检信息')
    // the gender Select rides catalog 1004 (id-anchored): the dict labels, no
    // hard-coded set, no radio strip (2026-11-25)
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '性别' }))
    await waitFor(() => {
      const texts = Array.from(document.querySelectorAll('.ant-select-item-option')).map((o) => o.textContent)
      expect(texts).toEqual(['男', '女', '未知'])
    })
    // 患者来源 opens the value-keyed catalog-1023 dropdown (us-vocab-dict-catalogs)
    await userEvent.setup().click(screen.getByRole('combobox', { name: '患者来源' }))
    expect(await pickOption('体检')).toBeInTheDocument()
    // 证件类型 = the compact type segment inside the compound box (ruling
    // 2026-11-24): defaults to the first dict item, opens the dict menu - the
    // hard-coded extras are gone
    const user = userEvent.setup()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '证件类型' })).toHaveTextContent('居民身份证'))
    await user.click(screen.getByRole('button', { name: '证件类型' }))
    expect(screen.queryByText('军官证')).not.toBeInTheDocument()
  })

  it('section 3 renders the split picker: rail chips + picked table + seek + the dashed add block', async () => {
    renderPage()
    await screen.findByText('检查信息')
    // 方案 C (user-approved prototype 2026-11-24): the common-item rail sits RIGHT
    // of the picked table; chips toggle add/remove instead of graying out
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    expect(screen.getAllByText('腹部彩超').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '腹部彩超（已选，点击移除）' })).toBeInTheDocument()
    // the picked row deletes via the family trash icon (the ConsoleAction danger verb)
    fireEvent.click(screen.getByRole('button', { name: '删除 腹部彩超' }))
    expect(screen.queryByRole('button', { name: '删除 腹部彩超' })).toBeNull()
    // the picked table's ops header carries the family right-edge refresh glyph
    // (anchored via the trailing add-row's seek - the empty-state row is gone;
    // the aria-label lands on BOTH the select root and the inner input - take
    // the combobox role so the query stays single)
    const pickedTable = screen.getByRole('combobox', { name: '搜索检查项目' }).closest('table') as HTMLTableElement
    expect(within(pickedTable).getByLabelText('刷新列表')).toBeInTheDocument()
    // the header seek adds a NON-common item without opening the dialog.
    // aria-label lands on BOTH the .ant-select root and the inner input - take
    // the combobox role so the query stays single. The option label is a custom
    // node (no title attr) - match by textContent; the userEvent
    // click-combobox + click-option pairing is the suite-proven jsdom recipe.
    const seekUser = userEvent.setup()
    const seek = screen.getByRole('combobox', { name: '搜索检查项目' })
    await seekUser.click(seek)
    await seekUser.type(seek, '心脏')
    const seekOpt = await waitFor(() => {
      const el = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
        (o) => o.textContent?.includes('心脏彩超'),
      )
      if (!el) throw new Error('seek option 心脏彩超 not rendered')
      return el
    })
    await seekUser.click(seekOpt)
    await waitFor(() => {
      expect(screen.getAllByText('心脏彩超').length).toBeGreaterThan(0)
    })
    // the dashed-block entry (the 添加测量项 family pattern) opens the dialog;
    // the picker redesign (2026-11-24): rows toggle via the antd Checkbox
    // (role=checkbox, the whole row is clickable too) and the footer band
    // carries the live 已选/合计 summary
    fireEvent.click(screen.getByRole('button', { name: '添加项目' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getByRole('checkbox', { name: '选择 阑尾超声' }))
    expect(within(dialog).getByText('已选 2 项', { exact: false })).toBeInTheDocument()
    // the row itself toggles as well (click anywhere on the row)
    fireEvent.click(within(dialog).getByText('阑尾超声'))
    expect(within(dialog).getByText('已选 1 项', { exact: false })).toBeInTheDocument()
    fireEvent.click(within(dialog).getByText('阑尾超声'))
    fireEvent.click(within(dialog).getByRole('button', { name: '确认添加' }))
    await waitFor(() => {
      expect(screen.getAllByText('阑尾超声').length).toBeGreaterThan(0)
    })
  })

  it('the picker dialog filters by 检查部位 client-side', async () => {
    renderPage()
    await screen.findByText('检查信息')
    fireEvent.click(screen.getByRole('button', { name: '添加项目' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('腹部彩超')).toBeInTheDocument()
    // the 部位 Select narrows the list live (no server round trip)
    await userEvent.setup().click(within(dialog).getByRole('combobox', { name: '部位条件' }))
    fireEvent.click(await pickOption('心脏'))
    expect(within(dialog).getByText('心脏彩超')).toBeInTheDocument()
    expect(within(dialog).queryByText('腹部彩超')).toBeNull()
    expect(within(dialog).queryByText('阑尾超声')).toBeNull()
  })

  it('submit registers a walk-in (no bookingTime) and refreshes', async () => {
    renderPage()
    await screen.findByLabelText('日期导航')
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '赵六' } })
    fillRequiredDemo()
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalled())
    const body = vi.mocked(usOrdersApi.createOrder).mock.calls[0][0]
    expect(body.name).toBe('赵六')
    expect(body.bookingTime).toBeNull()
    expect(body.examItems.length).toBeGreaterThan(0)
    // the untouched HIS-origin trio submits as nulls (us-apply-info: blank = null)
    expect(body.ext?.applyDept).toBeNull()
    expect(body.ext?.applyDoctor).toBeNull()
    expect(body.ext?.applyTime).toBeNull()
  })

  it('submit posts bookingTime for pre-registration (the dialog date+slot pair)', async () => {
    renderPage()
    await screen.findByText('请检信息')
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '钱七' } })
    fillRequiredDemo()
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: '选择预约时段' }))
    const dialog = await screen.findByRole('dialog')
    // the D+1 default: the dialog opens with tomorrow pre-selected in the calendar
    // and its grid pre-queried
    expect(within(dialog).getByRole('button', { name: TOMORROW })).toBeInTheDocument()
    expect(
      vi.mocked(usOrdersApi.listSlotOptions).mock.calls.some(
        (call) => call[0] === TOMORROW && call[1] === 'US'),
    ).toBe(true)
    // pick 09:00 and confirm - the pair rides into the trigger summary
    await user.click(within(dialog).getByRole('button', { name: '预约时段 09:00–09:30，剩余 4 号' }))
    await user.click(within(dialog).getByRole('button', { name: /^确\s*定$/ }))
    expect(screen.getByText('09:00–09:30')).toBeInTheDocument()
    // the HIS application origin rides the ext block verbatim (us-apply-info)
    fireEvent.change(screen.getByLabelText('申请科室'), { target: { value: '门诊内科' } })
    fireEvent.change(screen.getByLabelText('申请医生'), { target: { value: '李临床' } })
    fireEvent.change(screen.getByLabelText('申请时间'), { target: { value: '2026-11-24 08:30:00' } })
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalled())
    const body = vi.mocked(usOrdersApi.createOrder).mock.calls[0][0]
    expect(body.bookingTime).toBe(`${TOMORROW} 09:00:00`)
    expect(body.ext?.applyDept).toBe('门诊内科')
    expect(body.ext?.applyDoctor).toBe('李临床')
    expect(body.ext?.applyTime).toBe('2026-11-24 08:30:00')
  })

  it('the dialog grays the full slot; the calendar marks day-level 余量 (D+1 floor)', async () => {
    renderPage()
    const user = userEvent.setup()
    const bookingTrigger = screen.getByRole('button', { name: '选择预约时段' })
    await waitFor(() => expect(bookingTrigger).toBeEnabled())
    await user.click(bookingTrigger)
    const dialog = await screen.findByRole('dialog')
    // the always-visible calendar: today disabled (D+1 floor), tomorrow carries
    // the client-aggregated day badge (stub: 12 cap - 7 booked = 余 5)
    expect(within(dialog).getByRole('button', { name: dayjs().format('YYYY-MM-DD') })).toBeDisabled()
    const tomorrowCell = within(dialog).getByRole('button', { name: TOMORROW })
    expect(tomorrowCell).toBeEnabled()
    await waitFor(() => expect(tomorrowCell).toHaveTextContent('余 5'))
    // the grouped grid carries the remaining counts and the day summary
    expect(within(dialog).getByText('上午')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: '预约时段 09:30–10:00，剩余 1 号' })).toBeInTheDocument()
    // the full slot renders disabled and refuses the pick (满员标灰 in the dialog)
    const fullCard = within(dialog).getByRole('button', { name: '预约时段 10:00–10:30，已满' })
    expect(fullCard).toHaveAttribute('aria-disabled', 'true')
    await user.click(fullCard)
    expect(within(dialog).getByRole('button', { name: /^确\s*定$/ })).toBeDisabled()
    // clicking a calendar day selects it and re-queries the day grid
    const monthEnd = dayjs().add(1, 'day').endOf('month').format('YYYY-MM-DD')
    await user.click(within(dialog).getByRole('button', { name: monthEnd }))
    await waitFor(() => {
      expect(vi.mocked(usOrdersApi.listSlotOptions).mock.calls.at(-1)?.[0]).toBe(monthEnd)
    })
  })

  it('clearing a picked booking (the trigger ×) returns the walk-in state and null bookingTime', async () => {
    renderPage()
    await screen.findByText('请检信息')
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '吴九' } })
    fillRequiredDemo()
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: '选择预约时段' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: '预约时段 09:00–09:30，剩余 4 号' }))
    await user.click(within(dialog).getByRole('button', { name: /^确\s*定$/ }))
    // the summary carries the pair; the trigger's × clears it back to walk-in
    expect(screen.getByText('09:00–09:30')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '清除预约时段' }))
    // back to the walk-in resting state: the summary is gone, the trigger stays live
    await waitFor(() => expect(screen.queryByText('09:00–09:30')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: '选择预约时段' })).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalled())
    expect(vi.mocked(usOrdersApi.createOrder).mock.calls[0][0].bookingTime).toBeNull()
  })

  it('an unconfigured modality disables the trigger (未开通预约) and walk-ins still submit', async () => {
    vi.mocked(usOrdersApi.listSlotOptions).mockResolvedValue({ configured: false, slots: [] })
    renderPage()
    const trigger = await screen.findByRole('button', { name: '选择预约时段' })
    await waitFor(() => expect(trigger).toBeDisabled())
    expect(screen.getByText('未开通预约')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '郑十' } })
    fillRequiredDemo()
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalled())
    expect(vi.mocked(usOrdersApi.createOrder).mock.calls[0][0].bookingTime).toBeNull()
  })

  it('the slot refusals surface verbatim and keep the form', async () => {
    vi.mocked(usOrdersApi.createOrder).mockRejectedValueOnce(
      new ApiError('A0400', '该时段已满，请选择其他时段'),
    )
    renderPage()
    await screen.findByText('请检信息')
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '孙八' } })
    fillRequiredDemo()
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    expect(await screen.findByText('该时段已满，请选择其他时段')).toBeInTheDocument()
    // the form stays (the filled name survives the refusal)
    expect((screen.getByLabelText('姓名') as HTMLInputElement).value).toBe('孙八')
  })

  describe('出生日期 two-way conversion (us-registration-birthdate-web)', () => {
    /** The house DatePicker commit: open the panel, type the ISO date, Enter. */
    function commitBirthDate(iso: string) {
      const input = screen.getByLabelText('出生日期') as HTMLInputElement
      fireEvent.mouseDown(input)
      fireEvent.change(input, { target: { value: iso } })
      fireEvent.keyDown(input, { key: 'Enter', keyCode: 13 })
    }

    const birthInput = () => screen.getByLabelText('出生日期') as HTMLInputElement
    const ageField = (label: string) => screen.getByLabelText(label) as HTMLInputElement

    it('diffYmd fixed oracles (leap-day anniversary + exact spans)', () => {
      expect(diffYmd(dayjs('1990-05-20'), dayjs('2026-11-25'))).toEqual([36, 6, 5])
      // the Feb-29 asymmetry: the remainder never leaks 12 into the months slot
      expect(diffYmd(dayjs('2000-02-29'), dayjs('2028-02-28'))).toEqual([28, 0, 0])
      expect(diffYmd(dayjs('2026-11-25'), dayjs('2026-11-25'))).toEqual([0, 0, 0])
    })

    it('clearing the picker resets the derived age fields', async () => {
      renderPage()
      await screen.findByText('请检信息')
      commitBirthDate('1990-05-20')
      expect(ageField('年龄-岁').value).not.toBe('')
      fireEvent.mouseEnter(birthInput())
      const clear = await waitFor(() => {
        const el = document.querySelector('.ant-picker-clear')
        expect(el).not.toBeNull()
        return el as HTMLElement
      })
      fireEvent.click(clear)
      expect(birthInput().value).toBe('')
      expect(ageField('年龄-岁').value).toBe('')
      expect(ageField('年龄-天').value).toBe('')
    })

    it('a future or malformed archive birth date no-ops the backfill', async () => {
      renderPage()
      await screen.findByText('请检信息')
      vi.mocked(usOrdersApi.resolvePatients).mockResolvedValueOnce({
        result: 'MATCHED',
        identity: {
          patUid: 992, patId: 'US3-BD2', state: 'RESOLVED', name: 'us3坏生日',
          gender: 1, birthDate: '2990-01-01', cardNo: 'CARD-BD2',
        },
        candidates: null,
      })
      fireEvent.change(screen.getByLabelText('解析键值'), { target: { value: 'ID-BD-2' } })
      fireEvent.click(screen.getByRole('button', { name: '解析查询' }))
      await screen.findByText(/已匹配档案/)
      expect(birthInput().value).toBe('')
      expect(ageField('年龄-天').value).toBe('')
    })

    it('picker commit converts the age inputs exactly', async () => {
      renderPage()
      await screen.findByText('请检信息')
      commitBirthDate('1990-05-20')
      const [y, m, d] = diffYmd(dayjs('1990-05-20'), dayjs())
      expect(ageField('年龄-岁').value).toBe(y > 0 ? String(y) : '')
      expect(ageField('年龄-月').value).toBe(m > 0 ? String(m) : '')
      expect(ageField('年龄-天').value).toBe(String(d))
      expect(birthInput().value).toBe('1990-05-20')
    })

    it('age edit re-pins the picker year, month/day preserved', async () => {
      renderPage()
      await screen.findByText('请检信息')
      commitBirthDate('1990-05-20')
      fireEvent.change(screen.getByLabelText('年龄-岁'), { target: { value: '50' } })
      expect(birthInput().value).toBe(`${dayjs().year() - 50}-05-20`)
    })

    it('future dates are disabled in the panel', async () => {
      const user = userEvent.setup()
      renderPage()
      await screen.findByText('请检信息')
      await user.click(screen.getByLabelText('出生日期'))
      const tomorrow = dayjs().add(1, 'day').format('YYYY-MM-DD')
      const cell = await waitFor(() => {
        const el = document.querySelector(`.ant-picker-cell[title="${tomorrow}"]`)
        expect(el).not.toBeNull()
        return el as HTMLElement
      })
      expect(cell.className).toContain('ant-picker-cell-disabled')
    })

    it('MATCHED identity backfills the picker and the converted age', async () => {
      renderPage()
      await screen.findByText('请检信息')
      vi.mocked(usOrdersApi.resolvePatients).mockResolvedValueOnce({
        result: 'MATCHED',
        identity: {
          patUid: 991, patId: 'US3-BD', state: 'RESOLVED', name: 'us3生日回填',
          gender: 1, birthDate: '1991-03-10', idType: '01', idNo: 'ID-BD-1',
          cardNo: 'CARD-BD', phone: '13800000001',
        },
        candidates: null,
      })
      fireEvent.change(screen.getByLabelText('解析键值'), { target: { value: 'ID-BD-1' } })
      fireEvent.click(screen.getByRole('button', { name: '解析查询' }))
      await screen.findByText(/已匹配档案/)
      expect(birthInput().value).toBe('1991-03-10')
      const [y, m, d] = diffYmd(dayjs('1991-03-10'), dayjs())
      expect(ageField('年龄-岁').value).toBe(y > 0 ? String(y) : '')
      expect(ageField('年龄-月').value).toBe(m > 0 ? String(m) : '')
      expect(ageField('年龄-天').value).toBe(String(d))
    })

    it('submit posts birthDate as yyyy-MM-dd, unset posts null', async () => {
      renderPage()
      await screen.findByText('请检信息')
      fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '赵生日' } })
      fillRequiredDemo()
      fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
      commitBirthDate('1990-05-20')
      fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
      await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalled())
      expect(vi.mocked(usOrdersApi.createOrder).mock.calls[0][0].birthDate).toBe('1990-05-20')
      // the reset workbench submits null without a picker value
      fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '钱无生日' } })
      fillRequiredDemo()
      fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
      fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
      await waitFor(() => expect(vi.mocked(usOrdersApi.createOrder).mock.calls.length).toBe(2))
      expect(vi.mocked(usOrdersApi.createOrder).mock.calls[1][0].birthDate).toBeNull()
    })
  })
})


describe('wide state + saved searches (us-registration-redesign batches 3-4)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    invalidateDictItemsCache()
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    // The dict transport stub: 1004 性别 / 1006 证件类型 (the V6 seed's stable
    // rcp ca_id anchors); unknown catalogs resolve empty.
    // the booking dialog's slot grid stub (us-booking-slot-picker): 09:30 rides
    // tight (仅剩 1 号) and 10:00 at capacity - the 满员标灰 render asserted
    vi.mocked(usOrdersApi.listSlotOptions).mockResolvedValue({
      configured: true,
      slots: [
        { start: '09:00', end: '09:30', booked: 0, capacity: 4 },
        { start: '09:30', end: '10:00', booked: 3, capacity: 4 },
        { start: '10:00', end: '10:30', booked: 4, capacity: 4 },
      ],
    })
    vi.mocked(dictsApi.fetchDictItemsById).mockImplementation(async (catalId: number) => {
      if (catalId === 1004) {
        return [
          { code: 100401, value: '男' },
          { code: 100402, value: '女' },
          { code: 100403, value: '未知' },
        ]
      }
      if (catalId === 1006) {
        return [
          { code: 100601, value: '居民身份证' },
          { code: 100602, value: '护照' },
        ]
      }
      if (catalId === 1023) {
        return [
          { code: 1, value: '门诊' },
          { code: 2, value: '住院' },
          { code: 3, value: '急诊' },
          { code: 4, value: '体检' },
        ]
      }
      if (catalId === 1002) {
        return [
          { code: 1, value: '未孕' },
          { code: 2, value: '早孕' },
          { code: 3, value: '中晚孕' },
          { code: 4, value: '未知' },
        ]
      }
      // the 1031 登记单状态 catalog (registration-order-status-dict-sev V55): the
      // six machine statuses as code 103101-103106 with the DB-configured colors
      if (catalId === 1031) {
        return [
          { code: 103101, value: '已登记', tag: 'blue' },
          { code: 103102, value: '检查中', tag: 'amber' },
          { code: 103103, value: '诊断中', tag: 'amber' },
          { code: 103104, value: '已完成', tag: 'green' },
          { code: 103105, value: '已打印', tag: 'green' },
          { code: 103106, value: '已取消', tag: 'gray' },
        ]
      }
      return []
    })
    vi.mocked(examItemApi.pageExamItems).mockResolvedValue({
      records: US_ITEMS,
      total: US_ITEMS.length,
      size: 20,
      current: 1,
      pages: 1,
    } as never)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({ orderId: 11, status: 'REGISTERED', bookingTime: '2026-11-25 09:17:00' }),
        orderView({ orderId: 12, status: 'REGISTERED', accessionNo: 'US26000002' }),
      ]) as never,
    )
    vi.mocked(usOrdersApi.statusCounts).mockResolvedValue(ZERO_COUNTS)
    vi.mocked(searchFiltersApi.listFilters).mockResolvedValue([])
    vi.mocked(searchFiltersApi.createFilter).mockImplementation(async (body) => ({
      id: 501,
      pageKey: body.pageKey,
      name: body.name,
      query: body.query ?? {},
      isDefault: Boolean(body.isDefault),
      updateTime: '2026-11-23 10:00:00',
    }))
    vi.mocked(searchFiltersApi.setDefaultFilter).mockImplementation(async (id) => ({
      id,
      pageKey: 'us-registration',
      name: 'x',
      query: {},
      isDefault: true,
      updateTime: '2026-11-23 10:00:00',
    }))
  })

  async function openWide() {
    renderPage()
    await screen.findAllByLabelText('日期导航')
    fireEvent.click(screen.getByLabelText('工作台切换'))
    await screen.findByText(/更多条件/)
  }

  it('the wide conditions compose into the page query (primary + collapsed extras)', async () => {
    await openWide()
    fireEvent.change(screen.getByLabelText('检查号条件'), { target: { value: 'US2600' } })
    fireEvent.change(screen.getByLabelText('患者姓名条件'), { target: { value: '张' } })
    fireEvent.click(screen.getByRole('button', { name: '更多条件' }) ?? screen.getByLabelText('更多条件'))
    fireEvent.change(screen.getByLabelText('影像号条件'), { target: { value: 'P00000100' } })
    fireEvent.change(screen.getByLabelText('就诊卡号条件'), { target: { value: 'CARD-9' } })
    fireEvent.change(screen.getByLabelText('项目名称条件'), { target: { value: '胆囊' } })
    fireEvent.click(screen.getByRole('button', { name: '查询' }))
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.accessionNo).toBe('US2600')
      expect(call?.patientName).toBe('张')
      expect(call?.patientId).toBe('P00000100')
      expect(call?.cardNo).toBe('CARD-9')
      expect(call?.examItemName).toBe('胆囊')
    })
  })

  it('the apply-time range rides the wide row and the shared date state feeds it by default', async () => {
    await openWide()
    fireEvent.click(screen.getByRole('button', { name: '更多条件' }) ?? screen.getByLabelText('更多条件'))
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.applyTimeStart).toMatch(/^\d{4}-\d{2}-\d{2} 00:00:00$/)
    })
    fireEvent.change(screen.getByLabelText('创建时间起'), { target: { value: '2026-11-01 00:00:00' } })
    fireEvent.change(screen.getByLabelText('创建时间止'), { target: { value: '2026-11-30 23:59:59' } })
    fireEvent.click(screen.getByRole('button', { name: '查询' }))
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.applyTimeStart).toBe('2026-11-01 00:00:00')
      expect(call?.applyTimeEnd).toBe('2026-11-30 23:59:59')
    })
  })

  it('saved searches persist, apply, and the default auto-applies on mount', async () => {
    vi.mocked(searchFiltersApi.listFilters).mockResolvedValue([
      {
        id: 501,
        pageKey: 'us-registration',
        name: 'us3我的筛选项',
        query: { accessionNo: 'US2600', patientName: '张三' },
        isDefault: true,
        updateTime: '2026-11-23 10:00:00',
      },
    ])
    await openWide()
    // the default search auto-applied: the list query carries its conditions
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.accessionNo).toBe('US2600')
      expect(call?.patientName).toBe('张三')
    })
    fireEvent.click(screen.getByRole('button', { name: '保存条件' }))
    const nameInput = await screen.findByLabelText('筛选项名称')
    fireEvent.change(nameInput, { target: { value: 'us3新筛选项' } })
    fireEvent.click(screen.getByRole('button', { name: /^确\s*定$/ }))
    await waitFor(() => expect(searchFiltersApi.createFilter).toHaveBeenCalled())
    vi.mocked(searchFiltersApi.listFilters).mockResolvedValue([
      {
        id: 501,
        pageKey: 'us-registration',
        name: 'us3我的筛选项',
        query: { accessionNo: 'US2600' },
        isDefault: false,
        updateTime: '2026-11-23 10:00:00',
      },
      {
        id: 502,
        pageKey: 'us-registration',
        name: 'us3新筛选项',
        query: { patientName: '张三' },
        isDefault: false,
        updateTime: '2026-11-23 10:05:00',
      },
    ])
    fireEvent.click(screen.getByRole('button', { name: '我的查询' }))
    // the 设默认 full-replace: the row's button, not the row (the row applies)
    const row = (await screen.findByText('us3新筛选项')).closest('div') as HTMLElement
    fireEvent.click(within(row).getByRole('button', { name: '设默认' }))
    await waitFor(() => expect(searchFiltersApi.setDefaultFilter).toHaveBeenCalledWith(502))
  })

  it('column settings persist to localStorage', async () => {
    await openWide()
    fireEvent.click(screen.getByRole('button', { name: '列设置' }))
    fireEvent.click(screen.getByRole('menuitem', { name: '创建时间' }))
    expect(JSON.parse(localStorage.getItem('us-registration.columns') ?? '{}')).toMatchObject({
      createTime: true, // the first toggle HIDES the column
    })
  })
})

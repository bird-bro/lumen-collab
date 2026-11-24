import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UsRegistrationPage from '@/pages/us/registration'
import * as usOrdersApi from '@/api/us-orders'
import type { UsOrderStatus, UsOrderView } from '@/api/us-orders'
import * as examItemApi from '@/api/exam-item'
import * as searchFiltersApi from '@/api/search-filters'
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

const FULL_PERMS = ['us:order:list', 'us:order:create', 'us:order:update', 'us:order:cancel']

function orderView(partial: Partial<UsOrderView> & { orderId: number }): UsOrderView {
  return {
    accessionNo: 'US26000001',
    status: 'BOOKED',
    patientUid: 101,
    patientSnapshot: { name: '张三', patId: 'P00000100', gender: 1, phone: '13800000001' },
    examItems: [{ itemId: 1, itemName: '腹部彩超' }],
    emgFlag: 0,
    cancelReason: null,
    createTime: '2026-11-20 15:00:00',
    bookingTime: null,
    pregnancyStatus: null,
    gestationalWeeks: null,
    note: null,
    ...partial,
  }
}

function pageFixture(records: UsOrderView[]) {
  return { records, total: records.length, size: 20, current: 1, pages: 1 }
}

const ZERO_COUNTS: Record<UsOrderStatus, number> = {
  BOOKED: 0,
  REGISTERED: 0,
  IN_PROGRESS: 0,
  COMPLETED: 0,
  BOOKING_CANCELLED: 0,
  CANCELLED: 0,
}

const US_ITEMS = [
  { id: 1, exam: '腹部彩超', modality: 'US', isCommon: true },
  { id: 2, exam: '心脏彩超', modality: 'US', isCommon: false },
]

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
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    vi.mocked(examItemApi.pageExamItems).mockResolvedValue({
      records: US_ITEMS,
      total: US_ITEMS.length,
      size: 20,
      current: 1,
      pages: 1,
    } as never)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({ orderId: 11, status: 'BOOKED', bookingTime: '2026-11-25 09:17:00' }),
        orderView({ orderId: 12, status: 'REGISTERED', accessionNo: 'US26000002' }),
      ]) as never,
    )
    vi.mocked(usOrdersApi.statusCounts).mockResolvedValue({
      ...ZERO_COUNTS,
      BOOKED: 1,
      REGISTERED: 1,
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

describe('compact workbench behaviors (us-registration-redesign batch 2)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    vi.mocked(examItemApi.pageExamItems).mockResolvedValue({
      records: US_ITEMS,
      total: US_ITEMS.length,
      size: 20,
      current: 1,
      pages: 1,
    } as never)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({ orderId: 11, status: 'BOOKED', bookingTime: '2026-11-25 09:17:00' }),
        orderView({ orderId: 12, status: 'REGISTERED', accessionNo: 'US26000002' }),
      ]) as never,
    )
    vi.mocked(usOrdersApi.statusCounts).mockResolvedValue({
      ...ZERO_COUNTS,
      BOOKED: 1,
      REGISTERED: 1,
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
      bookingTime: null,
    })
    vi.mocked(usOrdersApi.registerOrder).mockResolvedValue(true)
    vi.mocked(usOrdersApi.cancelOrder).mockResolvedValue(true)
  })

  it('the chips carry the counts and multi-select toggles the IN-filter', async () => {
    renderPage()
    await screen.findByLabelText('日期导航')
    // the default selection 预登记+已登记 rides the initial query
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.status?.join(',')).toBe('BOOKED,REGISTERED')
    })
    // the chips render the zero-filled meter
    expect(screen.getByRole('button', { name: '预登记 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '已完成 0' })).toBeInTheDocument()
    // toggling the 预登记 chip drops BOOKED from the IN-filter
    fireEvent.click(screen.getByRole('button', { name: /预登记/ }))
    await waitFor(() => {
      const call = vi.mocked(usOrdersApi.pageOrders).mock.calls.at(-1)?.[0]
      expect(call?.status?.join(',')).toBe('REGISTERED')
    })
  })

  it('the rail renders dense rows with the arrival/cancel actions', async () => {
    renderPage()
    // the BOOKED row carries 登记 (the arrival confirmation)
    const bookedRow = (await screen.findAllByText('张三'))[0].closest('tr') as HTMLElement
    expect(within(bookedRow).getByText('预登记')).toBeInTheDocument()
    expect(within(bookedRow).getByRole('button', { name: /^登记/ })).toBeInTheDocument()
    fireEvent.click(within(bookedRow).getByRole('button', { name: /^登记/ }))
    await waitFor(() => expect(usOrdersApi.registerOrder).toHaveBeenCalledWith(11))
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

  it('section 2 hosts the 急诊 switch, 预登记时间 picker, and 临床诊断', async () => {
    renderPage()
    await screen.findByText('请检信息')
    expect(screen.getByLabelText('急诊')).toBeInTheDocument()
    expect(screen.getByLabelText('预登记日期')).toBeInTheDocument()
    expect(screen.getByLabelText('预登记时段')).toBeInTheDocument()
    expect(screen.getByLabelText('临床诊断')).toBeInTheDocument()
  })

  it('the pregnancy cell is gender-conditional (female only, weeks disabled when 未孕/未知)', async () => {
    renderPage()
    await screen.findByText('请检信息')
    // default gender unset: the pregnancy cell stays hidden
    expect(screen.queryByLabelText('妊娠状态')).not.toBeInTheDocument()
    // female: the cell appears (the prototype's gender radio dots)
    const user = userEvent.setup()
    await user.click(screen.getByRole('radio', { name: '女' }))
    expect((await screen.findAllByLabelText('妊娠状态')).length).toBeGreaterThan(0)
    // 未孕 disables the weeks input
    await user.click(screen.getAllByRole('combobox', { name: '妊娠状态' })[0])
    await user.click(await pickOption('未孕'))
    expect(screen.getAllByLabelText('孕周')[0]).toBeDisabled()
  })

  it('section 3 renders common chips + the picked list + the add dialog', async () => {
    renderPage()
    await screen.findByText('检查信息')
    // the common-item chips render the isCommon catalog rows
    expect(screen.getByRole('button', { name: '腹部彩超' })).toBeInTheDocument()
    // picking a chip adds to the picked list
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    expect(screen.getAllByText('腹部彩超').length).toBeGreaterThan(0)
    // the 添加项目 dialog opens and adds the non-common row
    fireEvent.click(screen.getByRole('button', { name: '添加项目' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getAllByText('心脏彩超')[0])
    fireEvent.click(within(dialog).getByRole('button', { name: '确认添加' }))
    await waitFor(() => {
      expect(screen.getAllByText('心脏彩超').length).toBeGreaterThan(0)
    })
  })

  it('submit registers a walk-in (no bookingTime) and refreshes', async () => {
    renderPage()
    await screen.findByLabelText('日期导航')
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '赵六' } })
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalled())
    const body = vi.mocked(usOrdersApi.createOrder).mock.calls[0][0]
    expect(body.name).toBe('赵六')
    expect(body.bookingTime).toBeNull()
    expect(body.examItems.length).toBeGreaterThan(0)
  })

  it('submit posts bookingTime for pre-registration', async () => {
    renderPage()
    await screen.findByText('请检信息')
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '钱七' } })
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    fireEvent.change(screen.getByLabelText('预登记日期'), { target: { value: '2026-11-25' } })
    fireEvent.change(screen.getByLabelText('预登记时段'), { target: { value: '09:17' } })
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalled())
    const body = vi.mocked(usOrdersApi.createOrder).mock.calls[0][0]
    expect(body.bookingTime).toBe('2026-11-25 09:17:00')
  })

  it('the slot refusals surface verbatim and keep the form', async () => {
    vi.mocked(usOrdersApi.createOrder).mockRejectedValueOnce(
      new ApiError('A0400', '该时段已满，请选择其他时段'),
    )
    renderPage()
    await screen.findByText('请检信息')
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '孙八' } })
    fireEvent.click(screen.getByRole('button', { name: '腹部彩超' }))
    fireEvent.click(screen.getByRole('button', { name: '提交登记' }))
    expect(await screen.findByText('该时段已满，请选择其他时段')).toBeInTheDocument()
    // the form stays (the filled name survives the refusal)
    expect((screen.getByLabelText('姓名') as HTMLInputElement).value).toBe('孙八')
  })
})


describe('wide state + saved searches (us-registration-redesign batches 3-4)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    vi.mocked(examItemApi.pageExamItems).mockResolvedValue({
      records: US_ITEMS,
      total: US_ITEMS.length,
      size: 20,
      current: 1,
      pages: 1,
    } as never)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([
        orderView({ orderId: 11, status: 'BOOKED', bookingTime: '2026-11-25 09:17:00' }),
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
    fireEvent.change(screen.getByLabelText('患者编号条件'), { target: { value: 'P00000100' } })
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
    fireEvent.change(screen.getByLabelText('申请时间起'), { target: { value: '2026-11-01 00:00:00' } })
    fireEvent.change(screen.getByLabelText('申请时间止'), { target: { value: '2026-11-30 23:59:59' } })
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

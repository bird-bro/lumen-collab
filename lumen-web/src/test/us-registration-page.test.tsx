import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UsRegistrationPage from '@/pages/us/registration'
import * as usOrdersApi from '@/api/us-orders'
import type { UsOrderView } from '@/api/us-orders'
import * as examItemApi from '@/api/exam-item'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'
import { resolveMenuRoute } from '@/routes/menu-paths'

/**
 * US 工作站登记页 (us-station-registration-web tasks 1.1-2.3; spec delta
 * `web-us-station`): the workstation registration page against the REAL
 * `/api/v1/us/**` seam (mocked here at the api seam - no page data flows
 * through a mock at runtime, ruling 2026-11-05), the resolution-chain flow
 * (exact / candidates / none + the 急诊占位 toggle), the order form with the
 * US item catalog + the pregnancy extension, the list with 登记/取消 actions
 * and verbatim backend refusals, and the `us:order:*` perm gates.
 */
vi.mock('@/api/us-orders')
vi.mock('@/api/exam-item', async (importOriginal) => ({
  ...(await importOriginal<typeof examItemApi>()),
  pageExamItems: vi.fn(),
}))

const FULL_PERMS = ['us:order:list', 'us:order:create', 'us:order:update', 'us:order:cancel']

function identity(partial: Record<string, unknown> = {}) {
  return {
    patUid: 101,
    patId: 'P00000100',
    state: 'RESOLVED',
    idType: '身份证',
    idNo: '110101199001011234',
    name: '张三',
    gender: 1,
    birthDate: '1990-01-01',
    phone: '13800000001',
    ...partial,
  }
}

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
    pregnancyStatus: null,
    gestationalWeeks: null,
    note: null,
    ...partial,
  }
}

function pageFixture(records: UsOrderView[]) {
  return { records, total: records.length, size: 20, current: 1, pages: 1 }
}

const US_ITEMS = [
  { id: 1, exam: '腹部彩超', modality: 'US' },
  { id: 2, exam: '心脏彩超', modality: 'US' },
  { id: 3, exam: '胸片', modality: 'CR' },
]

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <UsRegistrationPage />
    </ConfigProvider>,
  )
}

describe('US workstation registration page (spec: web-us-station)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    vi.mocked(examItemApi.pageExamItems).mockResolvedValue({ records: US_ITEMS, total: US_ITEMS.length, size: 20, current: 1, pages: 1 } as never)
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(pageFixture([]) as never)
    vi.mocked(usOrdersApi.resolvePatients).mockResolvedValue({
      result: 'NONE',
      identity: null,
      candidates: null,
    })
  })

  it('maps the seeded component key to the /us/registration route', () => {
    expect(
      resolveMenuRoute({ mid: 1, type: 'C', path: 'registration', component: 'us/registration/index' } as never),
    ).toBe('/us/registration')
  })

  it('renders the order list against the real seam and gates affordances on us:order:create', async () => {
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([orderView({ orderId: 1 })]) as never,
    )
    renderPage()
    expect(await screen.findByLabelText('解析键值')).toBeInTheDocument()
    expect(await screen.findByText('张三')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /新建登记/ })).toBeInTheDocument()
    // a user without us:order:create sees no 新建登记 affordance
    useAuthStore.getState().setSession({ perms: ['us:order:list'] })
    renderPage()
    await waitFor(() => expect(screen.queryByRole('button', { name: /新建登记/ })).toBeNull())
  })

  it('fills the demographics on an exact id_no match (MATCHED)', async () => {
    vi.mocked(usOrdersApi.resolvePatients).mockResolvedValue({
      result: 'MATCHED',
      identity: identity(),
      candidates: null,
    } as never)
    renderPage()
    await screen.findByLabelText('解析键值')
    await userEvent.setup().type(await screen.findByLabelText('解析键值'), '110101199001011234')
    await userEvent.setup().click(screen.getByLabelText('解析查询'))
    const drawer = await screen.findByRole('dialog')
    expect(await within(drawer).findByDisplayValue('张三')).toBeInTheDocument()
    expect(await within(drawer).findByDisplayValue('13800000001')).toBeInTheDocument()
    expect(usOrdersApi.resolvePatients).toHaveBeenCalledWith(
      expect.objectContaining({ idNo: '110101199001011234' }),
    )
  })

  it('offers the candidate list on a soft match and fills on pick (CANDIDATES)', async () => {
    vi.mocked(usOrdersApi.resolvePatients).mockResolvedValue({
      result: 'CANDIDATES',
      identity: null,
      candidates: [identity({ patUid: 201, name: '李四A', phone: '13800000002' }), identity({ patUid: 202, name: '李四B', phone: '13800000003' })],
    } as never)
    renderPage()
    await screen.findByLabelText('解析键值')
    await userEvent.setup().type(await screen.findByLabelText('解析键值'), '李四')
    await userEvent.setup().click(screen.getByLabelText('解析查询'))
    const modal = await screen.findByRole('dialog', { name: '请选择患者档案' })
    expect(await within(modal).findByText('李四A')).toBeInTheDocument()
    await userEvent.setup().click(within(modal).getByText('李四B'))
    // the drawer panel: antd doesn't forward aria-label to the role=dialog wrapper -
    // scope by the .ant-drawer-content class instead (the title is visible there)
    const drawer = await waitFor(() => {
      const el = document.querySelector('.ant-drawer-content') as HTMLElement | null
      expect(el).toBeTruthy()
      return el as HTMLElement
    })
    expect(await within(drawer).findByDisplayValue('李四B')).toBeInTheDocument()
    expect(within(drawer).getByText(/P0000/)).toBeInTheDocument()
  })

  it('opens an empty demographics form with the 急诊占位 toggle on no match (NONE)', async () => {
    renderPage()
    await screen.findByLabelText('解析键值')
    await userEvent.setup().type(await screen.findByLabelText('解析键值'), '999999')
    await userEvent.setup().click(screen.getByLabelText('解析查询'))
    const drawer = await waitFor(() => {
      const el = document.querySelector('.ant-drawer-content') as HTMLElement | null
      expect(el).toBeTruthy()
      return el as HTMLElement
    })
    expect(await within(drawer).findByLabelText('急诊占位')).toBeInTheDocument()
    // the placeholder toggle disables the name input (unnamed PROVISIONAL)
    await userEvent.setup().click(within(drawer).getByLabelText('急诊占位'))
    expect(within(drawer).getByLabelText('姓名')).toBeDisabled()
  })

  it('submits the create body with items, extension, and emg flag, and surfaces the accession', async () => {
    vi.mocked(usOrdersApi.createOrder).mockResolvedValue({ orderId: 9, accessionNo: 'US26000009' } as never)
    renderPage()
    await screen.findByLabelText('解析键值')
    await userEvent.setup().type(await screen.findByLabelText('解析键值'), '999999')
    await userEvent.setup().click(screen.getByLabelText('解析查询'))
    const drawer = await waitFor(() => {
      const el = document.querySelector('.ant-drawer-content') as HTMLElement | null
      expect(el).toBeTruthy()
      return el as HTMLElement
    })
    const user = userEvent.setup()
    await user.type(within(drawer).getByLabelText('姓名'), '王五')
    await user.type(within(drawer).getByLabelText('手机号'), '13900000000')
    await pickOptionIn(drawer, '检查项目', '腹部彩超')
    await pickOptionIn(drawer, '检查项目', '心脏彩超')
    await pickOptionIn(drawer, '妊娠状态', '早孕')
    await user.type(within(drawer).getByLabelText('孕周'), '12')
    await user.click(within(drawer).getByLabelText('急诊'))
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await waitFor(() => expect(usOrdersApi.createOrder).toHaveBeenCalledTimes(1))
    expect(usOrdersApi.createOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '王五',
        phone: '13900000000',
        examItems: [
          { itemId: 1, itemName: '腹部彩超' },
          { itemId: 2, itemName: '心脏彩超' },
        ],
        emgFlag: 1,
        ext: expect.objectContaining({ pregnancyStatus: '早孕', gestationalWeeks: 12 }),
      }),
    )
    expect(await screen.findByText(/US26000009/)).toBeInTheDocument()
  })

  it('登记 transitions a BOOKED order and refreshes the list', async () => {
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([orderView({ orderId: 5, status: 'BOOKED' })]) as never,
    )
    vi.mocked(usOrdersApi.registerOrder).mockResolvedValue(true as never)
    renderPage()
    await screen.findByLabelText('解析键值')
    fireEvent.click(screen.getByLabelText('登记'))
    await waitFor(() => expect(usOrdersApi.registerOrder).toHaveBeenCalledWith(5))
    await waitFor(() => expect(usOrdersApi.pageOrders).toHaveBeenCalledTimes(2))
  })

  it('取消 requires the reason input and posts it (confirm-first)', async () => {
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([orderView({ orderId: 6, status: 'BOOKED' })]) as never,
    )
    vi.mocked(usOrdersApi.cancelOrder).mockResolvedValue(true as never)
    renderPage()
    await screen.findByLabelText('解析键值')
    await userEvent.setup().click(screen.getByLabelText('取消'))
    const modal = await screen.findByRole('dialog', { name: /取消登记单/ })
    await userEvent.setup().type(within(modal).getByLabelText('取消原因'), '重复登记')
    await userEvent.setup().click(within(modal).getByRole('button', { name: /确 定|确定/ }))
    await waitFor(() => expect(usOrdersApi.cancelOrder).toHaveBeenCalledWith(6, '重复登记'))
  })

  it('surfaces a backend state-machine refusal verbatim and keeps the row', async () => {
    vi.mocked(usOrdersApi.pageOrders).mockResolvedValue(
      pageFixture([orderView({ orderId: 7, status: 'BOOKED' })]) as never,
    )
    vi.mocked(usOrdersApi.registerOrder).mockRejectedValue(
      new ApiError('A0702', '当前状态不允许登记'),
    )
    renderPage()
    await screen.findByLabelText('解析键值')
    fireEvent.click(screen.getByLabelText('登记'))
    expect(await screen.findByText('当前状态不允许登记')).toBeInTheDocument()
    // the row stays (no refresh wipe, no navigation)
    expect(screen.getByText('US26000001')).toBeInTheDocument()
  })
})

/** Option picking: the combobox lives in the drawer, its dropdown portals to the
 * body - open it, then click the option by VISIBLE TEXT (the title-attribute match
 * proved unreliable for the multiple-mode select inside the drawer). */
async function pickOptionIn(scope: HTMLElement, label: string, title: string): Promise<void> {
  const user = userEvent.setup()
  await user.click(within(scope).getByLabelText(label))
  // the option ROOT's textContent gets polluted by rc-select internals (a stray
  // "12" prefix observed mid-suite) and the title attribute is unreliable for the
  // multiple-mode select - match by SUBSTRING over the node text instead.
  const option = await waitFor(() => {
    const match = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
      (node) => node.textContent?.includes(title),
    )
    expect(match).toBeTruthy()
    return match as HTMLElement
  })
  await user.click(option)
}

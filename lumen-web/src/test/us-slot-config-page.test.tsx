import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SlotConfigPage from '@/pages/setting/exam-slot'
import * as slotConfigApi from '@/api/us-slot-configs'
import type { SlotConfigView } from '@/api/us-slot-configs'
import * as dictItems from '@/utils/dict-items'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 预约设置 page (us-registration-redesign task 5.1-5.3; spec delta
 * `web-system-console` § 预约设置 Page — 拍板 06-A): the per-modality policy CRUD
 * against the REAL `/api/v1/us/slot-configs` seam (mocked at the api seam - no
 * page data flows through a mock at runtime, ruling 2026-11-05), the
 * `setting:slot:*` affordance gating, the dict-1024 modality SELECT (immutable on
 * edit), the windows pair editor with the fail-closed hint, and the soft-delete
 * confirmation. The duplicate-token A0400 surfaces verbatim with the drawer kept.
 */
vi.mock('@/api/us-slot-configs')
vi.mock('@/utils/dict-items', () => ({
  MODALITY_DICT_ID: 1024,
  dictItemsById: vi.fn(),
}))

const FULL_PERMS = ['setting:slot:list', 'setting:slot:create', 'setting:slot:update', 'setting:slot:delete']

const DICT_ITEMS = [
  { id: 1101, label: 'US', value: 'US' },
  { id: 1102, label: 'CT', value: 'CT' },
]

function config(partial: Partial<SlotConfigView> & { id: number }): SlotConfigView {
  return {
    modality: 'US',
    granularityMinutes: 30,
    slotCapacity: 4,
    openWindows: [
      { start: '08:00', end: '12:00' },
      { start: '14:00', end: '17:00' },
    ],
    ...partial,
  }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <SlotConfigPage />
    </ConfigProvider>,
  )
}

describe('预约设置 page (spec: web-system-console)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().setSession({ perms: FULL_PERMS })
    vi.mocked(dictItems.dictItemsById).mockResolvedValue(DICT_ITEMS as never)
    vi.mocked(slotConfigApi.listSlotConfigs).mockResolvedValue([
      config({ id: 1 }),
      config({ id: 2, modality: 'CT', openWindows: [] }),
    ])
    vi.mocked(slotConfigApi.createSlotConfig).mockResolvedValue(config({ id: 9 }))
    vi.mocked(slotConfigApi.updateSlotConfig).mockResolvedValue(config({ id: 1 }))
    vi.mocked(slotConfigApi.deleteSlotConfig).mockResolvedValue(undefined)
  })

  it('lists the live policies with decoded window pairs', async () => {
    renderPage()
    expect(await screen.findByText('US')).toBeInTheDocument()
    expect(screen.getByText('CT')).toBeInTheDocument()
    expect(screen.getAllByText('30').length).toBeGreaterThan(0)
    expect(screen.getAllByText('4').length).toBeGreaterThan(0)
    // the windows decode as the structured pair list
    expect(screen.getByText('08:00–12:00')).toBeInTheDocument()
    expect(screen.getByText('14:00–17:00')).toBeInTheDocument()
    expect(screen.getByText(/未放号/)).toBeInTheDocument() // the CT row's empty windows
  })

  it('affordances hide without the matching setting:slot perm', async () => {
    useAuthStore.getState().setSession({ perms: ['setting:slot:list'] })
    renderPage()
    expect(await screen.findByText('US')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /新\s*建/ })).not.toBeInTheDocument()
    expect(screen.queryAllByRole('button', { name: /编辑/ }).length).toBe(0)
  })

  it('create saves a trimmed policy through the drawer', async () => {
    const user = userEvent.setup()
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /新\s*建/ }))
    // the modality dict-1024 SELECT (the house combobox pattern)
    await user.click(await screen.findByRole('combobox', { name: '检查类型' }))
    await user.click((await screen.findAllByText('CT')).at(-1) as HTMLElement)
    const granularity = screen.getByLabelText('时段时长')
    fireEvent.change(granularity, { target: { value: '45' } })
    const capacity = screen.getByLabelText('号源数量')
    fireEvent.change(capacity, { target: { value: '6' } })
    // the window bounds are TimePickers: drive the INPUT itself (open panel →
    // type → Enter). Portal panel clicks are unsafe in jsdom — the previous
    // picker's leaving dropdown never finishes its exit motion, so a fresh
    // `.ant-picker-dropdown:not(-hidden)` query resolves to the stale panel.
    const pickTime = async (label: string, text: string) => {
      const input = screen.getByLabelText(label)
      await user.click(input) // opens the panel
      fireEvent.change(input, { target: { value: text } })
      fireEvent.keyDown(input, { key: 'Enter' })
    }
    await pickTime('窗口开始', '09:00')
    await pickTime('窗口结束', '12:00')
    fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }))
    await waitFor(() => expect(slotConfigApi.createSlotConfig).toHaveBeenCalled())
    const body = vi.mocked(slotConfigApi.createSlotConfig).mock.calls[0][0]
    expect(body.modality).toBe('CT')
    expect(body.granularityMinutes).toBe(45)
    expect(body.slotCapacity).toBe(6)
    expect(body.openWindows).toEqual([{ start: '09:00', end: '12:00' }])
  })

  it('the modality token is immutable on edit (the select renders disabled)', async () => {
    renderPage()
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0])
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: '检查类型' })).toBeDisabled()
    })
    // the stored token echoes in the drawer
    expect(screen.getAllByText('US').length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }))
    await waitFor(() => expect(slotConfigApi.updateSlotConfig).toHaveBeenCalledWith(1, expect.anything()))
  })

  it('the duplicate-token A0400 surfaces verbatim keeping the drawer', async () => {
    vi.mocked(slotConfigApi.createSlotConfig).mockRejectedValueOnce(
      new ApiError('A0400', '该模态已存在预约设置: US'),
    )
    const user = userEvent.setup()
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /新\s*建/ }))
    await user.click(await screen.findByRole('combobox', { name: '检查类型' }))
    await user.click((await screen.findAllByText('CT')).at(-1) as HTMLElement)
    fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }))
    expect(await screen.findByText('该模态已存在预约设置: US')).toBeInTheDocument()
    // the drawer stays open (the form fields survive)
    expect(screen.getByLabelText('号源数量')).toBeInTheDocument()
  })

  it('the drawer keeps the family chrome (vertical form) and pins the fail-closed hint above the footer (user rulings 2026-11-24)', async () => {
    renderPage()
    // the tool-row hint text is deleted (not needed)
    expect(screen.queryByText(/每模态一条预约策略/)).not.toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: /新\s*建/ }))
    // the dict-1024 dev placeholder is gone from the modality select
    expect(screen.queryByText('dict-1024 模态')).not.toBeInTheDocument()
    // the hint band pins just above the drawer footer: sticky wrapper seating
    // the ConsoleFormHint at the body bottom (flex body + margin-top auto) —
    // kept as a deliberate exception when the drawer was unified to the
    // family vertical form
    const hint = screen.getByText(/时段列表留空/)
    const stickyWrapper = hint.closest('div')!.parentElement as HTMLElement
    expect(stickyWrapper.style.position).toBe('sticky')
    expect(stickyWrapper.style.marginTop).toBe('auto')
    // the form is family-vertical: labels render above controls (no labelCol row)
    const drawerBody = hint.closest('.ant-drawer-body') as HTMLElement
    expect(drawerBody).not.toBeNull()
    expect(drawerBody.querySelector('.ant-form-vertical')).not.toBeNull()
    expect(drawerBody.querySelector('.ant-form-horizontal')).toBeNull()
    // the create form pre-seats the granularity at 60 分钟 (user ruling 2026-11-24)
    expect(screen.getByLabelText('时段时长')).toHaveValue('60')
    // the row-add button is the rep-template 添加节 idiom (user ruling
    // 2026-11-24, supersedes the same-day icon-only ruling): dashed block with
    // visible text; the per-row remove stays icon-only
    const addBtn = screen.getByRole('button', { name: '添加时段' })
    expect(addBtn).toBeInTheDocument()
    expect(addBtn.className).toContain('ant-btn-variant-dashed')
    expect(addBtn.className).toContain('ant-btn-block')
    expect(screen.queryByText('移除')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '移除时段' })).toBeInTheDocument()
  })

  it('the 检查类型 filter narrows the list in memory without a refetch (us-slot-list-filter)', async () => {
    const user = userEvent.setup()
    renderPage()
    expect(await screen.findByText('08:00–12:00')).toBeInTheDocument()
    expect(slotConfigApi.listSlotConfigs).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('combobox', { name: '检查类型过滤' }))
    await user.click((await screen.findAllByText('CT')).at(-1) as HTMLElement)
    // only the CT policy row remains - the US row's window tags are gone
    expect(screen.getByText('未放号（预约拒绝）')).toBeInTheDocument()
    expect(screen.queryByText('08:00–12:00')).not.toBeInTheDocument()
    // the narrowing rides the MOUNT-TIME payload (no additional list request)
    expect(slotConfigApi.listSlotConfigs).toHaveBeenCalledTimes(1)
    // clearing the filter restores the full list
    await user.click(document.querySelector('.ant-select-clear') as HTMLElement)
    expect(screen.getByText('08:00–12:00')).toBeInTheDocument()
  })

  it('delete soft-deletes with confirmation', async () => {
    const user = userEvent.setup()
    renderPage()
    const row = (await screen.findByText('CT')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: '删除' }))
    // the Popconfirm = the tooltip role (the house pattern)
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK|删/ }))
    await waitFor(() => expect(slotConfigApi.deleteSlotConfig).toHaveBeenCalledWith(2))
  })
})

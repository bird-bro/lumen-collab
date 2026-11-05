import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DictsPage from '@/pages/system/dicts'
import * as dictsApi from '@/api/dicts'
import { ApiError } from '@/api/client'
import type { DictCatal, DictDetail, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 字典管理 page (batch 6b, task 6.6; spec `specs/web-system-console`
 * § Dictionary Management: catalog CRUD with duplicate-`code` feedback and
 * per-catalog detail CRUD ordered by `sort`, laid out per the confirmed
 * prototype dicts-logs.html - catalog table with the detail table BENEATH).
 */
vi.mock('@/api/dicts')

function catal(partial: Partial<DictCatal> & { id: number; code: string; name: string }): DictCatal {
  return {
    enable: 1,
    sort: 0,
    remark: null,
    cls: null,
    initial: null,
    createBy: 1,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

function detail(partial: Partial<DictDetail> & { id: number; caCode: string; label: string }): DictDetail {
  return {
    enable: 1,
    caId: 1,
    code: null,
    value: null,
    color: null,
    css: null,
    sort: 1,
    back1: null,
    back2: null,
    remark: null,
    initial: null,
    createBy: 1,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

const CATALS = [catal({ id: 1, code: 'common_status', name: '通用状态' })]

const DETAILS = [
  detail({ id: 1, caCode: 'common_status', label: '启用', value: '1', sort: 1 }),
  detail({ id: 2, caCode: 'common_status', label: '停用', value: '0', sort: 2 }),
]

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <DictsPage />
    </ConfigProvider>,
  )
}

const DIALOG_LEAVING = 'ant-zoom-leave'

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({ perms: ['system:dict:list'] })
  vi.mocked(dictsApi.listCatal).mockResolvedValue({
    records: CATALS,
    total: 1,
    size: 20,
    current: 1,
    pages: 1,
  } as Page<DictCatal>)
  vi.mocked(dictsApi.listDetails).mockResolvedValue(DETAILS)
  vi.mocked(dictsApi.createCatal).mockResolvedValue(2)
  vi.mocked(dictsApi.updateCatal).mockResolvedValue(true)
  vi.mocked(dictsApi.deleteCatal).mockResolvedValue(true)
  vi.mocked(dictsApi.createDetail).mockResolvedValue(3)
  vi.mocked(dictsApi.updateDetail).mockResolvedValue(true)
  vi.mocked(dictsApi.deleteDetail).mockResolvedValue(true)
})

describe('dicts page - catalog (spec: Catalog list and duplicate feedback)', () => {
  it('renders the IPage catalog records with separate name and ID columns', async () => {
    renderPage()
    expect(await screen.findByText('通用状态')).toBeInTheDocument()
    // The ID renders in its own column (user request 2026-11-05; DB
    // auto-increment starts at 1000 - backend Flyway follow-up).
    const nameCell = screen.getByText('通用状态').closest('td')
    const row = nameCell?.closest('tr')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getByRole('cell', { name: '1' })).toBeInTheDocument()
  })

  it('creates a catalog and re-lists', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建目录/ }))
    await user.type(screen.getByLabelText('目录编码'), 'exam_type')
    await user.type(within(screen.getByRole('dialog')).getByLabelText('目录名称'), '检查类型')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(dictsApi.createCatal).toHaveBeenCalled())
    await waitFor(() => expect(dictsApi.listCatal).toHaveBeenCalledTimes(2))
  })

  it('lands a duplicate-code A0714 msg on the form and keeps the dialog open', async () => {
    const user = userEvent.setup()
    vi.mocked(dictsApi.createCatal).mockRejectedValue(
      new ApiError('A0714', '数据重复:dict catalog code already exists: common_status'),
    )
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建目录/ }))
    await user.type(screen.getByLabelText('目录编码'), 'common_status')
    await user.type(within(screen.getByRole('dialog')).getByLabelText('目录名称'), '重复')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(screen.getByText(/dict catalog code already exists/)).toBeInTheDocument())
    expect(screen.getByRole('dialog')).not.toHaveClass(DIALOG_LEAVING)
  })
})

describe('dicts page - details (spec: Details ordered by sort)', () => {
  it('loads a catalog\'s details ordered by sort when the row is selected', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('通用状态')
    await user.click(screen.getByText('通用状态'))
    await waitFor(() => expect(dictsApi.listDetails).toHaveBeenCalledWith('common_status'))
    expect(await screen.findByText('启用')).toBeInTheDocument()
    expect(screen.getByText('停用')).toBeInTheDocument()
    // sort order: 启用 (sort 1) renders before 停用 (sort 2)
    const first = screen.getAllByText(/启|停/)[0]
    expect(first).toHaveTextContent('启用')
  })

  it('creates a detail under the auto-selected catalog and re-lists', async () => {
    const user = userEvent.setup()
    renderPage()
    // Auto-select loads the first catalog's details on mount (user 2026-11-04).
    await screen.findByText('启用')
    const callsAfterAutoSelect = vi.mocked(dictsApi.listDetails).mock.calls.length
    await user.click(screen.getByRole('button', { name: /新建明细/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('标签'), '维护中')
    await user.type(within(screen.getByRole('dialog')).getByLabelText('数据值'), '2')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() =>
      expect(dictsApi.createDetail).toHaveBeenCalledWith(
        expect.objectContaining({ caId: 1, label: '维护中' }),
      ),
    )
    await waitFor(() => expect(vi.mocked(dictsApi.listDetails)).toHaveBeenCalledTimes(callsAfterAutoSelect + 1))
  })

  it('lands a duplicate-value A0714 msg on the form and keeps the dialog open', async () => {
    const user = userEvent.setup()
    vi.mocked(dictsApi.createDetail).mockRejectedValue(
      new ApiError('A0714', '数据重复:dict detail value already exists under caCode common_status: 1'),
    )
    renderPage()
    await screen.findByText('通用状态')
    await user.click(screen.getByText('通用状态'))
    await screen.findByText('启用')
    await user.click(screen.getByRole('button', { name: /新建明细/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('标签'), '重复')
    await user.type(within(screen.getByRole('dialog')).getByLabelText('数据值'), '1')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(screen.getByText(/dict detail value already exists/)).toBeInTheDocument())
    expect(screen.getByRole('dialog')).not.toHaveClass(DIALOG_LEAVING)
  })

  it('auto-selects the first catalog on mount (新建明细 enabled); an empty list keeps it disabled', async () => {
    const first = renderPage()
    // With data: the first row auto-selects, so the add action is available.
    await screen.findByText('启用')
    expect(screen.getByRole('button', { name: /新建明细/ })).toBeEnabled()
    first.unmount()
    // With an empty catalog list there is nothing to select - the guard holds.
    vi.mocked(dictsApi.listCatal).mockResolvedValue({
      records: [],
      total: 0,
      size: 20,
      current: 1,
      pages: 0,
    } as Page<DictCatal>)
    vi.mocked(dictsApi.listDetails).mockClear()
    const second = renderPage()
    await second.findByPlaceholderText('目录名称')
    expect(second.getByRole('button', { name: /新建明细/ })).toBeDisabled()
    second.unmount()
  })
})

describe('dicts page - perms gating', () => {
  it('hides mutation controls without system:dict:list but keeps the read', async () => {
    useAuthStore.getState().setSession({ perms: [] })
    renderPage()
    expect(await screen.findByText('通用状态')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /新建目录/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /新建明细/ })).not.toBeInTheDocument()
  })
})

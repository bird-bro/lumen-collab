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

function catal(partial: Partial<DictCatal> & { id: number; name: string }): DictCatal {
  return {
    enable: 1,
    sort: 0,
    remark: null,
    cls: null,
    createBy: 1,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

// Backend V9 shape: value is the single text field, the catalog anchors by caId,
// and the spare pair is tag/flag.
function detail(partial: Partial<DictDetail> & { id: number; value: string }): DictDetail {
  return {
    enable: 1,
    caId: 1000,
    code: null,
    color: null,
    css: null,
    tag: null,
    flag: null,
    sort: 1,
    remark: null,
    createBy: 1,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

const CATALS = [catal({ id: 1000, name: '通用状态' })]

const DETAILS = [
  detail({ id: 1, value: '启用', code: 1, sort: 1 }),
  detail({ id: 2, value: '停用', code: 0, sort: 2 }),
]

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <DictsPage />
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({ perms: ['system:dict:list', 'system:dict-catal:create', 'system:dict-catal:update', 'system:dict-catal:delete', 'system:dict-detail:create', 'system:dict-detail:update', 'system:dict-detail:delete'] })
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
    // The ID renders in its own column (user request 2026-11-05; the dict id
    // range starts at 1000 - V7 re-based common_status to head the real dicts).
    const nameCell = screen.getByText('通用状态').closest('td')
    const row = nameCell?.closest('tr')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getByRole('cell', { name: '1000' })).toBeInTheDocument()
  })

  it('marks the selected catalog row with the OPS current-row chrome', async () => {
    // OPS /manage/system/dict highlight-current-row: the selected row (auto the
    // first on load, then the clicked one) carries the chrome class; the detail
    // table never does.
    const user = userEvent.setup()
    vi.mocked(dictsApi.listCatal).mockResolvedValue({
      records: [
        catal({ id: 1000, name: '通用状态' }),
        catal({ id: 1001, name: '检查类型' }),
      ],
      total: 2,
      size: 20,
      current: 1,
      pages: 1,
    } as Page<DictCatal>)
    renderPage()
    const rowOf = (name: string) => screen.getByText(name).closest('tr') as HTMLTableRowElement
    await waitFor(() => expect(rowOf('通用状态')).toHaveClass('lumen-console-row-current'))
    await user.click(screen.getByText('检查类型'))
    await waitFor(() => {
      expect(rowOf('检查类型')).toHaveClass('lumen-console-row-current')
      expect(rowOf('通用状态')).not.toHaveClass('lumen-console-row-current')
    })
    // The detail table stays chrome-free - selection is master-only (OPS).
    expect(rowOf('启用')).not.toHaveClass('lumen-console-row-current')
  })

  it('creates a catalog and re-lists', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建目录/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('目录名称'), '检查类型')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(dictsApi.createCatal).toHaveBeenCalledWith(expect.objectContaining({ name: '检查类型' })))
    await waitFor(() => expect(dictsApi.listCatal).toHaveBeenCalledTimes(2))
  })

  it('lands a duplicate-name A0714 msg on the form and keeps the dialog open', async () => {
    const user = userEvent.setup()
    vi.mocked(dictsApi.createCatal).mockRejectedValue(
      new ApiError('A0714', '数据重复:dict catalog name already exists: 通用状态'),
    )
    renderPage()
    await user.click(await screen.findByRole('button', { name: /新建目录/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('目录名称'), '通用状态')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(screen.getByText(/dict catalog name already exists/)).toBeInTheDocument())
    // drawer did NOT close: `ant-drawer-open` still stands on the drawer root
    expect(document.querySelector('.ant-drawer-open')).not.toBeNull()
  })
})

describe('dicts page - details (spec: Details ordered by sort)', () => {
  it('loads a catalog\'s details ordered by sort when the row is selected', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('通用状态')
    await user.click(screen.getByText('通用状态'))
    await waitFor(() => expect(dictsApi.listDetails).toHaveBeenCalledWith(1000))
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
    await user.type(within(screen.getByRole('dialog')).getByLabelText('数据值'), '维护中')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() =>
      expect(dictsApi.createDetail).toHaveBeenCalledWith(
        expect.objectContaining({ caId: 1000, value: '维护中' }),
      ),
    )
    await waitFor(() => expect(vi.mocked(dictsApi.listDetails)).toHaveBeenCalledTimes(callsAfterAutoSelect + 1))
  })

  it('lands a duplicate-value A0714 msg on the form and keeps the dialog open', async () => {
    const user = userEvent.setup()
    vi.mocked(dictsApi.createDetail).mockRejectedValue(
      new ApiError('A0714', '数据重复:dict detail value already exists under caId 1000: 启用'),
    )
    renderPage()
    await screen.findByText('通用状态')
    await user.click(screen.getByText('通用状态'))
    await screen.findByText('启用')
    await user.click(screen.getByRole('button', { name: /新建明细/ }))
    await user.type(within(screen.getByRole('dialog')).getByLabelText('数据值'), '启用')
    await user.click(screen.getByRole('button', { name: '保 存' }))
    await waitFor(() => expect(screen.getByText(/dict detail value already exists/)).toBeInTheDocument())
    // drawer did NOT close: `ant-drawer-open` still stands on the drawer root
    expect(document.querySelector('.ant-drawer-open')).not.toBeNull()
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

describe('dicts page - button-level gating (button-perm-enforcement-web)', () => {
  it('hides ungranted affordances when only the page perm is held', async () => {
    useAuthStore.getState().setSession({ perms: ['system:dict:list'] })
    renderPage()
    await screen.findByText('通用状态')
    expect(screen.queryByRole('button', { name: /新建目录/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑目录' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除目录' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /新建明细/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑明细' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除明细' })).not.toBeInTheDocument()
  })

  it('reveals the two panels affordances when their button perms are granted', async () => {
    useAuthStore.getState().setSession({ perms: ['system:dict:list', 'system:dict-catal:create', 'system:dict-catal:update', 'system:dict-catal:delete', 'system:dict-detail:create', 'system:dict-detail:update', 'system:dict-detail:delete'] })
    renderPage()
    await screen.findByText('通用状态')
    expect(screen.getByRole('button', { name: /新建目录/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '编辑目录' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '删除目录' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /新建明细/ })).toBeEnabled()
    expect(screen.getAllByRole('button', { name: '编辑明细' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '删除明细' }).length).toBeGreaterThan(0)
  })
})

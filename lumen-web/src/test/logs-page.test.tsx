import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LogsPage from '@/pages/system/logs'
import * as logsApi from '@/api/logs'
import type { LogItem, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 操作日志 page (batch 6b, task 6.7; spec `specs/web-system-console`
 * § Operation Log Query: paged query with the documented filters, newest
 * first, and NO write affordances - append-only data).
 */
vi.mock('@/api/logs')

function log(partial: Partial<LogItem> & { id: number; actionTime: string }): LogItem {
  return {
    dateYm: 202611,
    title: '角色管理',
    descr: '创建角色',
    type: '新增',
    uid: 1,
    account: 'admin',
    spent: 12,
    method: 'POST',
    name: 'create',
    pack: 'org.birdbro.lumen.system',
    url: '/api/v1/system/roles',
    session: null,
    mobile: false,
    ip: '127.0.0.1',
    os: 'macOS',
    browser: 'Chrome',
    engine: 'Blink',
    record: 1,
    param: '{}',
    result: 'true',
    exception: null,
    app: 'system',
    createTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

const LOGS: LogItem[] = [
  log({ id: 3, actionTime: '2026-11-04 10:00:00', type: '删除' }),
  log({ id: 2, actionTime: '2026-11-03 10:00:00', type: '新增' }),
  log({ id: 1, actionTime: '2026-11-02 10:00:00', type: '新增', dateYm: 202610 }),
]

function pageFixture(records: LogItem[], total = 3): Page<LogItem> {
  return { records, total, size: 10, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <LogsPage />
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({ perms: ['system:log:list'] })
  vi.mocked(logsApi.listLogs).mockResolvedValue(pageFixture(LOGS))
})

describe('logs page - read-only paged query (spec: Filtered log query)', () => {
  it('renders rows newest-first with the documented columns', async () => {
    renderPage()
    expect(await screen.findByText('2026-11-04 10:00:00')).toBeInTheDocument()
    const times = screen
      .getAllByText(/2026-11-0\d 10:00:00/)
      .map((el) => el.textContent)
    expect(times).toEqual([...times].sort().reverse())
  })

  it('sends the documented filters (type/uid/dateYm) on 查询', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    await user.type(screen.getByLabelText('类型'), '新增')
    await user.type(screen.getByLabelText('操作人ID'), '1')
    await user.type(screen.getByLabelText('月份'), '202611')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await waitFor(() =>
      expect(logsApi.listLogs).toHaveBeenCalledWith(
        expect.objectContaining({ type: '新增', uid: 1, dateYm: 202611 }),
      ),
    )
  })

  it('resets the filters and re-queries', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    await user.type(screen.getByLabelText('类型'), '新增')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await user.click(screen.getByRole('button', { name: /重\s*置/ }))
    await waitFor(() =>
      expect(logsApi.listLogs).toHaveBeenLastCalledWith(
        expect.not.objectContaining({ type: '新增' }),
      ),
    )
    expect(screen.getByLabelText('类型')).toHaveValue('')
  })

  it('pages server-side (page key per the logs convention)', async () => {
    const user = userEvent.setup()
    vi.mocked(logsApi.listLogs).mockResolvedValue(pageFixture(LOGS, 25))
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    await user.click(screen.getByRole('listitem', { name: '2' }))
    await waitFor(() =>
      expect(logsApi.listLogs).toHaveBeenCalledWith(expect.objectContaining({ page: 2 })),
    )
  })

  it('offers no mutation controls (append-only surface)', async () => {
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    expect(screen.queryByRole('button', { name: '新建' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /删除/ })).not.toBeInTheDocument()
    const table = screen.getByRole('table')
    expect(within(table).queryByText('操作')).not.toBeInTheDocument()
  })

  it('hides nothing on read but gates via perms for future write affordances', async () => {
    useAuthStore.getState().setSession({ perms: [] })
    renderPage()
    expect(await screen.findByText('2026-11-04 10:00:00')).toBeInTheDocument()
  })
})

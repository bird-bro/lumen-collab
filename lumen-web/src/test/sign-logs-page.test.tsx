import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SignLogsPage from '@/pages/system/logs/sign'
import * as logsApi from '@/api/logs'
import type { LogSignItem, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 登录日志 page (sign-log-query batch 4, tasks 4.2-4.3; spec
 * `specs/web-system-console` § Sign-in Log Query: paged query at
 * `/system/logs/sign` with the documented filters (`account`/`uid`/`dateYm`),
 * newest first, and NO write affordances - append-only data; the `token`
 * column never leaves the service (design D7) so nothing here can render it.
 */
vi.mock('@/api/logs')

function sign(partial: Partial<LogSignItem> & { id: number; actionTime: string }): LogSignItem {
  return {
    dateYm: 202611,
    msg: '登录成功',
    descr: '登录成功',
    uid: 1,
    account: 'admin',
    mobile: false,
    ip: '127.0.0.1',
    os: 'macOS',
    browser: 'Chrome',
    engine: 'Blink',
    session: null,
    app: 'system',
    type: 1,
    deleted: 0,
    createTime: '2026-11-04 09:58:00',
    ...partial,
  }
}

const SIGNS: LogSignItem[] = [
  sign({ id: 3, actionTime: '2026-11-04 10:00:00', msg: '登录成功' }),
  sign({ id: 2, actionTime: '2026-11-03 10:00:00', msg: '退出登录' }),
  sign({
    id: 1,
    actionTime: '2026-11-02 10:00:00',
    dateYm: 202610,
    account: 'operator',
    createTime: '2026-11-02 09:58:00',
  }),
]

function pageFixture(records: LogSignItem[], total = 3): Page<LogSignItem> {
  return { records, total, size: 10, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <SignLogsPage />
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({ perms: ['system:signlog:list'] })
  vi.mocked(logsApi.listSignLogs).mockResolvedValue(pageFixture(SIGNS))
})

describe('sign-logs page - read-only paged query (spec: Filtered sign-log query)', () => {
  it('renders the audit columns with rows newest-first', async () => {
    renderPage()
    expect(await screen.findByText('2026-11-04 10:00:00')).toBeInTheDocument()
    const headers = screen.getAllByRole('columnheader').map((el) => el.textContent)
    for (const title of ['操作时间', '事件', '描述', '账号', 'IP', '操作系统', '浏览器', '入库时间']) {
      expect(headers).toContain(title)
    }
    // The actionTime cells only (createTime values stay off this pattern).
    const times = screen
      .getAllByText(/2026-11-0\d 10:00:00/)
      .map((el) => el.textContent)
    expect(times).toEqual([...times].sort().reverse())
  })

  it('sends the documented filters (account/uid/dateYm) on 查询', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    await user.type(screen.getByLabelText('账号'), 'admin')
    await user.type(screen.getByLabelText('操作人ID'), '1')
    await user.type(screen.getByLabelText('月份'), '202611')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await waitFor(() =>
      expect(logsApi.listSignLogs).toHaveBeenCalledWith(
        expect.objectContaining({ account: 'admin', uid: 1, dateYm: 202611 }),
      ),
    )
  })

  it('resets the filters and re-queries', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    await user.type(screen.getByLabelText('账号'), 'admin')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await user.click(screen.getByRole('button', { name: /重\s*置/ }))
    await waitFor(() =>
      expect(logsApi.listSignLogs).toHaveBeenLastCalledWith(
        expect.not.objectContaining({ account: 'admin' }),
      ),
    )
    expect(screen.getByLabelText('账号')).toHaveValue('')
  })

  it('pages server-side (page key per the logs convention)', async () => {
    const user = userEvent.setup()
    vi.mocked(logsApi.listSignLogs).mockResolvedValue(pageFixture(SIGNS, 25))
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    await user.click(screen.getByRole('listitem', { name: '2' }))
    await waitFor(() =>
      expect(logsApi.listSignLogs).toHaveBeenCalledWith(expect.objectContaining({ page: 2 })),
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

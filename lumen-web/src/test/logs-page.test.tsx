import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LogsPage from '@/pages/system/logs'
import * as logsApi from '@/api/logs'
import * as dictUtils from '@/utils/dict-items'
import type { DictItem } from '@/api/types'
import type { LogItem, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 审计日志 page (audit-log-unify-web 2.1; spec `specs/web-system-console`
 * § Operation Log Query as modified by audit-log-unify: the SINGLE unified
 * audit view of `GET /system/logs` — BOTH event families (operations 1–5,
 * sign events 10/11/12), dict-driven 类别 Select + column labels
 * (「审计日志类型」, raw-code fallback), the account filter (sign-page parity),
 * newest first, NO write affordances (append-only data). Sign-family rows
 * render — for the operation-only 方法/URL columns. The `session` column is
 * server-excluded — this page cannot render it (it is not on the type).
 */
vi.mock('@/api/logs')
vi.mock('@/utils/dict-items', () => ({
  ROLE_TYPE_DICT_NAME: '角色类型',
  AUDIT_LOG_TYPE_DICT_NAME: '审计日志类型',
  dictItemsByName: vi.fn(),
  resolveDictLabel: (items: { code: number; value: string }[], code: number) =>
    items.find((item) => item.code === code)?.value ?? String(code),
}))

const AUDIT_TYPE_DICT: DictItem[] = [
  { code: 1, value: '新增' },
  { code: 2, value: '修改' },
  { code: 3, value: '删除' },
  { code: 4, value: '授权' },
  { code: 5, value: '重置密码' },
  { code: 10, value: '登录成功' },
  { code: 11, value: '登录失败' },
  { code: 12, value: '登出' },
]

function log(partial: Partial<LogItem> & { id: number; actionTime: string }): LogItem {
  return {
    dateYm: 202611,
    title: '角色管理',
    descr: '创建角色',
    type: 1,
    uid: 1,
    account: 'admin',
    spent: 12,
    method: 'POST',
    name: 'create',
    pack: 'org.birdbro.lumen.system',
    url: '/api/v1/system/roles',
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
  log({ id: 3, actionTime: '2026-11-04 10:00:00', type: 3 }),
  log({ id: 2, actionTime: '2026-11-03 10:00:00', type: 1 }),
  log({ id: 1, actionTime: '2026-11-02 10:00:00', dateYm: 202610 }),
]

/** A sign-family row: Sign-IN event, no method/url values (em-dash cells). */
const SIGN_ROW: LogItem = log({
  id: 9,
  actionTime: '2026-11-05 10:00:00',
  title: 'Sign-IN',
  descr: '登录成功，用户operator',
  type: 10,
  account: 'operator',
  spent: null as unknown as number,
  method: null as unknown as string,
  name: null as unknown as string,
  pack: null as unknown as string,
  url: null as unknown as string,
  param: null as unknown as string,
  result: null as unknown as string,
})

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
  vi.mocked(dictUtils.dictItemsByName).mockResolvedValue(AUDIT_TYPE_DICT)
  vi.mocked(logsApi.listLogs).mockResolvedValue(pageFixture(LOGS))
})

describe('audit-logs page - dict-driven unified view (audit-log-unify-web 2.1)', () => {
  it('renders both event families newest-first with the merged columns', async () => {
    renderPage()
    expect(await screen.findByText('2026-11-04 10:00:00')).toBeInTheDocument()
    const headers = screen.getAllByRole('columnheader').map((el) => el.textContent)
    for (const title of ['时间', '类别', '标题', '描述', '操作人', 'IP', '操作系统', '浏览器', '方法', 'URL']) {
      expect(headers).toContain(title)
    }
    const times = screen
      .getAllByText(/2026-11-0\d 10:00:00/)
      .map((el) => el.textContent)
    expect(times).toEqual([...times].sort().reverse())
  })

  it('resolves the 类别 column labels through the 「审计日志类型」 dict (one wire call per session)', async () => {
    renderPage()
    // Column-label assertions anchor to table cells (the Select options carry the
    // same texts, so a bare findByText would be ambiguous).
    const table = await screen.findByRole('table')
    expect(await within(table).findByText('删除')).toBeInTheDocument()
    expect(within(table).getAllByText('新增').length).toBeGreaterThanOrEqual(1)
    expect(dictUtils.dictItemsByName).toHaveBeenCalledWith('审计日志类型')
    expect(dictUtils.dictItemsByName).toHaveBeenCalledTimes(1)
  })

  it('sends the dict-driven numeric 类别 + uid/account/dateYm filters on 查询', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    // The 类别 filter is an antd Select fed by the dict — the combobox carries the
    // aria-label (the column header's text also reads 类别, so a bare label query
    // would be ambiguous). Pick 登录失败 (code 11) from the dropdown listbox.
    await user.click(screen.getByRole('combobox', { name: '类别' }))
    await user.click(await screen.findByTitle('登录失败'))
    await user.type(screen.getByLabelText('账号'), 'operator')
    await user.type(screen.getByLabelText('操作人ID'), '1')
    await user.type(screen.getByLabelText('月份'), '202611')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await waitFor(() =>
      expect(logsApi.listLogs).toHaveBeenCalledWith(
        expect.objectContaining({ type: 11, account: 'operator', uid: 1, dateYm: 202611 }),
      ),
    )
  })

  it('degrades to raw codes with one error message when the dict fetch fails', async () => {
    vi.mocked(dictUtils.dictItemsByName).mockRejectedValue(new Error('boom'))
    renderPage()
    // Raw-code fallback: the seeded type 3 renders as "3", not a blank cell.
    expect(await screen.findByText('2026-11-04 10:00:00')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(await screen.findByText(/审计日志类型字典加载失败|加载失败/)).toBeInTheDocument()
    // The page still rendered its data — degrade, never block.
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it('renders em-dash cells for sign rows in the operation-only 方法/URL columns', async () => {
    vi.mocked(logsApi.listLogs).mockResolvedValue(pageFixture([SIGN_ROW], 1))
    renderPage()
    expect(await screen.findByText('Sign-IN')).toBeInTheDocument()
    const table = screen.getByRole('table')
    const row = screen.getByText('Sign-IN').closest('tr') as HTMLElement
    const cells = within(row).getAllByRole('cell').map((el) => el.textContent)
    // 方法 (index after 时间/类别/标题/描述/操作人/IP/操作系统/浏览器) and URL show —
    const dashCount = cells.filter((t) => t === '—').length
    expect(dashCount).toBeGreaterThanOrEqual(2)
    expect(within(table).getByText('登录成功，用户operator')).toBeInTheDocument()
  })

  it('resets the filters and re-queries', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('2026-11-04 10:00:00')
    await user.type(screen.getByLabelText('账号'), 'admin')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await user.click(screen.getByRole('button', { name: /重\s*置/ }))
    await waitFor(() =>
      expect(logsApi.listLogs).toHaveBeenLastCalledWith(
        expect.not.objectContaining({ account: 'admin' }),
      ),
    )
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
})

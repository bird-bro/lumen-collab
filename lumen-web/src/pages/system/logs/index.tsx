import { Button, Input, InputNumber, Select, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { listLogs } from '@/api/logs'
import { ApiError } from '@/api/client'
import type { DictItem, LogItem, Page } from '@/api/types'
import { usePerm } from '@/utils/perm'
import { AUDIT_LOG_TYPE_DICT_NAME, dictItemsByName, resolveDictLabel } from '@/utils/dict-items'
import {
  ConsolePage,
  ConsolePagination,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
} from '../console-shell'

/**
 * 审计日志 (audit-log-unify-web 2.2; spec `specs/web-system-console`
 * § Operation Log Query as modified by audit-log-unify): the SINGLE unified
 * paged READ-ONLY view of `GET /system/logs` — BOTH event families in one
 * table (operations 1–5, sign events 10/11/12), rows ordered actionTime
 * descending. The 类别 filter is a dict-driven Select of the 「审计日志类型」
 * catalog (codes 1–5 + 10/11/12, V25 seed) sending the Integer code; column
 * labels resolve through the same dict with the raw-code fallback (fetch
 * failure → one message + codes, the page never blocks — the roles page's
 * D4 pattern verbatim). The `account` filter joins with sign-page parity.
 * Sign-family rows render — for the operation-only 方法/URL columns. The
 * `session` credential column is select-excluded server-side and absent from
 * the `LogItem` type — nothing here can render it. The data is append-only —
 * there is deliberately no mutation affordance anywhere on the page.
 */

interface Filters {
  type: number | ''
  uid: string
  account: string
  dateYm: string
}

const EMPTY_FILTERS: Filters = { type: '', uid: '', account: '', dateYm: '' }

const PAGE_SIZE = 10

export default function LogsPage() {
  // Read-only surface: there is nothing to gate yet - a future write
  // affordance would gate on the `system:log:list` family via usePerm().
  usePerm();
  const [logs, setLogs] = useState<LogItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(PAGE_SIZE)
  const [loading, setLoading] = useState(true)
  const [draft, setDraft] = useState<Filters>(EMPTY_FILTERS)
  const [applied, setApplied] = useState<Filters>(EMPTY_FILTERS)
  const [auditTypeItems, setAuditTypeItems] = useState<DictItem[]>([])
  const [messageApi, messageHolder] = message.useMessage()

  // One wire call per catalog per session (session-cached resolver); a failed
  // fetch surfaces one message and degrades the labels to raw codes — never
  // blocks the page (role-type-dict D4 pattern).
  useEffect(() => {
    dictItemsByName(AUDIT_LOG_TYPE_DICT_NAME)
      .then(setAuditTypeItems)
      .catch((error: unknown) => {
        messageApi.error(error instanceof ApiError ? error.msg : '审计日志类型字典加载失败')
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const reload = useCallback(
    async (nextPage = page, nextSize = pageSize, filters = applied) => {
      setLoading(true)
      try {
        const data: Page<LogItem> = await listLogs({
          page: nextPage,
          pageSize: nextSize,
          ...(filters.type !== '' ? { type: filters.type } : {}),
          ...(filters.uid ? { uid: Number(filters.uid) } : {}),
          ...(filters.account ? { account: filters.account } : {}),
          ...(filters.dateYm ? { dateYm: Number(filters.dateYm) } : {}),
        })
        setLogs(data.records)
        setTotal(data.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '审计日志加载失败')
      } finally {
        setLoading(false)
      }
    },
    [applied, messageApi, page, pageSize],
  )

  useEffect(() => {
    void reload(page, pageSize)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize])

  const runQuery = () => {
    setApplied(draft)
    setPage(1)
    // Explicit reload: when page is already 1 no state flips, so the
    // page/pageSize effect would not fire - the click must query now.
    void reload(1, pageSize, draft)
  }

  const reset = () => {
    setDraft(EMPTY_FILTERS)
    setApplied(EMPTY_FILTERS)
    setPage(1)
    void reload(1, pageSize, EMPTY_FILTERS)
  }

  const auditTypeOptions = auditTypeItems.map((item) => ({
    value: item.code as number,
    title: item.value ?? String(item.code),
  }))

  const columns: TableColumnsType<LogItem> = [
    { title: '时间', dataIndex: 'actionTime', width: 180, render: (v: string) => (
      <Typography.Text style={{ fontVariantNumeric: 'tabular-nums' }}>{v}</Typography.Text>
    ) },
    { title: '类别', dataIndex: 'type', width: 100, render: (v: number) => resolveDictLabel(auditTypeItems, v) },
    { title: '标题', dataIndex: 'title', width: 140, render: (v: string) => v ?? '—' },
    { title: '描述', dataIndex: 'descr', render: (v: string) => v ?? '—' },
    { title: '操作人', dataIndex: 'account', width: 120 },
    { title: 'IP', dataIndex: 'ip', width: 130, render: (v: string) => v || '—' },
    { title: '操作系统', dataIndex: 'os', width: 110, render: (v: string) => v || '—' },
    { title: '浏览器', dataIndex: 'browser', width: 100, render: (v: string) => v || '—' },
    { title: '方法', dataIndex: 'method', width: 90, render: (v: string) => (
      v ? <Typography.Text code>{v}</Typography.Text> : '—'
    ) },
    { title: 'URL', dataIndex: 'url', render: (v: string) => (
      v ? (
        <Typography.Text type="secondary" style={{ fontSize: 13, wordBreak: 'break-all' }}>{v}</Typography.Text>
      ) : '—'
    ) },
  ]

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePage>
        <ConsoleToolRow>
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="类别"
            aria-label="类别"
            value={draft.type === '' ? undefined : draft.type}
            options={auditTypeOptions}
            onChange={(value) => setDraft((prev) => ({ ...prev, type: (value ?? '') as number | '' }))}
          />
          <Input
            allowClear
            style={{ width: 140 }}
            placeholder="账号"
            aria-label="账号"
            value={draft.account}
            onChange={(event) => setDraft((prev) => ({ ...prev, account: event.target.value }))}
            onPressEnter={runQuery}
          />
          <InputNumber
            style={{ width: 140 }}
            placeholder="操作人ID"
            aria-label="操作人ID"
            value={draft.uid ? Number(draft.uid) : undefined}
            onChange={(value) => setDraft((prev) => ({ ...prev, uid: value == null ? '' : String(value) }))}
          />
          <InputNumber
            style={{ width: 140 }}
            placeholder="月份(如 202611)"
            aria-label="月份"
            value={draft.dateYm ? Number(draft.dateYm) : undefined}
            onChange={(value) => setDraft((prev) => ({ ...prev, dateYm: value == null ? '' : String(value) }))}
          />
          <ConsoleQueryButton onClick={runQuery} />
          <Button onClick={reset}>重置</Button>
        </ConsoleToolRow>

        <ConsoleTable<LogItem>
          rowKey={(row) => row.id}
          columns={columns}
          dataSource={logs}
          loading={loading}
        />

        <ConsolePagination
          total={total}
          current={page}
          pageSize={pageSize}
          onChange={(next, nextSize) => {
            setPage(next)
            setPageSize(nextSize)
          }}
          onShowSizeChange={(_current, size) => {
            setPage(1)
            setPageSize(size)
          }}
        />
      </ConsolePage>
    </div>
  )
}

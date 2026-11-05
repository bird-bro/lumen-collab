import { Button, Input, InputNumber, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { listLogs } from '@/api/logs'
import { ApiError } from '@/api/client'
import type { LogItem, Page } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsolePanel,
  ConsolePagination,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
} from '../console-shell'

/**
 * 操作日志 (batch 6b, task 6.7; spec `specs/web-system-console`
 * § Operation Log Query): a paged READ-ONLY view of `GET /system/logs` with
 * the documented filters (`type`, `uid`, `dateYm`), rows ordered actionTime
 * descending. The data is append-only - there is deliberately no mutation
 * affordance anywhere on the page.
 */

interface Filters {
  type: string
  uid: string
  dateYm: string
}

const EMPTY_FILTERS: Filters = { type: '', uid: '', dateYm: '' }

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
  const [messageApi, messageHolder] = message.useMessage()

  const reload = useCallback(
    async (nextPage = page, nextSize = pageSize, filters = applied) => {
      setLoading(true)
      try {
        const data: Page<LogItem> = await listLogs({
          page: nextPage,
          pageSize: nextSize,
          ...(filters.type ? { type: filters.type } : {}),
          ...(filters.uid ? { uid: Number(filters.uid) } : {}),
          ...(filters.dateYm ? { dateYm: Number(filters.dateYm) } : {}),
        })
        setLogs(data.records)
        setTotal(data.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '操作日志加载失败')
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

  const columns: TableColumnsType<LogItem> = [
    { title: '时间', dataIndex: 'actionTime', width: 180, render: (v: string) => (
      <Typography.Text style={{ fontVariantNumeric: 'tabular-nums' }}>{v}</Typography.Text>
    ) },
    { title: '类型', dataIndex: 'type', width: 100 },
    { title: '标题', dataIndex: 'title', width: 140 },
    { title: '描述', dataIndex: 'descr', render: (v: string) => v ?? '—' },
    { title: '操作人', dataIndex: 'account', width: 120 },
    { title: '方法', dataIndex: 'method', width: 90, render: (v: string) => (
      <Typography.Text code>{v}</Typography.Text>
    ) },
    { title: 'URL', dataIndex: 'url', render: (v: string) => (
      <Typography.Text type="secondary" style={{ fontSize: 13, wordBreak: 'break-all' }}>{v}</Typography.Text>
    ) },
  ]

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePanel>
        <ConsoleToolRow>
          <Input
            allowClear
            style={{ width: 140 }}
            placeholder="类型"
            aria-label="类型"
            value={draft.type}
            onChange={(event) => setDraft((prev) => ({ ...prev, type: event.target.value }))}
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
      </ConsolePanel>
    </div>
  )
}

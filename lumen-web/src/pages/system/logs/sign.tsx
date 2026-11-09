import { Button, Input, InputNumber, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { listSignLogs } from '@/api/logs'
import { ApiError } from '@/api/client'
import type { LogSignItem, Page } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsolePage,
  ConsolePagination,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
} from '../console-shell'

/**
 * 登录日志 (sign-log-query batch 4, tasks 4.1-4.4; spec `specs/web-system-console`
 * § Sign-in Log Query): a paged READ-ONLY view of `GET /system/logs/sign` with
 * the documented filters (`account`, `uid`, `dateYm`), rows ordered actionTime
 * descending. Gated server-side by the `system:signlog:list` page permission -
 * independent of the operation-log permission (design D4). The data is
 * append-only - there is deliberately no mutation affordance anywhere on the
 * page, and the session `token` never leaves the service (design D7 - it is
 * absent from the typed payload, so nothing here can render it).
 */

interface Filters {
  account: string
  uid: string
  dateYm: string
}

const EMPTY_FILTERS: Filters = { account: '', uid: '', dateYm: '' }

const PAGE_SIZE = 10

export default function SignLogsPage() {
  // Read-only surface: there is nothing to gate yet - a future write
  // affordance would gate on the `system:signlog:list` family via usePerm().
  usePerm();
  const [signs, setSigns] = useState<LogSignItem[]>([])
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
        const data: Page<LogSignItem> = await listSignLogs({
          page: nextPage,
          pageSize: nextSize,
          ...(filters.account ? { account: filters.account } : {}),
          ...(filters.uid ? { uid: Number(filters.uid) } : {}),
          ...(filters.dateYm ? { dateYm: Number(filters.dateYm) } : {}),
        })
        setSigns(data.records)
        setTotal(data.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '登录日志加载失败')
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

  const columns: TableColumnsType<LogSignItem> = [
    { title: '操作时间', dataIndex: 'actionTime', width: 180, render: (v: string) => (
      <Typography.Text style={{ fontVariantNumeric: 'tabular-nums' }}>{v}</Typography.Text>
    ) },
    { title: '事件', dataIndex: 'msg', width: 180 },
    { title: '描述', dataIndex: 'descr', render: (v: string) => v ?? '—' },
    { title: '账号', dataIndex: 'account', width: 120 },
    { title: 'IP', dataIndex: 'ip', width: 130 },
    { title: '操作系统', dataIndex: 'os', width: 120 },
    { title: '浏览器', dataIndex: 'browser', width: 100 },
    { title: '入库时间', dataIndex: 'createTime', width: 180, render: (v?: string) => (
      <Typography.Text style={{ fontVariantNumeric: 'tabular-nums' }}>{v ?? '—'}</Typography.Text>
    ) },
  ]

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePage>
        <ConsoleToolRow>
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

        <ConsoleTable<LogSignItem>
          rowKey={(row) => row.id}
          columns={columns}
          dataSource={signs}
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

import { Button, DatePicker, Input, Select, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
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
 * § Operation Log Query as modified by audit-log-unify and log-query-date-range):
 * the SINGLE unified paged READ-ONLY view of `GET /system/logs` — BOTH event
 * families in one table (operations 1–5, sign events 10/11/12), rows ordered
 * actionTime descending. The filter bar is 类别 (dict-driven Select of the
 * 「审计日志类型」 catalog sending the Integer code, options rendering the dict
 * VALUES — rc-select needs label, not title), 账号, and 日期段 (RangePicker with
 * 今日/近7天/近30天/本月 presets sending beginDate/endDate yyyy-MM-dd, both-or-none);
 * the former 操作人ID (uid) / 月份 (dateYm) inputs are gone (the API keeps those
 * filters — additive contract). Column labels resolve through the same dict with
 * the raw-code fallback (fetch failure → one message + codes, the page never
 * blocks — the roles page's D4 pattern verbatim). Sign-family rows render —
 * for the operation-only 方法/URL columns. The `session` credential column is
 * select-excluded server-side and absent from the `LogItem` type — nothing here
 * can render it. The data is append-only — there is deliberately no mutation
 * affordance anywhere on the page.
 */

interface Filters {
  type: number | ''
  account: string
  dateRange: [Dayjs, Dayjs] | null
}

const EMPTY_FILTERS: Filters = { type: '', account: '', dateRange: null }

const PAGE_SIZE = 20

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
          ...(filters.account ? { account: filters.account } : {}),
          // Both bounds travel as yyyy-MM-dd only when the pair is complete
          // (log-query-date-range: the API treats each bound as optional, the
          // picker produces both-or-none by construction).
          ...(filters.dateRange
            ? {
                beginDate: filters.dateRange[0].format('YYYY-MM-DD'),
                endDate: filters.dateRange[1].format('YYYY-MM-DD'),
              }
            : {}),
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

  // rc-select renders option.label — the dropdown must show the dict VALUE
  // (登录失败…), never the raw code; title alone would fall back to the code.
  const auditTypeOptions = auditTypeItems.map((item) => ({
    value: item.code as number,
    label: item.value ?? String(item.code),
  }))

  // 日期段 quick presets (log-query-date-range D4: 今日 / 近7天 / 近30天 / 本月).
  const rangePresets: { label: string; value: [Dayjs, Dayjs] }[] = [
    { label: '今日', value: [dayjs(), dayjs()] },
    { label: '近7天', value: [dayjs().subtract(6, 'day'), dayjs()] },
    { label: '近30天', value: [dayjs().subtract(29, 'day'), dayjs()] },
    { label: '本月', value: [dayjs().startOf('month'), dayjs()] },
  ]

  const columns: TableColumnsType<LogItem> = [
    { title: '日期', dataIndex: 'actionTime', width: 220, render: (v: string) => (
      <Typography.Text style={{ fontVariantNumeric: 'tabular-nums' }}>{v}</Typography.Text>
    ) },
    { title: '操作人', dataIndex: 'account', width: 180 },
    { title: '类别', dataIndex: 'type', width: 180, render: (v: number) => resolveDictLabel(auditTypeItems, v) },
    { title: '方法', dataIndex: 'method', width: 120, onCell: () => ({ style: { textAlign: 'center' } }), render: (v: string) => (
      v ? <Typography.Text code>{v}</Typography.Text> : '—'
    ) },
    { title: '标题', dataIndex: 'title', width: 200, render: (v: string) => v ?? '—' },
    { title: '描述', dataIndex: 'descr', render: (v: string) => v ?? '—' },
    { title: 'IP', dataIndex: 'ip', width: 130, onCell: () => ({ style: { textAlign: 'center' } }), render: (v: string) => v || '—' },
    { title: '操作系统', dataIndex: 'os', width: 110, render: (v: string) => v || '—' },
    { title: '浏览器', dataIndex: 'browser', width: 180, render: (v: string) => v || '—' },
    { title: 'URL', dataIndex: 'url', render: (v: string) => (
      v ? (
        <Typography.Text type="secondary" style={{ wordBreak: 'break-all' }}>{v}</Typography.Text>
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
          <DatePicker.RangePicker
            allowClear
            style={{ width: 280 }}
            aria-label="日期段"
            placeholder={['开始日期', '结束日期']}
            value={draft.dateRange}
            presets={rangePresets}
            onChange={(dates) =>
              setDraft((prev) => ({
                ...prev,
                dateRange: dates && dates[0] && dates[1] ? [dates[0], dates[1]] : null,
              }))
            }
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

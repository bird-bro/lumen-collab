import {
  Button,
  Col,
  DatePicker,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
  Switch,
  Tag,
  message,
  theme,
} from 'antd'
import type { TableColumnsType } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import {
  cancelOrder,
  createOrder,
  pageOrders,
  registerOrder,
  resolvePatients,
  statusCounts,
} from '@/api/us-orders'
import type {
  UsOrderCreateBody,
  UsOrderPageQuery,
  UsOrderStatus,
  UsOrderView,
  UsPatientIdentity,
} from '@/api/us-orders'
import { pageExamItems } from '@/api/exam-item'
import type { ExamItem } from '@/api/types'
import {
  createFilter,
  deleteFilter,
  listFilters,
  setDefaultFilter,
} from '@/api/search-filters'
import type { SearchFilterView } from '@/api/search-filters'
import { ApiError } from '@/api/client'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleFormHint,
  ConsolePage,
  ConsolePagination,
  ConsoleTable,
} from '@/pages/system/console-shell'

/**
 * US 工作站两态工作台 (us-registration-redesign tasks 1.2-1.3; spec delta
 * `web-us-station` § Registration Page Shell — 拍板 01-B/02-B, the prototype's 壹
 * section): ONE route renders the COMPACT state by default (the 392px 今日登记单
 * rail beside the registration workbench form) and the edge toggle swaps to the
 * WIDE-LIST query state (the full-width table + the RCP∪EIS query row). Submit
 * registers (提交即登记 — the derived entry, 拍板 02-B); 登记 remains the arrival
 * confirmation for pre-registered orders. Against the REAL `/api/v1/us/**` backend
 * (no mock layer - ruling 2026-11-05); every affordance rides `us:order:*`.
 */
const STATUS_LABELS: Record<UsOrderStatus, { label: string; color: string }> = {
  BOOKED: { label: '预登记', color: 'blue' },
  REGISTERED: { label: '已登记', color: 'green' },
  IN_PROGRESS: { label: '处理中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'default' },
  BOOKING_CANCELLED: { label: '预约已取消', color: 'default' },
  CANCELLED: { label: '已取消', color: 'default' },
}

const ALL_STATUSES = Object.keys(STATUS_LABELS) as UsOrderStatus[]
/** The chip meter's default selection (拍板 03-B: 预登记 + 已登记). */
const DEFAULT_STATUS_FILTER: UsOrderStatus[] = ['BOOKED', 'REGISTERED']
const LIVE_STATES: UsOrderStatus[] = ['BOOKED', 'REGISTERED']
const PREGNANCY_OPTIONS = ['未孕', '早孕', '中晚孕', '未知'].map((label) => ({ value: label, label }))
const GENDER_OPTIONS = [
  { value: 0, label: '未知' },
  { value: 1, label: '男' },
  { value: 2, label: '女' },
]
const ID_TYPE_OPTIONS = ['身份证', '护照', '军官证', '其它'].map((label) => ({ value: label, label }))

interface RegistrationFormValues {
  name?: string | null
  gender?: number | null
  age?: string | null
  idType?: string | null
  idNo?: string | null
  cardNo?: string | null
  phone?: string | null
  patientSource?: string | null
  height?: number | null
  weight?: number | null
  admissionNumber?: string | null
  bedNumber?: string | null
  emgFlag?: boolean
  pregnancyStatus?: string | null
  gestationalWeeks?: number | null
  note?: string | null
  bookingTime?: dayjs.Dayjs | null
}

interface WorkbenchFilters {
  status: UsOrderStatus[]
  accessionNo: string
  patientName: string
  patientId: string
  patientSource: string
  cardNo: string
  examItemName: string
  /** the typed apply-time range; empty = the shared date state owns the range */
  applyTimeStart: string
  applyTimeEnd: string
}

/** The saved-search namespace (拍板 07-A) + the localStorage column-settings key. */
const PAGE_KEY = 'us-registration'
const COLUMNS_KEY = 'us-registration.columns'
/** The text-filter keys a saved search may restore (the chips/date state stay live). */
const SAVED_FILTER_KEYS = [
  'accessionNo',
  'patientName',
  'patientId',
  'patientSource',
  'cardNo',
  'examItemName',
] as const

export default function UsRegistrationPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  // the shared state both states render from (design D1 - lifted to the page root)
  const [mode, setMode] = useState<'compact' | 'wide'>('compact')
  const [rows, setRows] = useState<UsOrderView[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [counts, setCounts] = useState<Record<UsOrderStatus, number> | null>(null)
  const [activeDate, setActiveDate] = useState(dayjs())
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [cancelTarget, setCancelTarget] = useState<UsOrderView | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [lookupValue, setLookupValue] = useState('')
  const [saving, setSaving] = useState(false)
  const [candidates, setCandidates] = useState<UsPatientIdentity[] | null>(null)
  const [matchedPatId, setMatchedPatId] = useState<string | null>(null)
  const [items, setItems] = useState<ExamItem[]>([])
  const [picked, setPicked] = useState<ExamItem[]>([])
  const [itemDialogOpen, setItemDialogOpen] = useState(false)
  const [form] = Form.useForm<RegistrationFormValues>()
  const gender = Form.useWatch('gender', form)
  const pregnancyStatus = Form.useWatch('pregnancyStatus', form)
  const [filters, setFilters] = useState<WorkbenchFilters>({
    status: DEFAULT_STATUS_FILTER,
    accessionNo: '',
    patientName: '',
    patientId: '',
    patientSource: '',
    cardNo: '',
    examItemName: '',
    applyTimeStart: '',
    applyTimeEnd: '',
  })
  const [extrasOpen, setExtrasOpen] = useState(false)
  const [savedFilters, setSavedFilters] = useState<SearchFilterView[]>([])
  const [saveDialogOpen, setSaveDialogOpen] = useState(false)
  const [saveName, setSaveName] = useState('')
  const [searchListOpen, setSearchListOpen] = useState(false)
  const [hiddenColumns, setHiddenColumns] = useState<Record<string, boolean>>(() => {
    try {
      return JSON.parse(localStorage.getItem(COLUMNS_KEY) ?? '{}')
    } catch {
      return {}
    }
  })

  /** The shared date state feeds the apply-time range (the datenav owns it in the
   *  compact head; the wide state's range controls edit the same fields). */
  const dateRange = useCallback(
    (date: dayjs.Dayjs): Pick<UsOrderPageQuery, 'applyTimeStart' | 'applyTimeEnd'> => ({
      applyTimeStart: date.format('YYYY-MM-DD 00:00:00'),
      applyTimeEnd: date.format('YYYY-MM-DD 23:59:59'),
    }),
    [],
  )

  const load = useCallback(
    async (page: { pageNum: number; pageSize: number }) => {
      setLoading(true)
      try {
        const dateDefaults = dateRange(activeDate)
        const result = await pageOrders({
          pageNum: page.pageNum,
          pageSize: page.pageSize,
          status: filters.status,
          accessionNo: filters.accessionNo,
          patientName: filters.patientName,
          patientId: filters.patientId,
          patientSource: filters.patientSource,
          cardNo: filters.cardNo,
          examItemName: filters.examItemName,
          // the shared date state owns the range until the user types an explicit one
          applyTimeStart: filters.applyTimeStart || dateDefaults.applyTimeStart,
          applyTimeEnd: filters.applyTimeEnd || dateDefaults.applyTimeEnd,
        })
        setRows(result.records)
        setTotal(result.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '登记单列表加载失败')
      } finally {
        setLoading(false)
      }
    },
    [filters, activeDate, dateRange, messageApi],
  )

  const loadCounts = useCallback(async () => {
    try {
      setCounts(await statusCounts({ ...dateRange(activeDate) }))
    } catch {
      setCounts(null) // the meter degrades to chip-only rendering (the dict-degrade precedent)
    }
  }, [activeDate, dateRange])

  useEffect(() => {
    void load({ pageNum, pageSize })
    void loadCounts()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageNum, pageSize, filters, activeDate])

  useEffect(() => {
    // the US item vocabulary: the setting catalog filtered to the US modality
    // (server-side modality filter; client-side slice keeps it small)
    let alive = true
    void (async () => {
      try {
        const page = await pageExamItems({ pageNum: 1, pageSize: 200, modality: 'US' } as never)
        if (alive) setItems(page.records)
      } catch {
        if (alive) setItems([]) // catalog failure degrades to an empty select (the dict-degrade precedent)
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  // the saved searches (拍板 07-A): load the caller's list; the DEFAULT search
  // auto-applies its conditions before the first list query lands (the load effect
  // re-fires on the filters change - the last call carries the merged conditions)
  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const list = await listFilters(PAGE_KEY)
        if (!alive) return
        setSavedFilters(list)
        const myDefault = list.find((item) => item.isDefault)
        if (myDefault?.query) {
          setFilters((prev) => {
            const restored = { ...prev }
            for (const key of SAVED_FILTER_KEYS) {
              const value = myDefault.query[key]
              if (typeof value === 'string' && value) {
                restored[key] = value
              }
            }
            return restored
          })
        }
      } catch {
        if (alive) setSavedFilters([]) // the saved-search failure degrades to an empty bar
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  const persistColumns = (next: Record<string, boolean>) => {
    setHiddenColumns(next)
    localStorage.setItem(COLUMNS_KEY, JSON.stringify(next))
  }

  const saveCurrentSearch = async () => {
    const name = saveName.trim()
    if (!name) {
      messageApi.warning('请填写筛选项名称')
      return
    }
    try {
      const query: Record<string, string> = {}
      for (const key of SAVED_FILTER_KEYS) {
        if (filters[key]) {
          query[key] = filters[key]
        }
      }
      await createFilter({ pageKey: PAGE_KEY, name, query })
      messageApi.success('已保存')
      setSaveDialogOpen(false)
      setSaveName('')
      setSavedFilters(await listFilters(PAGE_KEY))
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '保存失败')
    }
  }

  const applySavedSearch = (filter: SearchFilterView) => {
    const restored: Partial<WorkbenchFilters> = {}
    for (const key of SAVED_FILTER_KEYS) {
      const value = filter.query[key]
      restored[key] = typeof value === 'string' ? value : ''
    }
    setFilters((prev) => ({ ...prev, ...restored }))
    setSearchListOpen(false)
  }

  const markDefaultSearch = async (filter: SearchFilterView) => {
    try {
      await setDefaultFilter(filter.id)
      setSavedFilters(await listFilters(PAGE_KEY))
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '默认设置失败')
    }
  }

  const removeSavedSearch = async (filter: SearchFilterView) => {
    try {
      await deleteFilter(filter.id)
      setSavedFilters(await listFilters(PAGE_KEY))
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  useEffect(() => {
    if (!autoRefresh) return
    const timer = setInterval(() => {
      void load({ pageNum, pageSize })
      void loadCounts()
    }, 30_000)
    return () => clearInterval(timer)
  }, [autoRefresh, load, loadCounts, pageNum, pageSize])

  const refreshAll = useCallback(() => {
    void load({ pageNum, pageSize })
    void loadCounts()
  }, [load, loadCounts])

  // ---------------- the resolution flow (the three outcomes, no side effect) -----

  const fillFromIdentity = (identity: UsPatientIdentity) => {
    form.setFieldsValue({
      idType: identity.idType ?? null,
      idNo: identity.idNo ?? null,
      cardNo: identity.cardNo ?? null,
      name: identity.name ?? null,
      gender: identity.gender ?? null,
      phone: identity.phone ?? null,
    })
    setMatchedPatId(identity.patId ?? null)
  }

  const runLookup = async () => {
    const key = lookupValue.trim()
    if (!key) {
      messageApi.warning('请输入解析键值')
      return
    }
    // the chain's key routing: an 18-ish digit-looking value walks id_no, else card_no
    const query = /^\d{15,21}$/.test(key) ? { idNo: key } : { cardNo: key }
    try {
      const result = await resolvePatients(query)
      if (result.result === 'MATCHED' && result.identity) {
        fillFromIdentity(result.identity)
        messageApi.success(`已匹配档案 ${result.identity.patId ?? ''}`)
      } else if (result.result === 'CANDIDATES' && result.candidates?.length) {
        setCandidates(result.candidates)
      } else {
        form.resetFields()
        setMatchedPatId(null)
        messageApi.info('未匹配到档案，请建档登记')
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '患者解析失败')
    }
  }

  // ---------------- the item picking (common chips + the add dialog) -------------

  const commonItems = items.filter((item) => item.isCommon)
  const isPicked = (id: number) => picked.some((item) => item.id === id)
  const togglePick = (item: ExamItem) => {
    setPicked((prev) =>
      isPicked(item.id) ? prev.filter((picked_) => picked_.id !== item.id) : [...prev, item],
    )
  }
  const removePick = (id: number) => setPicked((prev) => prev.filter((item) => item.id !== id))
  /** 未孕/未知（或未选） disables the weeks input (拍板 05-A). */
  const pregnancyDisablesWeeks =
    pregnancyStatus == null || pregnancyStatus === '未孕' || pregnancyStatus === '未知'

  // ---------------- the submit (提交即登记, 拍板 02-B) ----------------------------

  const save = async () => {
    try {
      const values = await form.validateFields()
      if (picked.length === 0) {
        messageApi.warning('请选择检查项目')
        return
      }
      setSaving(true)
      const body: UsOrderCreateBody = {
        idType: values.idType ?? null,
        idNo: values.idNo ?? null,
        cardNo: values.cardNo ?? null,
        // the empty-name rule (拍板: the server judges by name=null - an emergency
        // order carries an empty name as the placeholder archive; no client switch)
        name: values.name?.trim() ? values.name.trim() : null,
        gender: values.gender ?? null,
        phone: values.phone ?? null,
        age: values.age ?? null,
        patientSource: values.patientSource ?? null,
        admissionNumber: values.admissionNumber ?? null,
        bedNumber: values.bedNumber ?? null,
        height: values.height ?? null,
        weight: values.weight ?? null,
        bookingTime: values.bookingTime
          ? (values.bookingTime as dayjs.Dayjs).format('YYYY-MM-DD HH:mm:ss')
          : null,
        examItems: picked.map((item) => ({
          itemId: item.id,
          itemName: item.exam,
          // the catalog row's dict-1024 token rides the ref so the issuer resolves
          // the modality-scoped rule - row-derived, never a hardcoded 'US' string
          modality: item.modality ?? null,
        })),
        emgFlag: values.emgFlag ? 1 : 0,
        ext: {
          pregnancyStatus: values.pregnancyStatus ?? null,
          gestationalWeeks: values.gestationalWeeks ?? null,
          note: values.note ?? null,
        },
      }
      const created = await createOrder(body)
      messageApi.success(
        created.status === 'BOOKED'
          ? `预登记成功，检查号 ${created.accessionNo}`
          : `登记成功，检查号 ${created.accessionNo}`,
      )
      form.resetFields()
      setPicked([])
      setMatchedPatId(null)
      refreshAll()
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg) // backend refusals (the four A0400 slot tokens, A0715) surface verbatim
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return // AntD validation rejection - keep the form on the fields
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setSaving(false)
    }
  }

  const transitionRegister = async (row: UsOrderView) => {
    try {
      await registerOrder(row.orderId)
      messageApi.success(`已登记 ${row.accessionNo}`)
      refreshAll()
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '登记失败') // state-machine refusal verbatim
    }
  }

  const submitCancel = async () => {
    if (!cancelTarget) return
    const reason = cancelReason.trim()
    if (!reason) {
      messageApi.warning('请填写取消原因')
      return
    }
    try {
      await cancelOrder(cancelTarget.orderId, reason)
      messageApi.success('已取消')
      setCancelTarget(null)
      setCancelReason('')
      refreshAll()
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '取消失败')
    }
  }

  // the status chips: the desk workload meter (zero-filled server-side), the
  // multi-select toggle semantics wired in batch 2
  const toggleStatus = (status: UsOrderStatus) => {
    setFilters((prev) => ({
      ...prev,
      status: prev.status.includes(status)
        ? prev.status.filter((item) => item !== status)
        : [...prev.status, status],
    }))
  }

  const railRow = (row: UsOrderView) => {
    const meta = STATUS_LABELS[row.status]
    return (
      <div
        key={row.orderId}
        data-rail-row
        style={{
          padding: `8px 0`,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>
            {row.patientSnapshot?.name ?? '—'}
            <span style={{ color: token.colorTextTertiary, marginLeft: 6 }}>
              {row.patientSnapshot?.patId ?? ''}
            </span>
          </span>
          <Tag color={meta.color}>{meta.label}</Tag>
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: 2,
          }}
        >
          <span className="mono" style={{ fontSize: 12 }}>
            {row.accessionNo}
          </span>
          <span>
            {LIVE_STATES.includes(row.status) && hasPerm('us:order:update') && (
              <Button size="small" type="link" onClick={() => void transitionRegister(row)}>
                登记
              </Button>
            )}
            {LIVE_STATES.includes(row.status) && hasPerm('us:order:cancel') && (
              <Button
                size="small"
                type="link"
                danger
                onClick={() => {
                  setCancelTarget(row)
                  setCancelReason('')
                }}
              >
                取消
              </Button>
            )}
          </span>
        </div>
      </div>
    )
  }

  const columns: TableColumnsType<UsOrderView> = [
    {
      title: '检查号',
      dataIndex: 'accessionNo',
      key: 'accessionNo',
      width: 140,
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '患者',
      key: 'patient',
      width: 180,
      render: (_: unknown, row) => (
        <span>
          {row.patientSnapshot?.name ?? '—'}
          <span style={{ color: token.colorTextTertiary, marginLeft: 6 }}>
            {row.patientSnapshot?.patId ?? ''}
          </span>
        </span>
      ),
    },
    {
      title: '检查项目',
      key: 'items',
      render: (_: unknown, row) =>
        (row.examItems ?? []).map((item) => item.itemName).join('、') || '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      align: 'center',
      render: (value: UsOrderStatus) => {
        const meta = STATUS_LABELS[value]
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '预登记时间',
      dataIndex: 'bookingTime',
      key: 'bookingTime',
      width: 150,
      render: (value: string | null) => value ?? <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    {
      title: '急诊',
      dataIndex: 'emgFlag',
      key: 'emgFlag',
      width: 70,
      align: 'center',
      render: (value: number) => (value === 1 ? <Tag color="orange">急诊</Tag> : null),
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 150 },
  ]

  const statusChips = (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }} role="group" aria-label="状态筛选">
      {ALL_STATUSES.map((status) => {
        const meta = STATUS_LABELS[status]
        const active = filters.status.includes(status)
        return (
          <Button
            key={status}
            size="small"
            type={active ? 'primary' : 'default'}
            onClick={() => toggleStatus(status)}
          >
            {meta.label}
            {counts ? ` ${counts[status] ?? 0}` : ''}
          </Button>
        )
      })}
    </div>
  )

  return (
    <ConsolePage>
      {contextHolder}
      {/* the shared head: the edge toggle + (compact: datenav) + the autorefresh switch */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: token.marginSM,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: token.marginSM }}>
          <Radio.Group
            aria-label="工作台切换"
            value={mode}
            onChange={(event) => setMode(event.target.value as 'compact' | 'wide')}
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: 'compact', label: '工作台' },
              { value: 'wide', label: '宽表查询' },
            ]}
          />
          {mode === 'compact' && (
            <div role="group" aria-label="日期导航" style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <Button
                size="small"
                aria-label="上一日"
                onClick={() => {
                  setActiveDate((prev) => prev.add(-1, 'day'))
                  setFilters((prev) => ({ ...prev, applyTimeStart: '', applyTimeEnd: '' }))
                }}
              >
                ‹
              </Button>
              <Button size="small" onClick={() => setActiveDate(dayjs())}>
                {activeDate.format('MM-DD dddd')}
              </Button>
              <Button
                size="small"
                aria-label="下一日"
                onClick={() => {
                  setActiveDate((prev) => prev.add(1, 'day'))
                  setFilters((prev) => ({ ...prev, applyTimeStart: '', applyTimeEnd: '' }))
                }}
              >
                ›
              </Button>
            </div>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: token.marginSM }}>
          {statusChips}
          <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <Switch
              id="us-autorefresh"
              aria-label="自动刷新"
              size="small"
              checked={autoRefresh}
              onChange={setAutoRefresh}
            />
            <label htmlFor="us-autorefresh" style={{ fontSize: 12 }}>
              自动刷新
            </label>
          </span>
        </div>
      </div>

      {mode === 'compact' ? (
        <div style={{ display: 'flex', gap: token.marginLG, alignItems: 'stretch' }}>
          {/* the 392px 今日登记单 rail (the prototype's 密度) */}
          <div
            style={{
              width: 392,
              flexShrink: 0,
              border: `1px solid ${token.colorBorderSecondary}`,
              borderRadius: token.borderRadiusLG,
              padding: token.paddingSM,
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: token.marginSM }}>今日登记单</div>
            <div style={{ maxHeight: 640, overflowY: 'auto' }}>
              {rows.length === 0 && !loading && (
                <div style={{ color: token.colorTextTertiary, padding: '16px 0' }}>
                  当日暂无登记单
                </div>
              )}
              {rows.map(railRow)}
            </div>
          </div>

          {/* the registration workbench form (the three sections, 拍板 05-A) */}
          <div
            style={{
              flex: 1,
              border: `1px solid ${token.colorBorderSecondary}`,
              borderRadius: token.borderRadiusLG,
              padding: token.paddingMD,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: token.marginSM }}>
              <Input
                aria-label="解析键值"
                style={{ width: 240 }}
                placeholder="证件号 / 就诊卡号解析"
                value={lookupValue}
                onChange={(event) => setLookupValue(event.target.value)}
                onPressEnter={() => void runLookup()}
                disabled={!hasPerm('us:order:create')}
              />
              {hasPerm('us:order:create') && (
                <Button type="primary" aria-label="解析查询" onClick={() => void runLookup()}>
                  解析查询
                </Button>
              )}
              {matchedPatId && (
                <span style={{ color: token.colorTextSecondary }}>
                  已匹配档案 <span className="mono">{matchedPatId}</span>
                </span>
              )}
            </div>
            <Form form={form} layout="horizontal" labelCol={{ style: { width: 72 } }} disabled={saving}>
              <div aria-label="登记工作台">
                <div style={{ fontWeight: 600, margin: `${token.marginSM}px 0` }}>1患者信息</div>
                <Row gutter={12}>
                  <Col span={8}>
                    <Form.Item name="name" label="姓名">
                      <Input aria-label="姓名" placeholder="急诊单可留空（占位档案）" />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="gender" label="性别">
                      <Select aria-label="性别" allowClear options={GENDER_OPTIONS} />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="age" label="年龄">
                      <Input aria-label="年龄" />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item label="证件" style={{ marginBottom: 0 }}>
                      <Input.Group compact>
                        <Form.Item name="idType" noStyle>
                          <Select
                            aria-label="证件类型"
                            style={{ width: '38%' }}
                            allowClear
                            options={ID_TYPE_OPTIONS}
                          />
                        </Form.Item>
                        <Form.Item name="idNo" noStyle>
                          <Input aria-label="证件号" style={{ width: '62%' }} />
                        </Form.Item>
                      </Input.Group>
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="phone" label="电话">
                      <Input aria-label="电话" />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="cardNo" label="卡号">
                      <Input aria-label="卡号" />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item name="patientSource" label="患者来源">
                      <Select
                        aria-label="患者来源"
                        allowClear
                        options={['门诊', '住院', '急诊', '体检'].map((label) => ({ value: label, label }))}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item label="身高体重" style={{ marginBottom: 0 }}>
                      <Input.Group compact>
                        <Form.Item name="height" noStyle>
                          <InputNumber
                            aria-label="身高"
                            style={{ width: '50%' }}
                            min={0}
                            placeholder="身高cm"
                          />
                        </Form.Item>
                        <Form.Item name="weight" noStyle>
                          <InputNumber
                            aria-label="体重"
                            style={{ width: '50%' }}
                            min={0}
                            placeholder="体重kg"
                          />
                        </Form.Item>
                      </Input.Group>
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item label="住院" style={{ marginBottom: 0 }}>
                      <Input.Group compact>
                        <Form.Item name="admissionNumber" noStyle>
                          <Input aria-label="住院号" style={{ width: '50%' }} placeholder="住院号" />
                        </Form.Item>
                        <Form.Item name="bedNumber" noStyle>
                          <Input aria-label="床号" style={{ width: '50%' }} placeholder="床号" />
                        </Form.Item>
                      </Input.Group>
                    </Form.Item>
                  </Col>
                </Row>

                <div
                  style={{
                    fontWeight: 600,
                    margin: `${token.marginSM}px 0`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <span>2请检信息</span>
                  {/* the lone 急诊 boolean rides the switch-row pattern (ruling 2026-11-19) */}
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <label htmlFor="us-emg">急诊</label>
                    <Form.Item name="emgFlag" valuePropName="checked" noStyle>
                      <Switch id="us-emg" aria-label="急诊" />
                    </Form.Item>
                  </span>
                </div>
                <Row gutter={12}>
                  {gender === 2 && (
                    <Col span={16}>
                      <Form.Item label="妊娠" style={{ marginBottom: 0 }}>
                        <Input.Group compact>
                          <Form.Item name="pregnancyStatus" noStyle>
                            <Select
                              aria-label="妊娠状态"
                              style={{ width: '50%' }}
                              allowClear
                              placeholder="妊娠状态"
                              options={PREGNANCY_OPTIONS}
                            />
                          </Form.Item>
                          <Form.Item name="gestationalWeeks" noStyle>
                            <InputNumber
                              aria-label="孕周"
                              style={{ width: '50%' }}
                              min={1}
                              max={45}
                              placeholder="孕周(周)"
                              disabled={pregnancyDisablesWeeks}
                            />
                          </Form.Item>
                        </Input.Group>
                      </Form.Item>
                    </Col>
                  )}
                  <Col span={8}>
                    <Form.Item name="bookingTime" label="预登记">
                      <DatePicker
                        aria-label="预登记时间"
                        showTime
                        format="YYYY-MM-DD HH:mm:ss"
                        placeholder="预登记时间"
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={16}>
                    <Form.Item name="note" label="临床诊断">
                      <Input.TextArea
                        aria-label="临床诊断"
                        rows={1}
                        maxLength={255}
                        placeholder="选填（入 ext.note）"
                      />
                    </Form.Item>
                  </Col>
                </Row>

                <div style={{ fontWeight: 600, margin: `${token.marginSM}px 0` }}>3检查信息</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: token.marginSM }}>
                  {commonItems.map((item) => (
                    <Button
                      key={item.id}
                      size="small"
                      type={isPicked(item.id) ? 'primary' : 'default'}
                      onClick={() => togglePick(item)}
                    >
                      {item.exam}
                    </Button>
                  ))}
                  {hasPerm('us:order:create') && (
                    <Button size="small" aria-label="添加项目" onClick={() => setItemDialogOpen(true)}>
                      添加项目
                    </Button>
                  )}
                </div>
                <div style={{ minHeight: 34, marginBottom: token.marginSM }}>
                  {picked.length === 0 ? (
                    <span style={{ color: token.colorTextTertiary }}>未选择检查项目</span>
                  ) : (
                    picked.map((item) => (
                      <Tag
                        key={item.id}
                        closable={!saving}
                        onClose={() => removePick(item.id)}
                      >
                        {item.exam}
                      </Tag>
                    ))
                  )}
                </div>
                {hasPerm('us:order:create') && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: token.marginMD }}>
                    <Button
                      type="primary"
                      aria-label="提交登记"
                      loading={saving}
                      onClick={() => void save()}
                    >
                      提交登记
                    </Button>
                  </div>
                )}
                <ConsoleFormHint>提交即登记；选择预登记时间则预约到时段（容量校验，满时段/窗外/过期将拒绝）</ConsoleFormHint>
              </div>
            </Form>
          </div>
        </div>
      ) : (
        <div>
          {/* the wide query row (拍板 03-B: the RCP∪EIS union, uniform 150px controls,
              the 更多条件 collapse + the saved-search bar + 列设置) */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: token.marginSM, alignItems: 'center' }}>
            <Input
              aria-label="检查号条件"
              placeholder="检查号"
              style={{ width: 150 }}
              value={filters.accessionNo}
              onChange={(event) =>
                setFilters((prev) => ({ ...prev, accessionNo: event.target.value }))
              }
              allowClear
            />
            <Input
              aria-label="患者姓名条件"
              placeholder="患者姓名"
              style={{ width: 150 }}
              value={filters.patientName}
              onChange={(event) =>
                setFilters((prev) => ({ ...prev, patientName: event.target.value }))
              }
              allowClear
            />
            <Button aria-label="更多条件" onClick={() => setExtrasOpen((prev) => !prev)}>
              更多条件
            </Button>
            <Button aria-label="查询" onClick={refreshAll}>
              查询
            </Button>
            {hasPerm('us:order:list') && (
              <Button aria-label="保存条件" onClick={() => setSaveDialogOpen(true)}>
                保存条件
              </Button>
            )}
            <Button
              aria-label="我的查询"
              onClick={() => {
                // refresh on open - the modal renders the server's current list
                void (async () => {
                  try {
                    setSavedFilters(await listFilters(PAGE_KEY))
                  } catch {
                    setSavedFilters([])
                  }
                  setSearchListOpen(true)
                })()
              }}
            >
              我的查询
            </Button>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'createTime', label: '创建时间' },
                  { key: 'bookingTime', label: '预登记时间' },
                  { key: 'emgFlag', label: '急诊' },
                ],
                onClick: ({ key }) => persistColumns({ ...hiddenColumns, [key]: !hiddenColumns[key] }),
                selectedKeys: [],
              }}
            >
              <Button aria-label="列设置">列设置</Button>
            </Dropdown>
          </div>
          {extrasOpen && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: token.marginSM, alignItems: 'center', marginTop: token.marginSM }}>
              <Input
                aria-label="患者影像号条件"
                placeholder="患者影像号"
                style={{ width: 150 }}
                value={filters.patientId}
                onChange={(event) =>
                  setFilters((prev) => ({ ...prev, patientId: event.target.value }))
                }
                allowClear
              />
              <Input
                aria-label="就诊卡号条件"
                placeholder="就诊卡号"
                style={{ width: 150 }}
                value={filters.cardNo}
                onChange={(event) =>
                  setFilters((prev) => ({ ...prev, cardNo: event.target.value }))
                }
                allowClear
              />
              <Input
                aria-label="项目名称条件"
                placeholder="检查项目名称"
                style={{ width: 150 }}
                value={filters.examItemName}
                onChange={(event) =>
                  setFilters((prev) => ({ ...prev, examItemName: event.target.value }))
                }
                allowClear
              />
              <Input
                aria-label="申请时间起"
                placeholder="申请时间起"
                style={{ width: 150 }}
                value={filters.applyTimeStart}
                onChange={(event) =>
                  setFilters((prev) => ({ ...prev, applyTimeStart: event.target.value }))
                }
              />
              <Input
                aria-label="申请时间止"
                placeholder="申请时间止"
                style={{ width: 150 }}
                value={filters.applyTimeEnd}
                onChange={(event) =>
                  setFilters((prev) => ({ ...prev, applyTimeEnd: event.target.value }))
                }
              />
            </div>
          )}
          <ConsoleTable<UsOrderView>
            rowKey={(row) => row.orderId}
            loading={loading}
            columns={columns}
            dataSource={rows}
            onRefresh={refreshAll}
            actions={{
              width: 130,
              render: (row) => (
                <>
                  {LIVE_STATES.includes(row.status) && hasPerm('us:order:update') && (
                    <ConsoleAction label="登记" onClick={() => void transitionRegister(row)} />
                  )}
                  {LIVE_STATES.includes(row.status) && hasPerm('us:order:cancel') && (
                    <ConsoleAction
                      label="取消"
                      icon="remove"
                      danger
                      onClick={() => {
                        setCancelTarget(row)
                        setCancelReason('')
                      }}
                    />
                  )}
                </>
              ),
            }}
          />
          <ConsolePagination
            current={pageNum}
            pageSize={pageSize}
            total={total}
            onChange={(page, size) => {
              setPageNum(page)
              setPageSize(size)
            }}
          />
        </div>
      )}

      {/* the candidate list (soft match - the RCP card-style rows) */}
      <Modal
        title="就诊人候选"
        open={candidates != null}
        onCancel={() => setCandidates(null)}
        footer={null}
      >
        {(candidates ?? []).map((identity) => (
          <div
            key={identity.patUid}
            role="button"
            tabIndex={0}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              padding: '8px 4px',
              borderBottom: `1px solid ${token.colorBorderSecondary}`,
              cursor: 'pointer',
            }}
            onClick={() => {
              setCandidates(null)
              fillFromIdentity(identity)
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                setCandidates(null)
                fillFromIdentity(identity)
              }
            }}
          >
            <span>{identity.name ?? '—'}</span>
            <span style={{ color: token.colorTextTertiary }}>
              {[identity.phone, identity.gender === 1 ? '男' : identity.gender === 2 ? '女' : null, identity.birthDate]
                .filter(Boolean)
                .join(' / ')}
            </span>
          </div>
        ))}
      </Modal>

      {/* the 添加项目 dialog (the full US catalog; picking toggles into the picked list) */}
      <Modal
        title="添加检查项目"
        open={itemDialogOpen}
        onCancel={() => setItemDialogOpen(false)}
        onOk={() => setItemDialogOpen(false)}
        okText="确定"
        cancelText="返回"
        width={480}
      >
        <div style={{ maxHeight: 360, overflowY: 'auto' }}>
          {items.map((item) => (
            <div
              key={item.id}
              role="button"
              tabIndex={0}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '8px 4px',
                borderBottom: `1px solid ${token.colorBorderSecondary}`,
                cursor: 'pointer',
                color: isPicked(item.id) ? token.colorPrimary : undefined,
              }}
              onClick={() => togglePick(item)}
              onKeyDown={(event) => event.key === 'Enter' && togglePick(item)}
            >
              <span>{item.exam}</span>
              <span style={{ color: token.colorTextTertiary }}>
                {isPicked(item.id) ? '已选' : (item.modality ?? '')}
              </span>
            </div>
          ))}
        </div>
      </Modal>

      {/* the save-search modal (保存条件) */}
      <Modal
        title="保存当前条件"
        open={saveDialogOpen}
        onOk={() => void saveCurrentSearch()}
        onCancel={() => setSaveDialogOpen(false)}
        okText="确定"
        cancelText="返回"
      >
        <Input
          aria-label="筛选项名称"
          placeholder="筛选项名称"
          value={saveName}
          onChange={(event) => setSaveName(event.target.value)}
        />
      </Modal>

      {/* the saved-search list (我的查询: apply / star default / delete) */}
      <Modal
        title="我的查询"
        open={searchListOpen}
        onCancel={() => setSearchListOpen(false)}
        footer={null}
        width={420}
      >
        {savedFilters.length === 0 && (
          <div style={{ color: token.colorTextTertiary, padding: '12px 0' }}>暂无保存的查询</div>
        )}
        {savedFilters.map((filter) => (
          <div
            key={filter.id}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '8px 4px',
              borderBottom: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <span
              role="button"
              tabIndex={0}
              style={{ cursor: 'pointer' }}
              onClick={() => applySavedSearch(filter)}
              onKeyDown={(event) => event.key === 'Enter' && applySavedSearch(filter)}
            >
              {filter.name}
              {filter.isDefault && <Tag color="blue">默认</Tag>}
            </span>
            <span>
              {!filter.isDefault && (
                <Button size="small" type="link" onClick={() => void markDefaultSearch(filter)}>
                  设默认
                </Button>
              )}
              <Button
                size="small"
                type="link"
                danger
                onClick={() => void removeSavedSearch(filter)}
              >
                删除
              </Button>
            </span>
          </div>
        ))}
      </Modal>

      {/* the cancel-reason modal (取消 requires a reason with confirmation) */}
      <Modal
        title={`取消登记单 ${cancelTarget?.accessionNo ?? ''}`}
        open={cancelTarget != null}
        onOk={() => void submitCancel()}
        onCancel={() => setCancelTarget(null)}
        okText="确定"
        cancelText="返回"
      >
        <Input.TextArea
          aria-label="取消原因"
          rows={2}
          maxLength={255}
          showCount
          placeholder="取消原因（必填，入档可溯）"
          value={cancelReason}
          onChange={(event) => setCancelReason(event.target.value)}
        />
      </Modal>
    </ConsolePage>
  )
}

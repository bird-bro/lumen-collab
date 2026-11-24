import {
  Button,
  Col,
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
import { ApiError } from '@/api/client'
import { usePerm } from '@/utils/perm'
import {
  createFilter,
  deleteFilter,
  listFilters,
  setDefaultFilter,
} from '@/api/search-filters'
import type { SearchFilterView } from '@/api/search-filters'
import { ConsolePage } from '@/pages/system/console-shell'

/**
 * US 工作站两态工作台 (us-registration-redesign; 拍板 01-B/02-B; the UI AUTHORITY is
 * docs/prototypes/us-registration-redesign.html - this render mirrors its 壹 section
 * composition: the 392px rail card carries BOTH states (compact = the datenav + the
 * chips + the 3-line composite rows + the rail-foot; wide = the rail flexes full-width,
 * the compact table yields to the xtable and the rail-xhead query rows, the datenav
 * hides), the edge round button on the card's right edge toggles, and the bench card
 * carries the resolve bar (键型 seg + 查询 + matched chip) + the three fgrid sections
 * (sec-no circles, label 72px right, 3 cols) + the bench-foot (hint + 重置/提交登记).
 * Against the REAL `/api/v1/us/**` backend (no mock layer - ruling 2026-11-05).
 */
const ALL_STATUSES: UsOrderStatus[] = ['BOOKED', 'REGISTERED', 'IN_PROGRESS', 'COMPLETED', 'BOOKING_CANCELLED', 'CANCELLED']
const STATUS_LABELS: Record<UsOrderStatus, string> = {
  BOOKED: '预登记',
  REGISTERED: '已登记',
  IN_PROGRESS: '处理中',
  COMPLETED: '已完成',
  BOOKING_CANCELLED: '预约已取消',
  CANCELLED: '已取消',
}
const DEFAULT_STATUS_FILTER: UsOrderStatus[] = ['BOOKED', 'REGISTERED']
const LIVE_STATES: UsOrderStatus[] = ['BOOKED', 'REGISTERED']
const PREGNANCY_OPTIONS = ['未孕', '早孕', '中晚孕', '未知'].map((label) => ({ value: label, label }))
const GENDER_OPTIONS = [
  { value: 0, label: '未知' },
  { value: 1, label: '男' },
  { value: 2, label: '女' },
]
const ID_TYPE_OPTIONS = ['身份证', '护照', '军官证', '其它'].map((label) => ({ value: label, label }))
const SOURCE_OPTIONS = ['门诊', '住院', '急诊', '体检'].map((label) => ({ value: label, label }))

interface RegistrationFormValues {
  name?: string | null
  gender?: number | null
  ageYears?: string | null
  ageMonths?: string | null
  ageDays?: string | null
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
  bookingDate?: string | null
  bookingSlot?: string | null
  note?: string | null
}

interface WorkbenchFilters {
  status: UsOrderStatus[]
  accessionNo: string
  patientName: string
  patientId: string
  patientSource: string
  cardNo: string
  examItemName: string
  applyTimeStart: string
  applyTimeEnd: string
}

const PAGE_KEY = 'us-registration'
const COLUMNS_KEY = 'us-registration.columns'
const SAVED_FILTER_KEYS = [
  'accessionNo',
  'patientName',
  'patientId',
  'patientSource',
  'cardNo',
  'examItemName',
] as const

/** The status pill colors via the antd tokens (the prototype's rchip mapping). */
function statusChipColors(status: UsOrderStatus, token: {
  colorPrimaryBg: string; colorPrimary: string; colorSuccessBg: string; colorSuccess: string;
  colorFillSecondary: string; colorTextTertiary: string;
}) {
  if (status === 'BOOKED') return { background: token.colorPrimaryBg, color: token.colorPrimary }
  if (status === 'REGISTERED') return { background: token.colorSuccessBg, color: token.colorSuccess }
  return { background: token.colorFillSecondary, color: token.colorTextTertiary }
}

export default function UsRegistrationPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [mode, setMode] = useState<'compact' | 'wide'>('compact')
  const [rows, setRows] = useState<UsOrderView[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [counts, setCounts] = useState<Record<UsOrderStatus, number> | null>(null)
  const [activeDate, setActiveDate] = useState(dayjs())
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [cancelTarget, setCancelTarget] = useState<UsOrderView | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [lookupField, setLookupField] = useState<'idNo' | 'cardNo' | 'phone'>('idNo')
  const [lookupValue, setLookupValue] = useState('')
  const [matchedPatId, setMatchedPatId] = useState<string | null>(null)
  const [candidates, setCandidates] = useState<UsPatientIdentity[] | null>(null)
  const [candidateChecked, setCandidateChecked] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [items, setItems] = useState<ExamItem[]>([])
  const [picked, setPicked] = useState<ExamItem[]>([])
  const [itemDialogOpen, setItemDialogOpen] = useState(false)
  const [itemSearch, setItemSearch] = useState('')
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
      setCounts(null)
    }
  }, [activeDate, dateRange])

  useEffect(() => {
    void load({ pageNum, pageSize })
    void loadCounts()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageNum, pageSize, filters, activeDate])

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

  // the saved searches (拍板 07-A)
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
              if (typeof value === 'string' && value) restored[key] = value
            }
            return restored
          })
        }
      } catch {
        if (alive) setSavedFilters([])
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
        if (filters[key]) query[key] = filters[key]
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

  // ---------------- the resolution flow (键型 seg, the three outcomes) ---------

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
    const query = lookupField === 'idNo' ? { idNo: key } : lookupField === 'cardNo' ? { cardNo: key } : { phone: key }
    try {
      const result = await resolvePatients(query)
      if (result.result === 'MATCHED' && result.identity) {
        fillFromIdentity(result.identity)
        messageApi.success(`已匹配档案 ${result.identity.patId ?? ''}`)
      } else if (result.result === 'CANDIDATES' && result.candidates?.length) {
        setCandidates(result.candidates)
        setCandidateChecked(result.candidates[0]?.patUid ?? null)
      } else {
        form.resetFields()
        setMatchedPatId(null)
        messageApi.info('未匹配到档案，请建档登记')
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '患者解析失败')
    }
  }

  // ---------------- the items (common chips + the add dialog) ------------------

  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const page = await pageExamItems({ pageNum: 1, pageSize: 200, modality: 'US' } as never)
        if (alive) setItems(page.records)
      } catch {
        if (alive) setItems([])
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  const commonItems = items.filter((item) => item.isCommon)
  const dialogItems = items.filter((item) => {
    const keyword = itemSearch.trim()
    if (!keyword) return true
    return (item.exam ?? '').includes(keyword) || (item.code ?? '').includes(keyword)
  })
  const isPicked = (id: number) => picked.some((item) => item.id === id)
  const togglePick = (item: ExamItem) => {
    setPicked((prev) =>
      isPicked(item.id) ? prev.filter((picked_) => picked_.id !== item.id) : [...prev, item],
    )
  }
  const removePick = (id: number) => setPicked((prev) => prev.filter((item) => item.id !== id))
  const pickedTotal = picked.reduce((sum, item) => sum + (item.price ?? 0), 0)

  // ---------------- the submit (提交即登记, 拍板 02-B) --------------------------

  const resetWorkbench = () => {
    form.resetFields()
    setPicked([])
    setMatchedPatId(null)
    setLookupValue('')
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      if (picked.length === 0) {
        messageApi.warning('请选择检查项目')
        return
      }
      setSaving(true)
      const ageParts: string[] = []
      if (values.ageYears) ageParts.push(`${values.ageYears}岁`)
      if (values.ageMonths) ageParts.push(`${values.ageMonths}月`)
      if (values.ageDays) ageParts.push(`${values.ageDays}天`)
      const bookingSlot = (values.bookingSlot ?? '').trim()
      const bookingDate = (values.bookingDate ?? '').trim()
      const body: UsOrderCreateBody = {
        idType: values.idType ?? null,
        idNo: values.idNo ?? null,
        cardNo: values.cardNo ?? null,
        name: values.name?.trim() ? values.name.trim() : null,
        gender: values.gender ?? null,
        phone: values.phone ?? null,
        age: ageParts.length ? ageParts.join('') : null,
        patientSource: values.patientSource ?? null,
        admissionNumber: values.admissionNumber ?? null,
        bedNumber: values.bedNumber ?? null,
        height: values.height ?? null,
        weight: values.weight ?? null,
        bookingTime:
          bookingDate && bookingSlot
            ? `${bookingDate} ${bookingSlot}:00`
            : null,
        examItems: picked.map((item) => ({
          itemId: item.id,
          itemName: item.exam,
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
      resetWorkbench()
      refreshAll()
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg)
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return
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
      messageApi.error(error instanceof ApiError ? error.msg : '登记失败')
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

  const toggleStatus = (status: UsOrderStatus) => {
    setFilters((prev) => ({
      ...prev,
      status: prev.status.includes(status)
        ? prev.status.filter((item) => item !== status)
        : [...prev.status, status],
    }))
  }

  const shiftDate = (days: number) => {
    setActiveDate((prev) => prev.add(days, 'day'))
    setFilters((prev) => ({ ...prev, applyTimeStart: '', applyTimeEnd: '' }))
  }

  const rowItemsText = (row: UsOrderView) => (row.examItems ?? []).map((item) => item.itemName).join('、')
  const rowPregnancy = (row: UsOrderView) =>
    row.pregnancyStatus
      ? `${row.pregnancyStatus}${row.gestationalWeeks ? `${row.gestationalWeeks}周` : ''}`
      : null

  // ---------------- the shared prototype styles (via the antd tokens) ----------

  const line = token.colorBorderSecondary
  const headBg = token.colorFillQuaternary
  const railHeadStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap',
    padding: '8px 10px', borderBottom: `1px solid ${line}`, background: headBg,
  }
  const schipStyle = (active: boolean, hue: 'blue' | 'green' | 'plain') => ({
    display: 'inline-flex', alignItems: 'center', gap: 5, height: 24, padding: '0 9px',
    borderRadius: 999, fontSize: 12, cursor: 'pointer', userSelect: 'none' as const,
    border: `1px solid ${active ? (hue === 'green' ? token.colorSuccess : hue === 'blue' ? token.colorPrimary : token.colorBorder) : token.colorBorderSecondary}`,
    background: active ? (hue === 'green' ? token.colorSuccessBg : hue === 'blue' ? token.colorPrimaryBg : token.colorBgContainer) : token.colorBgContainer,
    color: active ? (hue === 'green' ? token.colorSuccess : hue === 'blue' ? token.colorPrimary : token.colorText) : token.colorTextSecondary,
    fontWeight: active ? 600 : 400,
  })
  const inputStyle: React.CSSProperties = {}
  const thStyle: React.CSSProperties = {
    textAlign: 'left', fontWeight: 500, color: token.colorTextSecondary, fontSize: 12,
    background: token.colorFillQuaternary, borderBottom: `1px solid ${line}`, padding: '6px 8px', whiteSpace: 'nowrap',
  }
  const tdStyle: React.CSSProperties = {
    borderBottom: `1px solid ${token.colorFillSecondary}`, padding: '7px 8px', verticalAlign: 'middle',
  }

  const statusChip = (row: UsOrderView) => (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', height: 19, padding: '0 8px',
        fontSize: 11, borderRadius: 999, whiteSpace: 'nowrap', fontWeight: 500,
        ...statusChipColors(row.status, token),
      }}
    >
      {STATUS_LABELS[row.status]}
    </span>
  )

  const railRowActions = (row: UsOrderView) => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
      {LIVE_STATES.includes(row.status) && hasPerm('us:order:update') && (
        <Button
          size="small"
          type="text"
          aria-label={`登记 ${row.accessionNo}`}
          title="登记"
          style={{ width: 22, height: 22, minWidth: 22, padding: 0, color: token.colorPrimary }}
          onClick={() => void transitionRegister(row)}
        >
          ✓
        </Button>
      )}
      {LIVE_STATES.includes(row.status) && hasPerm('us:order:cancel') && (
        <Button
          size="small"
          type="text"
          aria-label={`取消 ${row.accessionNo}`}
          title="取消登记"
          danger
          style={{ width: 22, height: 22, minWidth: 22, padding: 0 }}
          onClick={() => {
            setCancelTarget(row)
            setCancelReason('')
          }}
        >
          🗑
        </Button>
      )}
      {!LIVE_STATES.includes(row.status) && <span style={{ color: token.colorTextTertiary }}>—</span>}
    </div>
  )

  const emgTag = <span style={{ fontSize: 10.5, padding: '0 5px', lineHeight: '16px', borderRadius: 3, background: token.colorWarningBg, color: token.colorWarning, fontWeight: 600 }}>急</span>
  const sourceTag = (source: string | null | undefined) =>
    source ? <span style={{ fontSize: 10.5, padding: '0 5px', lineHeight: '16px', borderRadius: 3, background: token.colorFillSecondary, color: token.colorTextSecondary }}>{source}</span> : null

  const compositeCell = (row: UsOrderView) => (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: row.patientSnapshot?.name ? undefined : token.colorTextTertiary }}>
          {row.patientSnapshot?.name ?? '无名氏'}
        </span>
        {sourceTag(row.patientSnapshot?.patientSource)}
        {row.emgFlag === 1 && emgTag}
      </div>
      <div style={{ fontSize: 11.5, color: token.colorTextSecondary, marginTop: 2 }}>
        {rowItemsText(row)}
        {rowPregnancy(row) && <span style={{ color: token.colorInfo }}> · {rowPregnancy(row)}</span>}
      </div>
      <div style={{ fontSize: 11, color: token.colorTextTertiary, marginTop: 1, fontFamily: 'monospace' }}>
        {row.accessionNo} · {row.bookingTime ? `预登记 ${row.bookingTime.slice(11, 16)}` : (row.createTime ?? '').slice(11, 16)}
      </div>
    </div>
  )

  return (
    <ConsolePage>
      {contextHolder}
      <div style={{ display: 'flex', gap: 8, alignItems: 'stretch', flex: 1, minHeight: 0 }}>
        {/* ── the rail card: BOTH states live here (compact grid / expanded xtable) ── */}
        <div
          style={{
            background: token.colorBgContainer,
            border: `1px solid ${line}`,
            borderRadius: 10,
            boxShadow: token.boxShadowSecondary,
            overflow: 'visible',
            position: 'relative',
            flex: mode === 'wide' ? '1 1 0%' : '1 0 30%',
            width: mode === 'wide' ? 'auto' : undefined,
            minWidth: mode === 'wide' ? undefined : 340,
            maxWidth: mode === 'wide' ? undefined : 480,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {/* the edge round button: » expands the wide query state, « returns */}
          <Button
            aria-label="工作台切换"
            title={mode === 'compact' ? '展开完整列表（查询态）' : '返回工作台'}
            shape="circle"
            style={{
              position: 'absolute', top: '50%', right: -13, transform: 'translateY(-50%)',
              width: 26, height: 26, minWidth: 26, zIndex: 6,
              background: token.colorBgContainer, color: token.colorTextSecondary,
              border: `1px solid ${token.colorBorderSecondary}`,
              boxShadow: token.boxShadowSecondary,
            }}
            onClick={() => setMode((prev) => (prev === 'compact' ? 'wide' : 'compact'))}
          >
            {mode === 'compact' ? '»' : '«'}
          </Button>

          {/* the rail head: the datenav (compact) + the chips + the mini autorefresh */}
          <div style={railHeadStyle}>
            {mode === 'compact' && (
              <span role="group" aria-label="日期导航" style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12 }}>
                <Button size="small" aria-label="上一日" style={{ width: 18, height: 22, minWidth: 18, padding: 0, fontSize: 11 }} onClick={() => shiftDate(-1)}>‹</Button>
                <span><b>{activeDate.format('MM-DD')}</b> <span style={{ color: token.colorTextTertiary, fontSize: 11 }}>{activeDate.isSame(dayjs(), 'day') ? '今天' : ''}</span></span>
                <Button size="small" aria-label="下一日" style={{ width: 18, height: 22, minWidth: 18, padding: 0, fontSize: 11 }} onClick={() => shiftDate(1)}>›</Button>
              </span>
            )}
            {ALL_STATUSES.map((status) => {
              const active = filters.status.includes(status)
              const hue = status === 'BOOKED' ? 'blue' : status === 'REGISTERED' ? 'green' : 'plain'
              return (
                <span
                  key={status}
                  role="button"
                  aria-label={`${STATUS_LABELS[status]} ${counts?.[status] ?? 0}`}
                  style={{ ...schipStyle(active, hue) }}
                  onClick={() => toggleStatus(status)}
                >
                  {STATUS_LABELS[status]} <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{counts?.[status] ?? 0}</span>
                </span>
              )
            })}
            <span style={{ flex: 1 }} />
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 11.5, color: token.colorTextTertiary }}>
              自动刷新
              <Switch id="us-autorefresh" aria-label="自动刷新" size="small" checked={autoRefresh} onChange={setAutoRefresh} />
            </span>
          </div>

          {/* the wide query rows (rail-xhead — wide mode only) */}
          {mode === 'wide' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '8px 10px', borderBottom: `1px solid ${line}`, background: headBg }}>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                  <span style={{ fontSize: 12, color: token.colorTextSecondary }}>申请时间</span>
                  <Input aria-label="申请时间起" style={{ ...inputStyle, width: "clamp(110px, 12vw, 160px)" }} value={filters.applyTimeStart} placeholder="YYYY-MM-DD HH:mm:ss" onChange={(e) => setFilters((p) => ({ ...p, applyTimeStart: e.target.value }))} />
                  <span style={{ color: token.colorTextTertiary }}>~</span>
                  <Input aria-label="申请时间止" style={{ ...inputStyle, width: "clamp(110px, 12vw, 160px)" }} value={filters.applyTimeEnd} placeholder="YYYY-MM-DD HH:mm:ss" onChange={(e) => setFilters((p) => ({ ...p, applyTimeEnd: e.target.value }))} />
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                  <span style={{ fontSize: 12, color: token.colorTextSecondary }}>患者姓名</span>
                  <Input aria-label="患者姓名条件" style={{ ...inputStyle, width: 150 }} value={filters.patientName} placeholder="请输入" onChange={(e) => setFilters((p) => ({ ...p, patientName: e.target.value }))} allowClear />
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                  <span style={{ fontSize: 12, color: token.colorTextSecondary }}>检查号</span>
                  <Input aria-label="检查号条件" style={{ ...inputStyle, width: 150, fontFamily: 'monospace' }} value={filters.accessionNo} placeholder="US…" onChange={(e) => setFilters((p) => ({ ...p, accessionNo: e.target.value }))} allowClear />
                </span>
                {extrasOpen && (
                  <>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ fontSize: 12, color: token.colorTextSecondary }}>患者编号</span>
                      <Input aria-label="患者编号条件" style={{ ...inputStyle, width: 150, fontFamily: 'monospace' }} value={filters.patientId} placeholder="P…" onChange={(e) => setFilters((p) => ({ ...p, patientId: e.target.value }))} allowClear />
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ fontSize: 12, color: token.colorTextSecondary }}>患者类型</span>
                      <Select aria-label="患者类型条件" style={{ width: 150 }} size="small" allowClear placeholder="全部" value={filters.patientSource || undefined}
                        options={SOURCE_OPTIONS}
                        onChange={(value) => setFilters((p) => ({ ...p, patientSource: value ?? '' }))} />
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ fontSize: 12, color: token.colorTextSecondary }}>检查项目</span>
                      <Input aria-label="项目名称条件" style={{ ...inputStyle, width: 150 }} value={filters.examItemName} placeholder="模糊匹配" onChange={(e) => setFilters((p) => ({ ...p, examItemName: e.target.value }))} allowClear />
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ fontSize: 12, color: token.colorTextSecondary }}>就诊卡号</span>
                      <Input aria-label="就诊卡号条件" style={{ ...inputStyle, width: 150, fontFamily: 'monospace' }} value={filters.cardNo} placeholder="M…" onChange={(e) => setFilters((p) => ({ ...p, cardNo: e.target.value }))} allowClear />
                    </span>
                  </>
                )}
                <Button type="primary" aria-label="查询" onClick={refreshAll}>查询</Button>
                <Button
                  aria-label="重置条件"
                  onClick={() => {
                    setFilters((prev) => ({ ...prev, accessionNo: '', patientName: '', patientId: '', patientSource: '', cardNo: '', examItemName: '', applyTimeStart: '', applyTimeEnd: '' }))
                  }}
                >
                  重置
                </Button>
                {hasPerm('us:order:list') && (
                  <Button aria-label="保存条件" onClick={() => setSaveDialogOpen(true)}>
                    保存条件
                  </Button>
                )}
                <Button aria-label="我的查询" onClick={() => {
                  void (async () => {
                    try {
                      setSavedFilters(await listFilters(PAGE_KEY))
                    } catch {
                      setSavedFilters([])
                    }
                    setSearchListOpen(true)
                  })()
                }}>
                  ★ 我的查询 ▾
                </Button>
                <span
                  role="button"
                  aria-label="更多条件"
                  style={{ fontSize: 12, color: token.colorPrimary, cursor: 'pointer', marginLeft: 'auto', whiteSpace: 'nowrap', userSelect: 'none' }}
                  onClick={() => setExtrasOpen((prev) => !prev)}
                >
                  {extrasOpen ? '收起更多 ▴' : '更多条件 ▾'}
                </span>
              </div>
            </div>
          )}

          {/* the COMPACT table (3-line composite rows; the list area scrolls, the foot pins) */}
          {mode === 'compact' && (
            <>
              <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
                <thead>
                  <tr>
                    <th style={thStyle}>患者 / 项目</th>
                    <th style={{ ...thStyle, width: 62 }}>状态</th>
                    <th style={{ ...thStyle, width: 52 }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.orderId}>
                      <td style={tdStyle}>{compositeCell(row)}</td>
                      <td style={tdStyle}>{statusChip(row)}</td>
                      <td style={tdStyle}>{railRowActions(row)}</td>
                    </tr>
                  ))}
                  {rows.length === 0 && !loading && (
                    <tr><td colSpan={3} style={{ padding: 16, color: token.colorTextTertiary, textAlign: 'center' }}>当日暂无登记单</td></tr>
                  )}
                </tbody>
              </table>
              </div>
              <div style={{ marginTop: 'auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '7px 10px', borderTop: `1px solid ${line}`, fontSize: 11.5, color: token.colorTextSecondary, background: headBg }}>
                <span>{activeDate.format('MM-DD')} · {total} 单</span>
                <span style={{ display: 'flex', gap: 4 }}>
                  <Button size="small" style={{ minWidth: 20, height: 20, padding: '0 5px', fontSize: 11.5 }} onClick={() => setPageNum((p) => Math.max(1, p - 1))}>‹</Button>
                  <Button size="small" type="primary" style={{ minWidth: 20, height: 20, padding: '0 5px', fontSize: 11.5 }}>{pageNum}</Button>
                  <Button size="small" style={{ minWidth: 20, height: 20, padding: '0 5px', fontSize: 11.5 }} onClick={() => setPageNum((p) => p + 1)}>›</Button>
                </span>
              </div>
            </>
          )}

          {/* the WIDE xtable (the full columns + the 列设置 gear in the ops header) */}
          {mode === 'wide' && (
            <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
              <thead>
                <tr>
                  <th style={{ ...thStyle, minWidth: 150 }}>患者</th>
                  {!hiddenColumns.items && <th style={thStyle}>检查项目</th>}
                  {!hiddenColumns.status && <th style={{ ...thStyle, width: 72 }}>状态</th>}
                  {!hiddenColumns.emg && <th style={{ ...thStyle, width: 48 }}>急诊</th>}
                  {!hiddenColumns.pregnancy && <th style={{ ...thStyle, width: 88 }}>孕检</th>}
                  {!hiddenColumns.accessionNo && <th style={{ ...thStyle, width: 108 }}>检查号</th>}
                  {!hiddenColumns.createTime && <th style={{ ...thStyle, width: 118 }}>创建时间</th>}
                  {!hiddenColumns.note && <th style={thStyle}>临床诊断</th>}
                  <th style={{ ...thStyle, width: 64 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      操作
                      <Dropdown
                        trigger={['click']}
                        menu={{
                          items: [
                            { key: 'patient', label: '患者' },
                            { key: 'items', label: '检查项目' },
                            { key: 'status', label: '状态' },
                            { key: 'emg', label: '急诊' },
                            { key: 'pregnancy', label: '孕检' },
                            { key: 'accessionNo', label: '检查号' },
                            { key: 'createTime', label: '创建时间' },
                            { key: 'note', label: '临床诊断' },
                          ],
                          onClick: ({ key }) => persistColumns({ ...hiddenColumns, [key]: !hiddenColumns[key] }),
                        }}
                      >
                        <Button aria-label="列设置" type="text" size="small" style={{ padding: 0, color: token.colorTextTertiary }}>
                          ⚙
                        </Button>
                      </Dropdown>
                    </div>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const preg = rowPregnancy(row)
                  return (
                    <tr key={row.orderId}>
                      <td style={tdStyle}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                          <span style={{ fontSize: 13, fontWeight: 600 }}>{row.patientSnapshot?.name ?? '无名氏'}</span>
                          {sourceTag(row.patientSnapshot?.patientSource)}
                          {row.emgFlag === 1 && emgTag}
                        </div>
                        <div style={{ fontSize: 11, color: token.colorTextTertiary, fontFamily: 'monospace', marginTop: 2 }}>
                          {row.patientSnapshot?.patId ?? ''}{row.patientSnapshot?.gender === 1 ? ' · 男' : row.patientSnapshot?.gender === 2 ? ' · 女' : ''}{row.patientSnapshot?.age ? ` · ${row.patientSnapshot.age}` : ''}
                        </div>
                      </td>
                      {!hiddenColumns.items && <td style={{ ...tdStyle, fontSize: 11.5, color: token.colorTextSecondary }}>
                        {rowItemsText(row)}
                        {row.bookingTime && <span style={{ color: token.colorInfo }}> · 预登记 {row.bookingTime.slice(11, 16)}</span>}
                      </td>}
                      {!hiddenColumns.status && <td style={tdStyle}>{statusChip(row)}</td>}
                      {!hiddenColumns.emg && <td style={tdStyle}>{row.emgFlag === 1 ? emgTag : null}</td>}
                      {!hiddenColumns.pregnancy && <td style={{ ...tdStyle, fontSize: 12, color: token.colorTextSecondary }}>{preg ? <span><b>{row.pregnancyStatus}</b>{row.gestationalWeeks ? ` · ${row.gestationalWeeks}周` : ''}</span> : '—'}</td>}
                      {!hiddenColumns.accessionNo && <td style={{ ...tdStyle, fontFamily: 'monospace', fontSize: 11.5, color: token.colorTextSecondary }}>{row.accessionNo}</td>}
                      {!hiddenColumns.createTime && <td style={{ ...tdStyle, fontSize: 12, color: token.colorTextSecondary }}>{row.createTime}</td>}
                      {!hiddenColumns.note && <td style={{ ...tdStyle, fontSize: 11.5, color: token.colorTextTertiary, maxWidth: 130, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.note ?? '—'}</td>}
                      <td style={tdStyle}>{railRowActions(row)}</td>
                    </tr>
                  )
                })}
                {rows.length === 0 && !loading && (
                  <tr><td colSpan={9} style={{ padding: 16, color: token.colorTextTertiary, textAlign: 'center' }}>暂无符合条件的登记单</td></tr>
                )}
              </tbody>
            </table>
            </div>
          )}
        </div>

        {/* ── the bench card (compact only — the wide state takes the rail's full width) ── */}
        {mode === 'compact' && (
          <div
            style={{
              flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column',
              background: token.colorBgContainer, border: `1px solid ${line}`,
              borderRadius: 10, boxShadow: token.boxShadowSecondary, overflow: 'hidden',
            }}
          >
            {/* the resolve bar: 键型 seg + 键值 + 查询 + the matched chip */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 14px', borderBottom: `1px solid ${line}`, background: headBg, flexWrap: 'wrap' }}>
              <Radio.Group
                aria-label="解析字段"
                value={lookupField}
                onChange={(event) => setLookupField(event.target.value)}
                optionType="button"
                buttonStyle="solid"
                size="small"
                options={[
                  { value: 'idNo', label: '证件号' },
                  { value: 'cardNo', label: '就诊卡号' },
                  { value: 'phone', label: '手机号' },
                ]}
                disabled={!hasPerm('us:order:create')}
              />
              <Input
                aria-label="解析键值"
                style={{ width: 220 }}
                placeholder={lookupField === 'idNo' ? '证件号' : lookupField === 'cardNo' ? '就诊卡号' : '手机号'}
                value={lookupValue}
                onChange={(event) => setLookupValue(event.target.value)}
                onPressEnter={() => void runLookup()}
                disabled={!hasPerm('us:order:create')}
              />
              {hasPerm('us:order:create') && (
                <Button type="primary" aria-label="解析查询" onClick={() => void runLookup()}>
                  查询
                </Button>
              )}
              {matchedPatId && (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: 24, padding: '0 10px', borderRadius: 999, background: token.colorSuccessBg, color: token.colorSuccess, fontSize: 12 }}>
                  ✓ 已匹配档案 <span style={{ fontFamily: 'monospace' }}>{matchedPatId}</span>，已回填
                </span>
              )}
            </div>

            <Form form={form} disabled={saving} style={{ flex: 1, overflow: 'auto' }}>
              <div style={{ padding: '10px 16px 4px' }}>
                {/* 1 患者信息 */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '2px 0 10px', paddingBottom: 6, borderBottom: `1px solid ${line}` }}>
                  <span style={{ width: 18, height: 18, borderRadius: '50%', background: token.colorPrimary, color: token.colorBgContainer, fontSize: 11, fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>1</span>
                  <span style={{ fontSize: 13, fontWeight: 600 }}>患者信息</span>
                </div>
                <Row gutter={[14, 9]} style={{ marginBottom: 8 }}>
                  <Field label="姓名" required>
                    <Form.Item name="name" noStyle>
                      <Input aria-label="姓名" style={inputStyle} placeholder="急诊单可留空（占位档案）" />
                    </Form.Item>
                  </Field>
                  <Field label="性别">
                    <Form.Item name="gender" noStyle>
                      <Radio.Group aria-label="性别" options={GENDER_OPTIONS} />
                    </Form.Item>
                  </Field>
                  <Field label="年龄">
                    <div style={{ display: 'flex', gap: 6 }}>
                      <Form.Item name="ageYears" noStyle>
                        <Input aria-label="年龄-岁" style={{ ...inputStyle, flex: 1, textAlign: 'center' }} placeholder="岁" />
                      </Form.Item>
                      <Form.Item name="ageMonths" noStyle>
                        <Input aria-label="年龄-月" style={{ ...inputStyle, flex: '0 0 44px', textAlign: 'center' }} placeholder="月" />
                      </Form.Item>
                      <Form.Item name="ageDays" noStyle>
                        <Input aria-label="年龄-天" style={{ ...inputStyle, flex: '0 0 44px', textAlign: 'center' }} placeholder="天" />
                      </Form.Item>
                    </div>
                  </Field>
                  <Field label="就诊卡号">
                    <Form.Item name="cardNo" noStyle>
                      <Input aria-label="卡号" style={inputStyle} />
                    </Form.Item>
                  </Field>
                  <Field label="证件">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                      <Form.Item name="idType" noStyle>
                        <Select aria-label="证件类型" style={{ width: 96 }} size="small" allowClear placeholder="类型" options={ID_TYPE_OPTIONS} />
                      </Form.Item>
                      <Form.Item name="idNo" noStyle>
                        <Input aria-label="证件号" style={{ ...inputStyle, flex: 1, minWidth: 0 }} />
                      </Form.Item>
                    </div>
                  </Field>
                  <Field label="手机号">
                    <Form.Item name="phone" noStyle>
                      <Input aria-label="电话" style={inputStyle} />
                    </Form.Item>
                  </Field>
                  <Field label="患者来源">
                    <Form.Item name="patientSource" noStyle>
                      <Select aria-label="患者来源" style={{ width: '100%' }} size="small" allowClear placeholder="请选择" options={SOURCE_OPTIONS} />
                    </Form.Item>
                  </Field>
                  <Field label="住院/床号">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                      <Form.Item name="admissionNumber" noStyle>
                        <Input aria-label="住院号" style={{ ...inputStyle, flex: 1, minWidth: 0 }} placeholder="住院患者填写" />
                      </Form.Item>
                      <span style={{ color: token.colorTextTertiary, fontSize: 12 }}>/</span>
                      <Form.Item name="bedNumber" noStyle>
                        <Input aria-label="床号" style={{ ...inputStyle, flex: '0 0 56px' }} placeholder="床号" />
                      </Form.Item>
                    </div>
                  </Field>
                  <Field label="身高体重">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                      <Form.Item name="height" noStyle>
                        <InputNumber aria-label="身高" style={{ flex: 1, minWidth: 0 }} min={0} placeholder="身高cm" />
                      </Form.Item>
                      <span style={{ color: token.colorTextTertiary, fontSize: 12 }}>/</span>
                      <Form.Item name="weight" noStyle>
                        <InputNumber aria-label="体重" style={{ flex: 1, minWidth: 0 }} min={0} placeholder="体重kg" />
                      </Form.Item>
                    </div>
                  </Field>
                </Row>

                {/* 2 请检信息 (the 急诊 switch in the section title tail) */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '2px 0 10px', paddingBottom: 6, borderBottom: `1px solid ${line}` }}>
                  <span style={{ width: 18, height: 18, borderRadius: '50%', background: token.colorPrimary, color: token.colorBgContainer, fontSize: 11, fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>2</span>
                  <span style={{ fontSize: 13, fontWeight: 600 }}>请检信息</span>
                  <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 14 }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: token.colorTextSecondary }}>
                      急诊
                      <Form.Item name="emgFlag" valuePropName="checked" noStyle>
                        <Switch id="us-emg" aria-label="急诊" size="small" />
                      </Form.Item>
                    </span>
                  </span>
                </div>
                <Row gutter={[14, 9]} style={{ marginBottom: 8 }}>
                  <Field label="检查类型">
                    <span style={{ display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 10px', borderRadius: 4, background: token.colorPrimaryBg, color: token.colorPrimary, fontSize: 12, fontWeight: 600, letterSpacing: '0.06em' }}>
                      US 超声
                    </span>
                  </Field>
                  {gender === 2 && (
                    <Field label="妊娠">
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                        <Form.Item name="pregnancyStatus" noStyle>
                          <Select aria-label="妊娠状态" style={{ flex: 1 }} size="small" allowClear placeholder="妊娠状态" options={PREGNANCY_OPTIONS} />
                        </Form.Item>
                        <Form.Item name="gestationalWeeks" noStyle>
                          <InputNumber aria-label="孕周" style={{ width: 72, minWidth: 0 }} min={1} max={45} placeholder="周" disabled={pregnancyStatus == null || pregnancyStatus === '未孕' || pregnancyStatus === '未知'} />
                        </Form.Item>
                      </div>
                    </Field>
                  )}
                  <Field label="预登记时间">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                      <Form.Item name="bookingDate" noStyle>
                        <Input aria-label="预登记日期" style={{ width: 110, height: 28 }} placeholder="YYYY-MM-DD" />
                      </Form.Item>
                      <Form.Item name="bookingSlot" noStyle>
                        <Input aria-label="预登记时段" style={{ ...inputStyle, flex: 1 }} placeholder="HH:mm（如 08:00）" />
                      </Form.Item>
                    </div>
                  </Field>
                  <Field label="临床诊断" full>
                    <Form.Item name="note" noStyle>
                      <Input.TextArea aria-label="临床诊断" rows={2} maxLength={255} placeholder="选填（入 ext.note）" style={{ width: '100%' }} />
                    </Form.Item>
                  </Field>
                </Row>

                {/* 3 检查信息 */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '2px 0 10px', paddingBottom: 6, borderBottom: `1px solid ${line}` }}>
                  <span style={{ width: 18, height: 18, borderRadius: '50%', background: token.colorPrimary, color: token.colorBgContainer, fontSize: 11, fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>3</span>
                  <span style={{ fontSize: 13, fontWeight: 600 }}>检查信息</span>
                  <span style={{ marginLeft: 'auto', fontSize: 11.5, color: token.colorTextTertiary }}>US 模态项目目录（检查设置）</span>
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 8, alignItems: 'center' }}>
                  {commonItems.map((item) => {
                    const used = isPicked(item.id)
                    return (
                      <span
                        key={item.id}
                        role="button"
                        aria-label={`${item.exam}${used ? '（已选）' : ''}`}
                        style={{
                          display: 'inline-flex', alignItems: 'center', gap: 5, height: 24, padding: '0 10px',
                          border: `1px ${used ? 'solid' : 'dashed'} ${used ? token.colorBorderSecondary : token.colorPrimaryBorder}`,
                          borderRadius: 999, background: used ? token.colorFillSecondary : token.colorPrimaryBg,
                          fontSize: 12, color: used ? token.colorTextTertiary : token.colorPrimary,
                          cursor: used ? 'default' : 'pointer', userSelect: 'none',
                          opacity: used ? 0.45 : 1,
                        }}
                        onClick={() => !used && togglePick(item)}
                      >
                        {used ? `${item.exam} ✓` : `＋ ${item.exam}`}
                      </span>
                    )
                  })}
                  {hasPerm('us:order:create') && (
                    <Button size="small" aria-label="添加项目" style={{ marginLeft: 'auto', height: 24, padding: '0 10px', fontSize: 12 }} onClick={() => setItemDialogOpen(true)}>
                      添加项目
                    </Button>
                  )}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: token.colorSuccessBg, borderRadius: 5, padding: '5px 12px', marginBottom: 6, fontSize: 12.5, fontWeight: 600 }}>
                  <span>已选择<span style={{ color: token.colorPrimary, fontSize: 14, margin: '0 3px' }}>{picked.length}</span>个检查项目</span>
                  <span>合计：¥{pickedTotal.toFixed(2)}</span>
                </div>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                  <thead>
                    <tr>
                      <th style={thStyle}>检查项目</th>
                      <th style={{ ...thStyle, width: 120 }}>检查部位</th>
                      <th style={{ ...thStyle, width: 88, textAlign: 'right' }}>单价(元)</th>
                      <th style={{ ...thStyle, width: 48 }}>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {picked.map((item) => (
                      <tr key={item.id}>
                        <td style={tdStyle}>{item.exam}</td>
                        <td style={tdStyle}>{item.bodyName ?? '—'}</td>
                        <td style={{ ...tdStyle, textAlign: 'right', fontFamily: 'monospace' }}>{(item.price ?? 0).toFixed(2)}</td>
                        <td style={tdStyle}>
                          <span style={{ color: token.colorError, cursor: 'pointer', fontSize: 11.5 }} onClick={() => removePick(item.id)}>删除</span>
                        </td>
                      </tr>
                    ))}
                    {picked.length === 0 && (
                      <tr><td colSpan={4} style={{ padding: '5px 9px', color: token.colorTextTertiary }}>未选择检查项目</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </Form>

            {/* the bench foot: the hint + 重置/提交登记 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, padding: '9px 14px', borderTop: `1px solid ${line}`, background: headBg }}>
              <span style={{ fontSize: 11.5, color: token.colorTextTertiary }}>
                急诊单姓名可留空——留空即无名氏占位档，检查号照发 · 提交即登记：现场单直接落 已登记；选未来时段即 预登记（BOOKED），患者到检后在列表点 登记 确认
              </span>
              <span style={{ display: 'flex', gap: 8 }}>
                {hasPerm('us:order:create') && (
                  <Button danger style={{ background: token.colorBgContainer }} onClick={resetWorkbench}>
                    重置
                  </Button>
                )}
                {hasPerm('us:order:create') && (
                  <Button type="primary" aria-label="提交登记" loading={saving} onClick={() => void save()}>
                    提交登记
                  </Button>
                )}
              </span>
            </div>
          </div>
        )}
      </div>

      {/* the 就诊人候选 dialog (the RCP pcard style: select then 是/否) */}
      <Modal title="就诊人" open={candidates != null} onCancel={() => { setCandidates(null); setCandidateChecked(null) }} footer={null} width={460}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 9, maxHeight: 320, overflow: 'auto', padding: '12px 14px' }}>
          {(candidates ?? []).map((identity) => {
            const checked = candidateChecked === identity.patUid
            return (
              <div
                key={identity.patUid}
                role="button"
                tabIndex={0}
                style={{
                  position: 'relative', border: `1px solid ${checked ? token.colorPrimary : line}`, borderRadius: 8,
                  padding: '10px 13px', background: checked ? token.colorPrimaryBg : token.colorBgLayout,
                  cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 12,
                }}
                onClick={() => setCandidateChecked(identity.patUid)}
                onKeyDown={(event) => event.key === 'Enter' && setCandidateChecked(identity.patUid)}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                    <span style={{ fontSize: 16, fontWeight: 700 }}>{identity.name ?? '—'}</span>
                    <span style={{ fontSize: 12, color: token.colorTextSecondary, fontWeight: 600 }}>{identity.gender === 1 ? '男' : identity.gender === 2 ? '女' : ''}</span>
                    <span style={{ fontSize: 11, color: token.colorTextTertiary, fontFamily: 'monospace' }}>{identity.birthDate ?? ''}</span>
                  </div>
                  <div style={{ fontSize: 12, color: token.colorTextSecondary, lineHeight: 1.75 }}>
                    <span style={{ color: token.colorTextTertiary, marginRight: 2 }}>就诊号</span>{identity.cardNo ?? '—'} · <span style={{ color: token.colorTextTertiary, marginRight: 2 }}>证件号</span>{identity.idNo ?? '—'}
                  </div>
                </div>
                {checked && <span style={{ marginLeft: 'auto', width: 18, height: 18, borderRadius: '50%', background: token.colorBgContainer, color: token.colorPrimary, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 12 }}>✓</span>}
              </div>
            )
          })}
        </div>
        <div style={{ padding: '9px 14px', borderTop: `1px solid ${line}`, display: 'flex', justifyContent: 'flex-end', gap: 8, background: headBg }}>
          <Button onClick={() => { setCandidates(null); setCandidateChecked(null) }}>否</Button>
          <Button
            type="primary"
            onClick={() => {
              const pickedIdentity = (candidates ?? []).find((identity) => identity.patUid === candidateChecked)
              if (pickedIdentity) {
                fillFromIdentity(pickedIdentity)
                messageApi.success(`已匹配档案 ${pickedIdentity.patId ?? ''}`)
              }
              setCandidates(null)
              setCandidateChecked(null)
            }}
          >
            是
          </Button>
        </div>
      </Modal>

      {/* the 添加项目 dialog (the dgrid checkbox table + the dsearch) */}
      <Modal title="添加检查项目" open={itemDialogOpen} onCancel={() => setItemDialogOpen(false)} footer={null} width={520}>
        <div style={{ padding: '12px 14px' }}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 9 }}>
            <Input aria-label="项目搜索" style={{ height: 28 }} placeholder="项目名 / 对照编码" value={itemSearch} onChange={(event) => setItemSearch(event.target.value)} />
            <Button style={{ height: 28 }}>查询</Button>
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead>
              <tr>
                <th style={{ ...thStyle, width: 34 }}></th>
                <th style={thStyle}>检查项目</th>
                <th style={{ ...thStyle, width: 90 }}>检查部位</th>
                <th style={{ ...thStyle, width: 70, textAlign: 'right' }}>单价(元)</th>
              </tr>
            </thead>
            <tbody>
              {dialogItems.map((item) => {
                const used = isPicked(item.id)
                return (
                  <tr key={item.id}>
                    <td style={tdStyle}>
                      <span
                        role="button"
                        aria-label={`选择 ${item.exam}`}
                        style={{
                          width: 14, height: 14, borderRadius: 3, border: `1px solid ${used ? token.colorPrimary : token.colorBorderSecondary}`,
                          display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: token.colorBgContainer,
                          fontSize: 10, background: used ? token.colorPrimary : token.colorBgContainer, cursor: 'pointer',
                        }}
                        onClick={() => togglePick(item)}
                      >
                        {used ? '✓' : ''}
                      </span>
                    </td>
                    <td style={{ ...tdStyle, color: used ? token.colorPrimary : undefined }}>{item.exam}</td>
                    <td style={tdStyle}>{item.bodyName ?? '—'}</td>
                    <td style={{ ...tdStyle, textAlign: 'right', fontFamily: 'monospace' }}>{item.price != null ? item.price.toFixed(2) : '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <div style={{ padding: '9px 14px', borderTop: `1px solid ${line}`, display: 'flex', justifyContent: 'flex-end', gap: 8, background: headBg }}>
          <Button onClick={() => setItemDialogOpen(false)}>取消</Button>
          <Button type="primary" onClick={() => setItemDialogOpen(false)}>确认添加</Button>
        </div>
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

      {/* the save-search modal */}
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

      {/* the saved-search list modal */}
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
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '6px 8px', borderRadius: 6, fontSize: 12.5, borderBottom: `1px solid ${token.colorFillSecondary}`,
            }}
          >
            <span
              role="button"
              tabIndex={0}
              style={{ cursor: 'pointer', flex: 1 }}
              onClick={() => applySavedSearch(filter)}
              onKeyDown={(event) => event.key === 'Enter' && applySavedSearch(filter)}
            >
              {filter.name}
              {filter.isDefault && <Tag color="orange" style={{ marginLeft: 6 }}>默认</Tag>}
            </span>
            <span style={{ display: 'inline-flex', gap: 4 }}>
              {!filter.isDefault && (
                <Button size="small" type="text" style={{ fontSize: 11, color: token.colorTextTertiary }} onClick={() => void markDefaultSearch(filter)}>
                  设默认
                </Button>
              )}
              <Button size="small" type="text" danger style={{ fontSize: 11 }} onClick={() => void removeSavedSearch(filter)}>
                删除
              </Button>
            </span>
          </div>
        ))}
        <div style={{ borderTop: `1px solid ${line}`, marginTop: 4, paddingTop: 6, color: token.colorPrimary }}>
          <span role="button" tabIndex={0} style={{ cursor: 'pointer' }} onClick={() => { setSearchListOpen(false); setSaveDialogOpen(true) }}>
            ＋ 保存当前条件
          </span>
        </div>
      </Modal>
    </ConsolePage>
  )
}

/** The prototype's `field` row wrapped in the RESPONSIVE grid cell (xl 三列 /
 *  md 两列 / xs 单列 - the prototype's own 1180px media collapse, antd-native):
 *  the 72px right label + the control flexing. `full` spans every breakpoint. */
function Field({ label, required, full, children }: {
  label: string
  required?: boolean
  full?: boolean
  children: React.ReactNode
}) {
  const { token } = theme.useToken()
  return (
    <Col xs={24} md={full ? 24 : 12} xl={full ? 24 : 8}>
      <div style={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>
        <span style={{ flex: '0 0 72px', width: 72, color: token.colorTextSecondary, textAlign: 'right', paddingRight: 8, whiteSpace: 'nowrap' }}>
          {label}
          {required && <i style={{ color: token.colorError, fontStyle: 'normal' }}> *</i>}
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
      </div>
    </Col>
  )
}

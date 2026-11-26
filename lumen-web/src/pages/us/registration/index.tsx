import {
  AutoComplete,
  Button,
  Checkbox,
  Col,
  DatePicker,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Modal,
  Popover,
  Radio,
  Row,
  Select,
  Switch,
  Tag,
  Tooltip,
  message,
  theme,
} from 'antd'
import { CalendarOutlined, CheckOutlined, DeleteOutlined, DoubleLeftOutlined, DoubleRightOutlined, DownOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import { Fragment, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  cancelOrder,
  createOrder,
  listSlotOptions,
  pageOrders,
  resolvePatients,
  statusCounts,
} from '@/api/us-orders'
import type {
  SlotOptionView,
  SlotOptionsView,
  UsOrderCreateBody,
  UsOrderPageQuery,
  UsOrderStatus,
  UsOrderView,
  UsPatientIdentity,
} from '@/api/us-orders'
import { pageExamItems } from '@/api/exam-item'
import type { DictItem, ExamItem } from '@/api/types'
import { ApiError } from '@/api/client'
import { usePerm } from '@/utils/perm'
import {
  createFilter,
  deleteFilter,
  listFilters,
  setDefaultFilter,
} from '@/api/search-filters'
import type { SearchFilterView } from '@/api/search-filters'
import { ConsoleAction, ConsolePage, ConsolePagination } from '@/pages/system/console-shell'
import {
  dictItemsById,
  GENDER_DICT_ID,
  GENDER_FEMALE_CODE,
  ID_TYPE_DICT_ID,
  PATIENT_SOURCE_DICT_ID,
  usOrderStatusFallbackLabels,
  usOrderStatusLabels,
  type UsOrderStatusLabel,
  PREGNANCY_STATUS_DICT_ID,
  resolveDictLabel,
} from '@/utils/dict-items'

/**
 * US 工作站两态工作台 (us-registration-redesign; 拍板 01-B/02-B; the UI AUTHORITY is
 * docs/prototypes/us-registration-redesign.html - this render mirrors its 壹 section
 * composition: the 392px rail card carries BOTH states (compact = the datenav + the
 * chips + the per-order card flow + the rail-foot; wide = the rail flexes full-width,
 * the compact table yields to the xtable and the rail-xhead query rows, the datenav
 * hides), the edge round button on the card's right edge toggles, and the bench card
 * carries the resolve bar (键型 seg + 查询 + matched chip) + the three fgrid sections
 * (vertical section spines, label 88px right, 3 cols; the 急诊 switch lives in the
 * 检查类型 field - user ruling 2026-11-25) + the bench-foot (重置/提交登记).
 * Against the REAL `/api/v1/us/**` backend (no mock layer - ruling 2026-11-05).
 */
/** The rail-head chip row = the SIX machine statuses (registration-order-status-dict,
 * user ruling 2026-11-26): labels AND colors resolve from dict catalog 1031 through
 * the session resolver - the hard-coded STATUS_LABELS map is retired with the
 * removed BOOKED/BOOKING_CANCELLED vocabulary (the chips serve BOTH states' filter). */
const RAIL_HEAD_STATUSES: UsOrderStatus[] = ['REGISTERED', 'IN_EXAM', 'IN_DIAGNOSIS', 'COMPLETED', 'PRINTED', 'CANCELLED']
// us-prereg-as-registered: the desk default watches the live pre-exam state
const DEFAULT_STATUS_FILTER: UsOrderStatus[] = ['REGISTERED']
// the machine's cancelable set (the six-state edges: CANCELLED fires from the three
// live states; COMPLETED/PRINTED/CANCELLED rows render stateless)
const LIVE_STATES: UsOrderStatus[] = ['REGISTERED', 'IN_EXAM', 'IN_DIAGNOSIS']
const PREGNANCY_DISABLE_VALUES = ['未孕', '未知']
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

/** The exact elapsed years/months/days between two dates (us-registration-birthdate-web
 *  design D2, the EIS CheckInfo conversion semantics): full years first, then months,
 *  then days. Exported for the fixed-oracle unit tests (the page-level tests pin the
 *  wiring; these pin the conversion math). */
export const diffYmd = (from: Dayjs, to: Dayjs): [number, number, number] => {
  let years = to.diff(from, 'year')
  let cursor = from.add(years, 'year')
  if (cursor.isAfter(to)) { years -= 1; cursor = from.add(years, 'year') }
  let months = to.diff(cursor, 'month')
  let cursor2 = cursor.add(months, 'month')
  if (cursor2.isAfter(to)) { months -= 1; cursor2 = cursor.add(months, 'month') }
  // the Feb-29 anniversary asymmetry: dayjs's clamped year-add can leave exactly 12
  // months on the remainder (2000-02-29 on 2028-02-28) - relabel it into the years
  // slot so the age string stays well-formed (months never leave [0, 11])
  if (months >= 12) { years += 1; months = 0 }
  return [years, months, to.diff(cursor2, 'day')]
}

/** Birth date -> the age form fields; zero-leading 岁/月 parts render empty.
 *  null leaves the fields untouched (the matched-identity backfill no-ops when
 *  the archive carries no birth date). */
const ageFieldsFromBirth = (date: Dayjs | null): Partial<RegistrationFormValues> => {
  if (!date) return {}
  const [years, months, days] = diffYmd(date, dayjs())
  return {
    ageYears: years > 0 ? String(years) : '',
    ageMonths: months > 0 ? String(months) : '',
    ageDays: String(days),
  }
}

interface RegistrationFormValues {
  name?: string | null
  gender?: number | null
  birthDate?: Dayjs | null
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
  note?: string | null
  applyDept?: string | null
  applyDoctor?: string | null
  applyTime?: string | null
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

/** The status pill colors from the DB-configured hue (the dict-1031 tag) through the
 * design-token map (registration-order-status-dict design D3 - never ad-hoc hex);
 * an unknown/absent tag degrades to the neutral fills. */
function hueChipColors(hue: string | null | undefined, token: {
  colorPrimaryBg: string; colorPrimary: string; colorSuccessBg: string; colorSuccess: string;
  colorWarningBg: string; colorWarning: string; colorErrorBg: string; colorError: string;
  colorFillSecondary: string; colorTextTertiary: string;
}) {
  if (hue === 'blue') return { background: token.colorPrimaryBg, color: token.colorPrimary }
  if (hue === 'green') return { background: token.colorSuccessBg, color: token.colorSuccess }
  if (hue === 'amber') return { background: token.colorWarningBg, color: token.colorWarning }
  if (hue === 'red') return { background: token.colorErrorBg, color: token.colorError }
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
  const [datePopOpen, setDatePopOpen] = useState(false)
  const [navMonth, setNavMonth] = useState(dayjs().startOf('month'))
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [cancelTarget, setCancelTarget] = useState<UsOrderView | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [lookupField, setLookupField] = useState<'idNo' | 'cardNo' | 'phone'>('cardNo')
  const [lookupValue, setLookupValue] = useState('')
  const [matchedPatId, setMatchedPatId] = useState<string | null>(null)
  const [candidates, setCandidates] = useState<UsPatientIdentity[] | null>(null)
  const [candidateChecked, setCandidateChecked] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [items, setItems] = useState<ExamItem[]>([])
  const [genderItems, setGenderItems] = useState<DictItem[]>([])
  const [idTypeItems, setIdTypeItems] = useState<DictItem[]>([])
  const [patientSourceItems, setPatientSourceItems] = useState<DictItem[]>([])
  const [pregnancyItems, setPregnancyItems] = useState<DictItem[]>([])
  // The status labels + colors (dict-1031 through the session resolver): the state
  // SEEDS with the bridge fallback so the meter renders before the wire lands and
  // survives a dict failure (the coordination doc's display-vocabulary deviation).
  const [statusLabels, setStatusLabels] = useState<UsOrderStatusLabel[]>(usOrderStatusFallbackLabels())
  const [slotOptions, setSlotOptions] = useState<SlotOptionsView | null>(null)
  const [slotOptionsDate, setSlotOptionsDate] = useState<string | null>(null)
  // The booking pair + the slot dialog states (us-booking-slot-picker 2026-11-24
  // redesign): the dialog opens on TOMORROW (D+1 - today = walk-in in place).
  const [booking, setBooking] = useState<{ date: Dayjs; slot: string; end: string } | null>(null)
  const [slotDlgOpen, setSlotDlgOpen] = useState(false)
  const [dlgDate, setDlgDate] = useState<Dayjs>(() => dayjs().add(1, 'day'))
  const [dlgSlot, setDlgSlot] = useState<string | null>(null)
  const [calMonth, setCalMonth] = useState<Dayjs>(() => dayjs().add(1, 'day').startOf('month'))
  // Day-level remaining badges for the ALWAYS-VISIBLE month calendar, aggregated
  // CLIENT-SIDE over the per-date read (user ruling 2026-11-24 mid-apply; a
  // dedicated day-level range endpoint stays a future optimization).
  const [dayBadges, setDayBadges] = useState<Record<string, number>>({})
  const [picked, setPicked] = useState<ExamItem[]>([])
  const [itemDialogOpen, setItemDialogOpen] = useState(false)
  const [itemSearch, setItemSearch] = useState('')
  // The picker dialog's 检查部位 condition (2026-11-24): a client-side slice of
  // the loaded catalog - distinct body names, no server round trip.
  const [dialogBody, setDialogBody] = useState<string | null>(null)
  // The 急诊 flag lives on the resolve bar (outside the Form, user move
  // 2026-11-25) - React state, not a form field; submit + reset carry it.
  const [emg, setEmg] = useState(false)
  // The header seek (方案 C 2026-11-24): the section-level typeahead state; plus
  // the busy flag of the picked-table refresh glyph (it re-runs the catalog load).
  const [seekKw, setSeekKw] = useState('')
  const [itemsBusy, setItemsBusy] = useState(false)
  const [form] = Form.useForm<RegistrationFormValues>()
  const gender = Form.useWatch('gender', form)
  const idType = Form.useWatch('idType', form)
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
  // the rail quick-search (us rail-search, user ruling 2026-11-26): ONE box OR-
  // matching name/accession/patId/cardNo server-side; debounced so typing does not
  // fire a wire call per keystroke
  const [railSearch, setRailSearch] = useState('')
  const [railSearchDebounced, setRailSearchDebounced] = useState('')
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
          keyword: railSearchDebounced.trim() || undefined,
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
    [filters, activeDate, dateRange, railSearchDebounced, messageApi],
  )

  const loadCounts = useCallback(async () => {
    try {
      setCounts(await statusCounts({
        ...dateRange(activeDate),
        keyword: railSearchDebounced.trim() || undefined,
      }))
    } catch {
      setCounts(null)
    }
  }, [activeDate, dateRange, railSearchDebounced])

  useEffect(() => {
    void load({ pageNum, pageSize })
    void loadCounts()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageNum, pageSize, filters, activeDate, railSearchDebounced])

  useEffect(() => {
    if (!autoRefresh) return
    const timer = setInterval(() => {
      void load({ pageNum, pageSize })
      void loadCounts()
    }, 30_000)
    return () => clearInterval(timer)
  }, [autoRefresh, load, loadCounts, pageNum, pageSize])

  useEffect(() => {
    const timer = setTimeout(() => setRailSearchDebounced(railSearch), 400)
    return () => clearTimeout(timer)
  }, [railSearch])

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
    // the archive's birth date backfills only when parseable and not in the future
    // (disabledDate governs the panel, not programmatic values) - anything else
    // no-ops, so a bad archive row can never poison the create body (review 2026-11-25)
    const parsed = identity.birthDate ? dayjs(identity.birthDate) : null
    const birth = parsed && parsed.isValid() && !parsed.isAfter(dayjs(), 'day') ? parsed : null
    form.setFieldsValue({
      idType: identity.idType ?? null,
      idNo: identity.idNo ?? null,
      cardNo: identity.cardNo ?? null,
      name: identity.name ?? null,
      gender: identity.gender ?? null,
      phone: identity.phone ?? null,
      birthDate: birth,
      ...ageFieldsFromBirth(birth),
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

  // The exam-item catalog load (方案 C 2026-11-24): extracted so the picked
  // table's ops-header refresh glyph re-runs it (the family onRefresh chrome);
  // a failed fetch degrades to an empty catalog, never blocking the workbench.
  const refreshItems = useCallback(async () => {
    setItemsBusy(true)
    try {
      const page = await pageExamItems({ pageNum: 1, pageSize: 200, modality: 'US' } as never)
      setItems(page.records)
    } catch {
      setItems([])
    } finally {
      setItemsBusy(false)
    }
  }, [])
  useEffect(() => {
    void refreshItems()
  }, [refreshItems])

  // The dict-driven vocabularies (性别 1004 / 证件类型 1006, ID-ANCHORED per the
  // dict-lookup-by-id ruling 2026-11-19; session-cached via dictItemsById). A
  // failed fetch degrades to an empty option set + one error message, never
  // blocking the workbench (the users-console precedent).
  // The status vocabulary (dict 1031, id-anchored): labels + DB-configured colors
  // through the session resolver; a failed fetch keeps the bridge fallback meter +
  // one error message (never blocks the desk).
  useEffect(() => {
    let alive = true
    void usOrderStatusLabels()
      .then((labels) => {
        if (alive) setStatusLabels(labels)
      })
      .catch((error: unknown) => {
        if (!alive) return
        messageApi.error(error instanceof ApiError ? error.msg : '状态字典加载失败')
      })
    return () => {
      alive = false
    }
  }, [messageApi])

  useEffect(() => {
    let alive = true
    void dictItemsById(GENDER_DICT_ID)
      .then((dictItems) => {
        if (alive) setGenderItems(dictItems)
      })
      .catch((error: unknown) => {
        if (alive) messageApi.error(error instanceof ApiError ? error.msg : '性别字典加载失败')
      })
    void dictItemsById(ID_TYPE_DICT_ID)
      .then((dictItems) => {
        if (alive) setIdTypeItems(dictItems)
      })
      .catch((error: unknown) => {
        if (alive) messageApi.error(error instanceof ApiError ? error.msg : '证件类型字典加载失败')
      })
    void dictItemsById(PATIENT_SOURCE_DICT_ID)
      .then((dictItems) => {
        if (alive) setPatientSourceItems(dictItems)
      })
      .catch((error: unknown) => {
        if (alive) messageApi.error(error instanceof ApiError ? error.msg : '患者来源字典加载失败')
      })
    void dictItemsById(PREGNANCY_STATUS_DICT_ID)
      .then((dictItems) => {
        if (alive) setPregnancyItems(dictItems)
      })
      .catch((error: unknown) => {
        if (alive) messageApi.error(error instanceof ApiError ? error.msg : '妊娠状态字典加载失败')
      })
    return () => {
      alive = false
    }
  }, [])

  // The booking slot dialog's day grid (us-booking-slot-picker; the 2026-11-24
  // trigger+dialog redesign, UI authority docs/prototypes/us-booking-slot-dialog.html).
  // The dialog opens on TOMORROW (D+1 - today = register in place = walk-in) and the
  // same default fetch seeds the trigger's configured state. A failed fetch degrades
  // to a disabled trigger + one message (never blocks walk-ins).
  const dlgDateKey = dlgDate.format('YYYY-MM-DD')
  useEffect(() => {
    let alive = true
    void listSlotOptions(dlgDateKey, 'US')
      .then((options) => {
        if (!alive) return
        setSlotOptions(options)
        setSlotOptionsDate(dlgDateKey)
      })
      .catch((error: unknown) => {
        if (!alive) return
        setSlotOptions(null)
        setSlotOptionsDate(null)
        messageApi.error(error instanceof ApiError ? error.msg : '预约时段加载失败')
      })
    return () => {
      alive = false
    }
  }, [dlgDateKey])
  const slotConfigured = slotOptions?.configured === true
  // The dialog grid binds to the FETCHED date (a new day never shows stale data).
  const dlgSlots = slotOptions && slotOptionsDate === dlgDateKey ? slotOptions.slots : null
  const dlgGroups = dlgSlots
    ? [
        { label: '上午', info: false, list: dlgSlots.filter((slot) => slot.start < '12:00') },
        { label: '下午', info: true, list: dlgSlots.filter((slot) => slot.start >= '12:00') },
      ].filter((group) => group.list.length > 0)
    : null
  const tomorrow = dayjs().add(1, 'day')

  const openSlotDialog = () => {
    const base = booking?.date ?? tomorrow
    setDlgDate(base)
    setDlgSlot(booking?.slot ?? null)
    setCalMonth(base.startOf('month'))
    setSlotDlgOpen(true)
  }

  // The badge prefetch: the displayed month's future days fan out over the
  // per-date read in parallel; a rejected/unconfigured day simply carries no
  // badge (the selected day's own fetch still surfaces errors).
  const calMonthKey = calMonth.format('YYYY-MM')
  useEffect(() => {
    if (!slotDlgOpen) return
    let alive = true
    const start = dayjs().add(1, 'day')
    const from = start.isAfter(calMonth.startOf('month'), 'day') ? start : calMonth.startOf('month')
    const keys: string[] = []
    for (let day = from; !day.isAfter(calMonth.endOf('month'), 'day'); day = day.add(1, 'day')) {
      keys.push(day.format('YYYY-MM-DD'))
    }
    void Promise.all(
      keys.map((key) =>
        listSlotOptions(key, 'US')
          .then((options) => [key, options] as const)
          .catch(() => [key, null] as const),
      ),
    ).then((results) => {
      if (!alive) return
      setDayBadges((prev) => {
        const next = { ...prev }
        for (const [key, options] of results) {
          if (options?.configured) {
            const totals = options.slots.reduce(
              (acc, slot) => ({ cap: acc.cap + slot.capacity, booked: acc.booked + slot.booked }),
              { cap: 0, booked: 0 },
            )
            next[key] = totals.cap - totals.booked
          } else {
            delete next[key]
          }
        }
        return next
      })
    })
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slotDlgOpen, calMonthKey])

  const confirmSlotDialog = () => {
    if (!dlgSlot) return
    const slot = (slotOptions?.slots ?? []).find((item) => item.start === dlgSlot)
    setBooking({ date: dlgDate, slot: dlgSlot, end: slot?.end ?? dlgSlot })
    setSlotDlgOpen(false)
  }

  // One slot card: range + the segmented capacity meter (1 cell = 1 号; capacity
  // > 10 degrades to a progress bar) + the remaining count. Full slots render
  // grayed/disabled (满员标灰), the last-remaining slot gets the warning tint.
  const renderSlotCard = (slot: SlotOptionView) => {
    const full = slot.booked >= slot.capacity
    const left = slot.capacity - slot.booked
    const selected = dlgSlot === slot.start
    const meter =
      slot.capacity > 10 ? (
        <div style={{ height: 8, borderRadius: 4, background: token.colorFillSecondary, overflow: 'hidden' }}>
          <div style={{ width: `${(slot.booked / slot.capacity) * 100}%`, height: '100%', background: full ? token.colorTextQuaternary : token.colorPrimaryBorder }} />
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 3, maxWidth: 120 }}>
          {Array.from({ length: slot.capacity }, (_, i) => (
            <span
              key={i}
              style={{
                flex: '1 1 0', minWidth: 0, height: 8, borderRadius: 2, border: `1px solid ${token.colorBorder}`,
                background: i < slot.booked ? (full ? token.colorFillSecondary : token.colorPrimaryBorder) : token.colorBgContainer,
              }}
            />
          ))}
        </div>
      )
    return (
      <div
        key={slot.start}
        role="button"
        tabIndex={0}
        aria-disabled={full || undefined}
        aria-label={full ? `预约时段 ${slot.start}–${slot.end}，已满` : `预约时段 ${slot.start}–${slot.end}，剩余 ${left} 号`}
        onClick={() => {
          if (!full) setDlgSlot((prev) => (prev === slot.start ? null : slot.start))
        }}
        onKeyDown={(event) => {
          if (!full && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault()
            setDlgSlot((prev) => (prev === slot.start ? null : slot.start))
          }
        }}
        style={{
          position: 'relative', borderRadius: 8, padding: '10px 12px 9px', userSelect: 'none', minWidth: 0,
          border: `1px solid ${selected ? token.colorPrimary : token.colorBorder}`,
          background: selected ? token.colorPrimaryBg : full ? token.colorFillQuaternary : token.colorBgContainer,
          boxShadow: selected ? `inset 0 0 0 1px ${token.colorPrimary}` : undefined,
          cursor: full ? 'not-allowed' : 'pointer',
        }}
      >
        {selected && (
          <span style={{ position: 'absolute', top: -7, right: -6, width: 17, height: 17, borderRadius: '50%', background: token.colorPrimary, color: token.colorBgContainer, fontSize: 10, lineHeight: '17px', textAlign: 'center' }}>✓</span>
        )}
        <div style={{ fontSize: 16, fontWeight: 600, fontFamily: 'monospace', color: selected ? token.colorPrimary : token.colorText, whiteSpace: 'nowrap' }}>
          {slot.start}
          <span style={{ fontWeight: 400, fontSize: 13, color: token.colorTextTertiary }}>–{slot.end}</span>
        </div>
        <div style={{ marginTop: 6 }}>{meter}</div>
        <div style={{ marginTop: 5, fontSize: 13.5, fontFamily: 'monospace', fontWeight: !full && left === 1 ? 700 : 400, color: full ? token.colorTextTertiary : left === 1 ? token.colorWarning : token.colorTextSecondary }}>
          {full ? '已满' : `余 ${left} / ${slot.capacity}`}
        </div>
      </div>
    )
  }

  // The 患者来源/证件类型 selects default to the FIRST dict option (user ruling
  // 2026-11-24); the workbench 重置 reapplies the same defaults.
  useEffect(() => {
    const firstSource = patientSourceItems.find((item) => item.value)?.value ?? null
    if (firstSource != null && form.getFieldValue('patientSource') == null) {
      form.setFieldsValue({ patientSource: firstSource })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [patientSourceItems])
  useEffect(() => {
    const firstIdType = idTypeItems.find((item) => item.value)?.value ?? null
    if (firstIdType != null && form.getFieldValue('idType') == null) {
      form.setFieldsValue({ idType: firstIdType })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idTypeItems])

  const commonItems = items.filter((item) => item.isCommon)
  const dialogItems = items.filter((item) => {
    const keyword = itemSearch.trim()
    const kwOk = !keyword || (item.exam ?? '').includes(keyword) || (item.code ?? '').includes(keyword)
    const bodyOk = !dialogBody || (item.bodyName ?? '') === dialogBody
    return kwOk && bodyOk
  })
  const bodyOptions = [...new Set(items.map((item) => item.bodyName).filter((b): b is string => !!b))]
  const isPicked = (id: number) => picked.some((item) => item.id === id)
  const togglePick = (item: ExamItem) => {
    setPicked((prev) =>
      isPicked(item.id) ? prev.filter((picked_) => picked_.id !== item.id) : [...prev, item],
    )
  }
  const removePick = (id: number) => setPicked((prev) => prev.filter((item) => item.id !== id))
  const pickedTotal = picked.reduce((sum, item) => sum + (item.price ?? 0), 0)

  // The header seek (方案 C 2026-11-24): fuzzy name/code match over the loaded
  // catalog, top 6; Enter/click adds directly - the dialog stays for browsing.
  // Picked options disable with the 已在单内 suffix; matched substrings bold.
  // The fixed unit suffix (the EIS hw-unit pattern, user ruling 2026-11-25):
  // the unit rides INSIDE the control - 岁/月/天 and cm/kg stay always visible.
  const unitSuffix = (t: string) => (
    <span style={{ color: token.colorTextTertiary, fontSize: 12 }}>{t}</span>
  )
  const seekKwTrim = seekKw.trim()
  const markMatch = (text: string): ReactNode => {
    if (!seekKwTrim || !(text ?? '').includes(seekKwTrim)) return text
    return text.split(seekKwTrim).map((seg, i) => (
      <Fragment key={i}>
        {i > 0 && <b>{seekKwTrim}</b>}
        {seg}
      </Fragment>
    ))
  }
  const seekOptions = seekKwTrim
    ? items
        .filter(
          (item) =>
            (item.exam ?? '').includes(seekKwTrim) ||
            (item.code ?? '').toLowerCase().includes(seekKwTrim.toLowerCase()),
        )
        .slice(0, 6)
        .map((item) => ({
          value: String(item.id),
          disabled: isPicked(item.id),
          label: (
            <span style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 14, minWidth: 300 }}>
              <span style={{ color: token.colorTextTertiary, fontFamily: 'monospace', fontSize: 12 }}>{item.code}</span>
              <span>{markMatch(item.exam)}</span>
              <span style={{ color: token.colorTextTertiary, fontSize: 12, marginLeft: 'auto' }}>{item.bodyName ?? '—'}</span>
              {isPicked(item.id) && <span style={{ color: token.colorPrimary, fontSize: 11 }}>已在单内</span>}
            </span>
          ),
        }))
    : []

  // ---------------- the submit (提交即登记, 拍板 02-B) --------------------------

  const resetWorkbench = () => {
    form.resetFields()
    setPicked([])
    setMatchedPatId(null)
    setLookupValue('')
    setBooking(null)
    setEmg(false)
    form.setFieldsValue({
      patientSource: patientSourceItems.find((item) => item.value)?.value ?? null,
      idType: idTypeItems.find((item) => item.value)?.value ?? null,
    })
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
      const bookingDate = booking ? booking.date.format('YYYY-MM-DD') : ''
      const bookingSlot = booking?.slot ?? ''
      const body: UsOrderCreateBody = {
        idType: values.idType ?? null,
        idNo: values.idNo ?? null,
        cardNo: values.cardNo ?? null,
        name: values.name?.trim() ? values.name.trim() : null,
        gender: values.gender ?? null,
        birthDate: values.birthDate ? values.birthDate.format('YYYY-MM-DD') : null,
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
        emgFlag: emg ? 1 : 0,
        ext: {
          pregnancyStatus: values.pregnancyStatus ?? null,
          gestationalWeeks: values.gestationalWeeks ?? null,
          note: values.note ?? null,
          // the HIS application origin (us-apply-info) - blank submits as null
          applyDept: (values.applyDept ?? '').trim() || null,
          applyDoctor: (values.applyDoctor ?? '').trim() || null,
          applyTime: (values.applyTime ?? '').trim() || null,
        },
      }
      const created = await createOrder(body)
      // 预约=预登记 (user ruling 2026-11-24): every create lands REGISTERED - the
      // single success message (the category rides registrationType, not the status)
      messageApi.success(`登记成功，检查号 ${created.accessionNo}`)
      resetWorkbench()
      refreshAll()
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg)
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        // the noStyle fields still mark their controls; surface the first refusal
        const first = (error as { errorFields?: Array<{ errors?: string[] }> }).errorFields?.[0]?.errors?.[0]
        messageApi.warning(first ?? '请完善必填项')
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setSaving(false)
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
  // The row timestamps format defensively through dayjs: the VO's Date fields
  // serialize per-field — @JsonFormat strings ("yyyy-MM-dd HH:mm:ss") where
  // annotated, epoch millis numbers where not — so a stray number must never
  // crash the page again (the createTime .slice crash, 2026-11-26; the backend
  // annotation now pins the string form, this bridges any older deployment).
  // Null/absent renders the em dash.
  const formatStamp = (value?: string | number | null, fmt = 'HH:mm') => {
    if (value == null || value === '') return '—'
    const d = dayjs(value)
    return d.isValid() ? d.format(fmt) : '—'
  }

  // ---------------- the shared prototype styles (via the antd tokens) ----------

  const line = token.colorBorderSecondary
  const headBg = token.colorFillQuaternary
  const railHeadStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
    padding: '10px 10px', borderBottom: `1px solid ${line}`, background: headBg,
  }
  // the prototype's .rail-chips block (us-diagnosis-workbench): chips + quick-search
  // on WHITE below the tinted datenav band, each block carrying its own divider -
  // the query area and the list never share one band
  const railChipsStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap',
    padding: '7px 10px', borderBottom: `1px solid ${line}`,
    background: token.colorBgContainer,
  }
  // The status chip = checkbox + label + count (user ruling 2026-11-26: the plain
  // pill's color-only selection was not intuitive enough - the check glyph makes it
  // explicit; the body-size text + breathing room keep 3-digit counts legible). The
  // hue still tints the selected state through the design-token map.
  // The status chip = the prototype's .schip pill (us-diagnosis-workbench rail-chips)
  // + the checkbox glyph (user ruling 2026-11-26: color-only selection was not
  // intuitive) + the body-floor 14px text with a roomy monospace count (3-digit
  // counts must breathe); the hue tints the selected state through the token map.
  const schipStyle = (active: boolean, hue: string | null) => {
    const pair = hueChipColors(hue, token)
    return {
    display: 'inline-flex', alignItems: 'center', gap: 6, height: 28, padding: '0 10px',
    borderRadius: 999, cursor: 'pointer', userSelect: 'none' as const, fontSize: 16,
    lineHeight: 1.2, whiteSpace: 'nowrap' as const,
    border: `1px solid ${active ? pair.color : token.colorBorder}`,
    background: active ? pair.background : token.colorBgContainer,
    color: active ? pair.color : token.colorTextSecondary,
    }
  }
  const chipCheckStyle = (active: boolean, hue: string | null) => {
    const pair = hueChipColors(hue, token)
    return {
    width: 14, height: 14, flex: '0 0 auto', borderRadius: 3,
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    border: `1px solid ${active ? pair.color : token.colorBorder}`,
    background: active ? pair.color : token.colorBgContainer,
    color: token.colorBgContainer, fontSize: 10, lineHeight: 1,
    }
  }
  const inputStyle: React.CSSProperties = {}
  const thStyle: React.CSSProperties = {
    textAlign: 'left', fontWeight: 500, color: token.colorTextSecondary,
    background: token.colorFillQuaternary, borderBottom: `1px solid ${line}`, padding: '6px 8px', whiteSpace: 'nowrap',
  }
  const tdStyle: React.CSSProperties = {
    borderBottom: `1px solid ${token.colorFillSecondary}`, padding: '9px 8px', verticalAlign: 'middle',
  }
  // The narrow-list text ellipsis (the card flow's name span) — the 14px
  // thStyle/tdStyle stay for the wide table + the picked table.
  const ellipsis: React.CSSProperties = { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }
  /** The value-keyed dict projection for string-token selects (value = label = the dict text). */
  const valueKeyedOptions = (dictItems: DictItem[]) =>
    dictItems
      .filter((item) => item.value)
      .map((item) => ({ value: item.value as string, label: item.value as string }))

  const statusHue = (status: UsOrderStatus) =>
    statusLabels.find((l) => l.status === status)?.hue ?? null
  const statusLabel = (status: UsOrderStatus) =>
    statusLabels.find((l) => l.status === status)?.label ?? status

  const statusChip = (row: UsOrderView) => (
    <span
      data-status-hue={statusHue(row.status)}
      style={{
        display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px',
        borderRadius: 999, whiteSpace: 'nowrap', fontWeight: 500,
        ...hueChipColors(statusHue(row.status), token),
      }}
    >
      {statusLabel(row.status)}
    </span>
  )

  const railRowActions = (row: UsOrderView) => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
      {/* us-prereg-as-registered: NO 登记 action — 预登记 rows enter 已登记 at creation
          (预约=预登记, user ruling 2026-11-24); the only row action is 取消. The verb
          rides the shared ConsoleAction chrome (the ops-column invariant: the trash
          glyph + Tooltip, never the emoji literal the other 14 pages don't use). */}
      {LIVE_STATES.includes(row.status) && hasPerm('us:order:cancel') && (
        <ConsoleAction
          label="取消登记"
          icon="remove"
          danger
          onClick={() => {
            setCancelTarget(row)
            setCancelReason('')
          }}
        />
      )}
      {!LIVE_STATES.includes(row.status) && <span style={{ color: token.colorTextTertiary }}>—</span>}
    </div>
  )

  const emgTag = <span style={{ padding: '0 6px', lineHeight: '20px', borderRadius: 3, background: token.colorWarningBg, color: token.colorWarning, fontWeight: 600 }}>急</span>
  const sourceTag = (source: string | null | undefined) =>
    source ? <span style={{ padding: '0 6px', lineHeight: '20px', borderRadius: 3, background: token.colorFillSecondary, color: token.colorTextSecondary }}>{source}</span> : null
  // the entry-category tag (us-prereg-as-registered): the SERVER-stamped business fact
  // — 预约 = PRE_REGISTERED (info tint, the special kind), 现场 = WALK_IN (neutral fill,
  // the sourceTag chrome). Never inferred from bookingTime null-ness.
  const categoryTag = (row: UsOrderView) =>
    row.registrationType === 'PRE_REGISTERED' ? (
      <span style={{ padding: '0 6px', lineHeight: '20px', borderRadius: 3, background: token.colorInfoBg, color: token.colorInfo, fontWeight: 500 }}>预约</span>
    ) : (
      <span style={{ padding: '0 6px', lineHeight: '20px', borderRadius: 3, background: token.colorFillSecondary, color: token.colorTextSecondary }}>现场</span>
    )

  // the rail chips render shared by BOTH states: the compact rail-chips block (white,
  // under the tinted datenav band) and the wide head row
  const chipsRow = (
    <span
      role="group"
      aria-label="状态筛选"
      style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', flex: mode === 'compact' ? '1 1 100%' : 1, minWidth: 0 }}
    >
      {RAIL_HEAD_STATUSES.map((status) => {
        const active = filters.status.includes(status)
        const hue = statusHue(status)
        const label = statusLabel(status)
        return (
          <span
            key={status}
            role="button"
            aria-pressed={active}
            data-status-hue={hue}
            aria-label={`${label} ${counts?.[status] ?? 0}`}
            style={{ ...schipStyle(active, hue) }}
            onClick={() => toggleStatus(status)}
          >
            <span aria-hidden style={{ ...chipCheckStyle(active, hue) }}>
              {active && <CheckOutlined />}
            </span>
            {label}
            <span style={{ fontFamily: 'monospace', fontSize: 13, paddingLeft: 1 }}>
              {counts?.[status] ?? 0}
            </span>
          </span>
        )
      })}
    </span>
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
              position: 'absolute', top: '50%', right: -17, transform: 'translateY(-50%)',
              width: 34, height: 34, minWidth: 34, zIndex: 6, fontSize: 18,
              background: token.colorBgContainer, color: token.colorPrimary,
              border: `1px solid ${token.colorPrimaryBorder}`,
              boxShadow: token.boxShadowSecondary,
            }}
            onClick={() => setMode((prev) => (prev === 'compact' ? 'wide' : 'compact'))}
          >
            {mode === 'compact' ? <DoubleRightOutlined /> : <DoubleLeftOutlined />}
          </Button>

          {/* the rail head, TWO rows (user ruling 2026-11-24, compact): row 1 =
              the datenav + the mini autorefresh (margin-left auto pins it right),
              row 2 = the grouped status chips on their own full-width line — the
              former chips-before-spacer order pushed the autorefresh onto a
              third row; the wide mode keeps chips-first + autorefresh right */}
          <div style={railHeadStyle}>
            {mode === 'compact' && (
              <span role="group" aria-label="日期导航" style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 20 }}>
                <Button size="small" aria-label="上一日" style={{ width: 24, height: 28, minWidth: 24, padding: 0, fontSize: 20 }} onClick={() => shiftDate(-1)}>‹</Button>
                <Popover
                  open={datePopOpen}
                  onOpenChange={(open) => { setDatePopOpen(open); if (open) setNavMonth(activeDate.startOf('month')) }}
                  trigger="click"
                  placement="bottomLeft"
                  content={
                    <div style={{ width: 231 }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                        <Button size="small" type="text" aria-label="跳转上一月" onClick={() => setNavMonth((prev) => prev.subtract(1, 'month'))}>‹</Button>
                        <span style={{ fontSize: 13, fontWeight: 600, letterSpacing: '0.05em' }}>{navMonth.format('YYYY 年 MM 月')}</span>
                        <Button size="small" type="text" aria-label="跳转下一月" onClick={() => setNavMonth((prev) => prev.add(1, 'month'))}>›</Button>
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 2 }}>
                        {['一', '二', '三', '四', '五', '六', '日'].map((week) => (
                          <span key={week} style={{ fontSize: 12, color: token.colorTextTertiary, textAlign: 'center', paddingBottom: 2 }}>{week}</span>
                        ))}
                        {Array.from({ length: (navMonth.day() + 6) % 7 }, (_, i) => (
                          <span key={`nav-blank-${i}`} />
                        ))}
                        {Array.from({ length: navMonth.daysInMonth() }, (_, i) => navMonth.date(i + 1)).map((day) => {
                          const today = day.isSame(dayjs(), 'day')
                          const selected = day.isSame(activeDate, 'day')
                          return (
                            <button
                              key={day.format('YYYY-MM-DD')}
                              type="button"
                              aria-label={day.format('YYYY-MM-DD')}
                              onClick={() => { setActiveDate(day); setDatePopOpen(false) }}
                              style={{
                                height: 26, fontFamily: 'monospace', fontSize: 12, borderRadius: 5, border: 'none', padding: 0,
                                cursor: 'pointer',
                                background: selected ? token.colorPrimary : 'transparent',
                                color: selected ? token.colorBgContainer : today ? token.colorPrimary : token.colorText,
                                fontWeight: selected || today ? 600 : 400,
                                boxShadow: today && !selected ? `inset 0 0 0 1px ${token.colorPrimaryBorder}` : undefined,
                              }}
                            >
                              {day.date()}
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  }
                >
                  <span role="button" aria-label="选择日期" style={{ cursor: 'pointer' }} title="选择日期">{activeDate.format('YYYY-MM-DD')}</span>
                </Popover>
                <Button size="small" aria-label="下一日" style={{ width: 24, height: 28, minWidth: 24, padding: 0, fontSize: 20 }} onClick={() => shiftDate(1)}>›</Button>
              </span>
            )}
            {mode === 'compact' && (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: token.colorTextTertiary, marginLeft: 'auto' }}>
                自动刷新
                <Switch id="us-autorefresh" aria-label="自动刷新" size="small" checked={autoRefresh} onChange={setAutoRefresh} />
              </span>
            )}
            {mode === 'wide' && chipsRow}
            {mode === 'wide' && (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: token.colorTextTertiary, marginLeft: 12 }}>
                自动刷新
                <Switch id="us-autorefresh" aria-label="自动刷新" size="small" checked={autoRefresh} onChange={setAutoRefresh} />
              </span>
            )}
          </div>

          {/* the prototype's rail-chips block (compact only): the chips + the quick
              search on WHITE with its own divider, below the tinted datenav band */}
          {mode === 'compact' && (
            <div style={railChipsStyle}>
              {chipsRow}
              <Input
                aria-label="快速查询"
                style={{ flex: '1 1 100%' }}
                placeholder="姓名 / 检查号 / 影像号"
                prefix={<SearchOutlined style={{ color: token.colorTextTertiary, fontSize: 12 }} />}
                allowClear
                value={railSearch}
                onChange={(e) => setRailSearch(e.target.value)}
              />
            </div>
          )}

          {/* the wide query rows (rail-xhead — wide mode only) */}
          {mode === 'wide' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '8px 10px', borderBottom: `1px solid ${line}`, background: headBg }}>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                  {/* us-prereg-as-registered: the filter binds create_time (the 登记/
                      建单 moment) — labeled 创建时间, never 申请时间 (that name belongs
                      to the form's ext HIS apply-time field); params unchanged */}
                  <span style={{ color: token.colorTextSecondary }}>创建时间</span>
                  <Input aria-label="创建时间起" style={{ ...inputStyle, width: "clamp(110px, 12vw, 160px)" }} value={filters.applyTimeStart} placeholder="YYYY-MM-DD HH:mm:ss" onChange={(e) => setFilters((p) => ({ ...p, applyTimeStart: e.target.value }))} />
                  <span style={{ color: token.colorTextTertiary }}>~</span>
                  <Input aria-label="创建时间止" style={{ ...inputStyle, width: "clamp(110px, 12vw, 160px)" }} value={filters.applyTimeEnd} placeholder="YYYY-MM-DD HH:mm:ss" onChange={(e) => setFilters((p) => ({ ...p, applyTimeEnd: e.target.value }))} />
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                  <span style={{ color: token.colorTextSecondary }}>患者姓名</span>
                  <Input aria-label="患者姓名条件" style={{ ...inputStyle, width: 150 }} value={filters.patientName} placeholder="请输入" onChange={(e) => setFilters((p) => ({ ...p, patientName: e.target.value }))} allowClear />
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                  <span style={{ color: token.colorTextSecondary }}>检查号</span>
                  <Input aria-label="检查号条件" style={{ ...inputStyle, width: 150, fontFamily: 'monospace' }} value={filters.accessionNo} placeholder="US…" onChange={(e) => setFilters((p) => ({ ...p, accessionNo: e.target.value }))} allowClear />
                </span>
                {extrasOpen && (
                  <>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ color: token.colorTextSecondary }}>影像号</span>
                      <Input aria-label="影像号条件" style={{ ...inputStyle, width: 150, fontFamily: 'monospace' }} value={filters.patientId} placeholder="P…" onChange={(e) => setFilters((p) => ({ ...p, patientId: e.target.value }))} allowClear />
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ color: token.colorTextSecondary }}>患者类型</span>
                      <Select aria-label="患者类型条件" style={{ width: 150 }} size="small" allowClear placeholder="全部" value={filters.patientSource || undefined}
                        options={valueKeyedOptions(patientSourceItems)}
                        onChange={(value) => setFilters((p) => ({ ...p, patientSource: value ?? '' }))} />
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ color: token.colorTextSecondary }}>检查项目</span>
                      <Input aria-label="项目名称条件" style={{ ...inputStyle, width: 150 }} value={filters.examItemName} placeholder="模糊匹配" onChange={(e) => setFilters((p) => ({ ...p, examItemName: e.target.value }))} allowClear />
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}>
                      <span style={{ color: token.colorTextSecondary }}>就诊卡号</span>
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
                  style={{ color: token.colorPrimary, cursor: 'pointer', marginLeft: 'auto', whiteSpace: 'nowrap', userSelect: 'none' }}
                  onClick={() => setExtrasOpen((prev) => !prev)}
                >
                  {extrasOpen ? '收起更多 ▴' : '更多条件 ▾'}
                </span>
              </div>
            </div>
          )}

          {/* the COMPACT list (the per-order card flow, user-approved prototype
              2026-11-26): the rail's narrow band cannot hold columns without shrinking
              fonts or scrolling sideways, so each order is ONE panel stacking the
              facts vertically — line 1 = name/source/emg + status/category + 取消;
              line 2 = the items (+ pregnancy); line 3 = 影像号·检查号 (mono);
              line 4 = the labeled 检查/登记 times (HH:mm; the datenav scopes the day —
              the wide state keeps the full datetimes). NO horizontal scroll, no
              squeezed columns; the wide state restores the xtable form (edge toggle). */}
          {mode === 'compact' && (
            <div style={{ flex: 1, overflowY: 'auto', minHeight: 0, display: 'flex', flexDirection: 'column', gap: 8, padding: '9px 10px' }}>
              {rows.map((row) => (
                <div
                  key={row.orderId}
                  className="us-ocard"
                  style={{ border: `1px solid ${line}`, borderRadius: 8, background: token.colorBgContainer, padding: '8px 11px 9px' }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 5, minWidth: 0 }}>
                      <span style={{ fontSize: 13.5, fontWeight: 600, ...ellipsis }} title={row.patientSnapshot?.name ?? '无名氏'}>
                        {row.patientSnapshot?.name ?? '无名氏'}
                      </span>
                      {sourceTag(row.patientSnapshot?.patientSource)}
                      {row.emgFlag === 1 && emgTag}
                    </span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, marginLeft: 'auto', flex: 'none' }}>
                      {statusChip(row)}
                      {categoryTag(row)}
                      {railRowActions(row)}
                    </span>
                  </div>
                  <div style={{ fontSize: 12.5, color: token.colorTextSecondary, marginTop: 4 }}>
                    {rowItemsText(row) || '—'}
                    {rowPregnancy(row) && <span style={{ color: token.colorInfo }}> · {rowPregnancy(row)}</span>}
                  </div>
                  <div style={{ fontSize: 12, color: token.colorTextTertiary, marginTop: 4, fontFamily: 'monospace' }}>
                    {row.patientSnapshot?.patId ? `${row.patientSnapshot.patId} · ` : ''}{row.accessionNo}
                  </div>
                  <div style={{ fontSize: 12, color: token.colorTextSecondary, marginTop: 4, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                    <span>
                      <span style={{ color: token.colorTextTertiary, marginRight: 4 }}>检查</span>
                      <span style={{ fontFamily: 'monospace' }}>{formatStamp(row.bookingTime)}</span>
                    </span>
                    <span>
                      <span style={{ color: token.colorTextTertiary, marginRight: 4 }}>登记</span>
                      <span style={{ fontFamily: 'monospace' }}>{formatStamp(row.createTime)}</span>
                    </span>
                  </div>
                </div>
              ))}
              {rows.length === 0 && !loading && (
                <div style={{ padding: 16, color: token.colorTextTertiary, textAlign: 'center' }}>当日暂无登记单</div>
              )}
            </div>
          )}

          {/* the WIDE xtable (the full columns + the 列设置 gear in the ops header) */}
          {mode === 'wide' && (
            <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
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
                          <span style={{ fontWeight: 600 }}>{row.patientSnapshot?.name ?? '无名氏'}</span>
                          {sourceTag(row.patientSnapshot?.patientSource)}
                          {row.emgFlag === 1 && emgTag}
                        </div>
                        <div style={{ color: token.colorTextTertiary, fontFamily: 'monospace', marginTop: 2 }}>
                          {row.patientSnapshot?.patId ?? ''}{row.patientSnapshot?.gender != null ? ` · ${resolveDictLabel(genderItems, row.patientSnapshot.gender)}` : ''}{row.patientSnapshot?.age ? ` · ${row.patientSnapshot.age}` : ''}
                        </div>
                      </td>
                      {!hiddenColumns.items && <td style={{ ...tdStyle, color: token.colorTextSecondary }}>
                        {rowItemsText(row)}
                      </td>}
                      {!hiddenColumns.status && <td style={tdStyle}><span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>{statusChip(row)}{categoryTag(row)}</span></td>}
                      {!hiddenColumns.emg && <td style={tdStyle}>{row.emgFlag === 1 ? emgTag : null}</td>}
                      {!hiddenColumns.pregnancy && <td style={{ ...tdStyle, color: token.colorTextSecondary }}>{preg ? <span><b>{row.pregnancyStatus}</b>{row.gestationalWeeks ? ` · ${row.gestationalWeeks}周` : ''}</span> : '—'}</td>}
                      {!hiddenColumns.accessionNo && <td style={{ ...tdStyle, fontFamily: 'monospace', color: token.colorTextSecondary }}>{row.accessionNo}</td>}
                      {!hiddenColumns.createTime && <td style={{ ...tdStyle, color: token.colorTextSecondary }}>{formatStamp(row.createTime, 'YYYY-MM-DD HH:mm:ss')}</td>}
                      {!hiddenColumns.note && <td style={{ ...tdStyle, color: token.colorTextTertiary, maxWidth: 130, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.note ?? '—'}</td>}
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

          {/* the rail foot: the shared ConsolePagination in BOTH states (共 N 条 + antd
              pager, unified with the setting/system pages) - renders nothing at 0 单 */}
          <ConsolePagination
            total={total}
            current={pageNum}
            pageSize={pageSize}
            showSizeChanger={false}
            onChange={(next) => setPageNum(next)}
          />
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
                  { value: 'cardNo', label: '就诊卡号' },
                  { value: 'idNo', label: '证件号' },
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
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: 24, padding: '0 10px', borderRadius: 999, background: token.colorSuccessBg, color: token.colorSuccess }}>
                  ✓ 已匹配档案 <span style={{ fontFamily: 'monospace' }}>{matchedPatId}</span>，已回填
                </span>
              )}
              {/* the 急诊 flag, pinned right on the resolve bar (user move 2026-11-25):
                  the "what kind of order" toggle rides the operator's entry toolbar.
                  Alarm-red styling (user ruling 2026-11-25): the label runs the error
                  color at body size + weight, the checked switch turns alarm red via
                  the --lumen-switch-emg-bg pipeline (red = clinical-alarm semantics). */}
              <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 7, color: token.colorError, fontSize: 16, fontWeight: 700 }}>
                急诊
                <Switch id="us-emg" className="lumen-switch-emg" aria-label="急诊" size="small" checked={emg} onChange={(v) => setEmg(v)} />
              </span>
            </div>

            <Form
              form={form}
              disabled={saving}
              style={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}
              onValuesChange={(changed) => {
                // the EIS year-pin (us-registration-birthdate-web design D2): an edited 岁
                // re-pins the picker's year only (month/day preserved). NO fabricated
                // dates: an empty picker + age edit is a no-op (invented month/day would
                // poison the archive); garbage/empty 岁 no-ops. setFieldsValue (backfill)
                // never fires this - no loop.
                if (!('ageYears' in changed)) return
                const years = Number(changed.ageYears)
                if (!changed.ageYears || !Number.isFinite(years) || years <= 0) return
                const base = form.getFieldValue('birthDate') as Dayjs | null
                if (!base) return
                form.setFieldsValue({ birthDate: base.year(dayjs().year() - years) })
              }}
            >
              <div style={{ padding: '10px 16px 4px', display: 'flex', flexDirection: 'column', flex: 1 }}>
                {/* 1 患者信息 - the vertical section spine (user ruling 2026-11-25):
                    the 4-char title rides a tinted left rail (writing-mode vertical),
                    the former header row's height budget goes to the fields */}
                <div style={{ display: 'flex', alignItems: 'stretch', gap: 10, marginBottom: 10 }}>
                  <div style={{ flex: 'none', width: 24, alignSelf: 'flex-start', borderRadius: 6, background: token.colorPrimaryBg, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 7, padding: '7px 0 9px' }}>
                    <span style={{ width: 16, height: 16, borderRadius: '50%', background: token.colorPrimary, color: token.colorBgContainer, fontSize: 11, fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>1</span>
                    <span style={{ writingMode: 'vertical-rl', letterSpacing: 4, marginBottom: -4, fontSize: 13, color: token.colorPrimary, lineHeight: '14px' }}>患者信息</span>
                  </div>
                  <Row gutter={[14, 9]} style={{ flex: 1, minWidth: 0, margin: 0 }}>
                  <Field label="就诊卡号" required>
                    <Form.Item name="cardNo" noStyle rules={[{ required: true, whitespace: true, message: '请填写就诊卡号' }]}>
                      <Input aria-label="卡号" style={inputStyle} />
                    </Form.Item>
                  </Field>
                  <Field label="姓名" required>
                    <Form.Item name="name" noStyle rules={[{ required: true, whitespace: true, message: '请填写姓名' }]}>
                      <Input aria-label="姓名" style={inputStyle} />
                    </Form.Item>
                  </Field>
                  {/* the gender/source pair shares one grid cell (user ruling
                      2026-11-26, the 住院/床号 pattern): two plain bordered selects
                      side by side, the placeholders identify each - no special
                      chrome, the cell just saves one column. 患者来源 returns to
                      患者信息 (its 请检信息 posting existed to accompany the
                      retired 类型 chip). */}
                  <Col xs={24} md={12} xl={8}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                      <span style={{ flex: 'none', color: token.colorTextSecondary, textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <i style={{ color: token.colorError, fontStyle: 'normal' }}>* </i>性别
                      </span>
                      <Form.Item name="gender" noStyle rules={[{ required: true, message: '请选择性别' }]}>
                        <Select aria-label="性别" style={{ flex: 1, minWidth: 0 }} options={genderItems.filter((item) => item.code != null).map((item) => ({ value: item.code as number, label: item.value ?? '' }))} />
                      </Form.Item>
                      <span style={{ flex: 'none', color: token.colorTextSecondary, textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <i style={{ color: token.colorError, fontStyle: 'normal' }}>* </i>来源
                      </span>
                      <Form.Item name="patientSource" noStyle rules={[{ required: true, message: '请选择患者来源' }]}>
                        <Select aria-label="患者来源" style={{ flex: 1, minWidth: 0 }} allowClear options={valueKeyedOptions(patientSourceItems)} />
                      </Form.Item>
                    </div>
                  </Col>
                  <Field label="年龄" required>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <Form.Item
                        name="ageYears"
                        noStyle
                        rules={[{
                          validator: (_, value) =>
                            value || form.getFieldValue('ageMonths') || form.getFieldValue('ageDays')
                              ? Promise.resolve()
                              : Promise.reject(new Error('请填写年龄')),
                        }]}
                      >
                        <Input aria-label="年龄-岁" style={{ ...inputStyle, flex: '1 1 0', minWidth: 0 }} suffix={unitSuffix('岁')} />
                      </Form.Item>
                      <Form.Item name="ageMonths" noStyle>
                        <Input aria-label="年龄-月" style={{ ...inputStyle, flex: '1 1 0', minWidth: 0 }} suffix={unitSuffix('月')} />
                      </Form.Item>
                      <Form.Item name="ageDays" noStyle>
                        <Input aria-label="年龄-天" style={{ ...inputStyle, flex: '1 1 0', minWidth: 0 }} suffix={unitSuffix('天')} />
                      </Form.Item>
                    </div>
                  </Field>
                  <Field label="出生日期">
                    {/* us-registration-birthdate-web: the EIS-parity conversion pair -
                        a pick recomputes 岁/月/天 exactly; the reverse (岁 -> year-pin)
                        rides the Form's onValuesChange. Future dates are refused. */}
                    <Form.Item name="birthDate" noStyle>
                      <DatePicker
                        aria-label="出生日期"
                        style={{ width: '100%' }}
                        disabledDate={(current) => current != null && current.isAfter(dayjs(), 'day')}
                        onChange={(date) => {
                          // the age fields are the picker's shadow: a pick derives them,
                          // a clear resets them (no stale derived age may ship without
                          // its birth date - review 2026-11-25)
                          form.setFieldsValue(date
                            ? ageFieldsFromBirth(date)
                            : { ageYears: '', ageMonths: '', ageDays: '' })
                        }}
                        allowClear
                      />
                    </Form.Item>
                  </Field>
                  {/* The 证件 compound control (user ruling 2026-11-24): ONE bordered box -
                      the type Dropdown segment inside-left + the divider + the borderless
                      number input. idType registers via the hidden Form.Item so the
                      default fill / switch / submit / reset all stay live. No field
                      label - the control self-describes. */}
                  <Col xs={24} md={12} xl={8}>
                    <div
                      style={{
                        display: 'flex', alignItems: 'center', minWidth: 0,
                        height: token.controlHeight, border: `1px solid ${token.colorBorder}`,
                        borderRadius: token.borderRadius, background: token.colorBgContainer,
                      }}
                    >
                      <Form.Item name="idType" hidden>
                        <Input />
                      </Form.Item>
                      <Dropdown
                        trigger={['click']}
                        menu={{
                          items: idTypeItems
                            .filter((item) => item.value)
                            .map((item) => ({ key: item.value as string, label: item.value as string })),
                          selectedKeys: idType ? [idType] : [],
                          onClick: ({ key }) => form.setFieldsValue({ idType: key }),
                        }}
                      >
                        <span
                          role="button"
                          tabIndex={0}
                          aria-label="证件类型"
                          style={{
                            display: 'inline-flex', alignItems: 'center', gap: 4, flex: 'none', maxWidth: 110,
                            height: '100%', padding: '0 8px 0 11px', fontSize: 12,
                            borderRight: `1px solid ${token.colorBorderSecondary}`,
                            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                            color: idType ? token.colorText : token.colorTextTertiary,
                            cursor: 'pointer', userSelect: 'none',
                          }}
                        >
                          {idType ?? '类型'}
                          <DownOutlined style={{ fontSize: 10, color: token.colorTextTertiary }} />
                        </span>
                      </Dropdown>
                      <Form.Item name="idNo" noStyle>
                        <Input aria-label="证件号" variant="borderless" style={{ flex: 1, minWidth: 0 }} />
                      </Form.Item>
                    </div>
                  </Col>
                  <Field label="住院/床号">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                      <Form.Item name="admissionNumber" noStyle>
                        <Input aria-label="住院号" style={{ ...inputStyle, flex: 1, minWidth: 0 }} placeholder="住院号" />
                      </Form.Item>
                      <span style={{ color: token.colorTextTertiary }}>/</span>
                      <Form.Item name="bedNumber" noStyle>
                        <Input aria-label="床号" style={{ ...inputStyle, flex: '0 0 88px' }} placeholder="床号" />
                      </Form.Item>
                    </div>
                  </Field>
                  <Field label="身高/体重">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                      <Form.Item name="height" noStyle>
                        <InputNumber aria-label="身高" style={{ flex: 1, minWidth: 0 }} min={0} suffix={unitSuffix('cm')} />
                      </Form.Item>
                      <span style={{ color: token.colorTextTertiary }}>/</span>
                      <Form.Item name="weight" noStyle>
                        <InputNumber aria-label="体重" style={{ flex: 1, minWidth: 0 }} min={0} suffix={unitSuffix('kg')} />
                      </Form.Item>
                    </div>
                  </Field>
                  <Field label="手机号">
                    <Form.Item name="phone" noStyle>
                      <Input aria-label="电话" style={inputStyle} />
                    </Form.Item>
                  </Field>
                </Row>
                </div>

                {/* 2 请检信息 - vertical spine; the 2026-11-26 bench-height ruling
                    (round 5): row 1 = the apply trio, rows 2-3 = the CONSTANT
                    two-row band - 临床诊断 (fixed band height) on the LEFT, the
                    booking column on the RIGHT (预约时间 + 妊娠 stacked, with a
                    reserved empty slot for non-female patients) - the geometry
                    never shifts with 妊娠's visibility or content; long text
                    scrolls, the user drags it taller. The hairline rule between
                    the sections rides the wrapper's top border (2026-11-25). */}
                <div style={{ display: 'flex', alignItems: 'stretch', gap: 10, borderTop: `1px solid ${line}`, paddingTop: 10, marginBottom: 10 }}>
                  <div style={{ flex: 'none', width: 24, alignSelf: 'flex-start', borderRadius: 6, background: token.colorPrimaryBg, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 7, padding: '7px 0 9px' }}>
                    <span style={{ width: 16, height: 16, borderRadius: '50%', background: token.colorPrimary, color: token.colorBgContainer, fontSize: 11, fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>2</span>
                    <span style={{ writingMode: 'vertical-rl', letterSpacing: 4, marginBottom: -4, fontSize: 13, color: token.colorPrimary, lineHeight: '14px' }}>请检信息</span>
                  </div>
                  <Row gutter={[14, 9]} style={{ flex: 1, minWidth: 0, margin: 0 }}>
                  {/* the HIS application origin (us-apply-info) leads the section:
                      the department / doctor / moment that opened the 检查申请单 -
                      free text per the coordination ruling, HIS identities are external */}
                  <Field label="申请时间">
                    <Form.Item name="applyTime" noStyle>
                      <Input aria-label="申请时间" style={inputStyle} />
                    </Form.Item>
                  </Field>
                  <Field label="申请科室">
                    <Form.Item name="applyDept" noStyle>
                      <Input aria-label="申请科室" style={inputStyle} />
                    </Form.Item>
                  </Field>
                  <Field label="申请医生">
                    <Form.Item name="applyDoctor" noStyle>
                      <Input aria-label="申请医生" style={inputStyle} />
                    </Form.Item>
                  </Field>
                  {/* the diagnosis band LEFT + the booking/pregnancy stack RIGHT
                      (user ruling 2026-11-26 round 4): 临床诊断 fills the CONSTANT
                      two-row band on the left; 预约时间 + 妊娠 (female only) stack
                      in the right 8-col column with an always-RESERVED second slot
                      (round 5) so nothing shifts when 妊娠 toggles. The
                      visit-classification compound is US-station-retired
                      (2026-11-25: on a single-modality station the type chip is
                      zero-information); the compound form [modality Select |
                      source] re-appears in the radiology workstation proposal. */}
                  <Col xs={24} md={24} xl={16}>
                    <FieldRow label="临床诊断">
                      <Form.Item name="note" noStyle>
                        {/* the box fills the CONSTANT two-row band (round 5):
                            2 controls + the 9px row gap - flush top and bottom on
                            BOTH genders, no dead band under it, nothing moves when
                            妊娠 toggles. Long text scrolls natively; resize:
                            vertical still lets the user drag it taller. */}
                        <Input.TextArea aria-label="临床诊断" maxLength={255} style={{ width: '100%', height: token.controlHeight * 2 + 9, resize: 'vertical' }} />
                      </Form.Item>
                    </FieldRow>
                  </Col>
                  <Col xs={24} md={12} xl={8}>
                    <FieldRow label="预约时间">
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                        <Button
                          aria-label="选择预约时段"
                          type="primary"
                          ghost
                          disabled={!slotConfigured}
                          icon={<CalendarOutlined />}
                          onClick={openSlotDialog}
                        />
                        <span style={{ minWidth: 0, color: booking ? token.colorText : token.colorTextTertiary }}>
                          {booking ? (
                            <>
                              <span>周{WEEKDAYS[booking.date.day()]} {booking.date.format('MM-DD')}</span>{' '}
                              <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{booking.slot}–{booking.end}</span>
                            </>
                          ) : (
                            !slotConfigured && '未开通预约'
                          )}
                        </span>
                        {booking && (
                          <Button
                            type="text"
                            aria-label="清除预约时段"
                            title="清除（回到即到即检）"
                            onClick={() => setBooking(null)}
                            style={{ width: 18, height: 18, minWidth: 18, padding: 0, borderRadius: '50%', background: token.colorFillSecondary, color: token.colorTextTertiary }}
                          >
                            ×
                          </Button>
                        )}
                      </div>
                    </FieldRow>
                    {gender === GENDER_FEMALE_CODE ? (
                      <div style={{ marginTop: 9 }}>
                        <FieldRow label="妊娠">
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
                            <Form.Item name="pregnancyStatus" noStyle>
                              <Select aria-label="妊娠状态" style={{ flex: 1, minWidth: 0 }} allowClear placeholder="妊娠状态" options={valueKeyedOptions(pregnancyItems)} />
                            </Form.Item>
                            <Form.Item name="gestationalWeeks" noStyle>
                              <InputNumber aria-label="孕周" style={{ width: 96, flex: 'none' }} min={1} max={45} suffix={unitSuffix('周')} disabled={pregnancyStatus == null || PREGNANCY_DISABLE_VALUES.includes(pregnancyStatus)} />
                            </Form.Item>
                          </div>
                        </FieldRow>
                      </div>
                    ) : (
                      /* the RESERVED pregnancy slot (user ruling 2026-11-26 round 5):
                          the booking column always occupies its two rows, so the
                          band - and the diagnosis box filling it - never shifts
                          when the gender toggle shows/hides 妊娠 */
                      <div style={{ marginTop: 9, height: token.controlHeight }} aria-hidden="true" />
                    )}
                  </Col>
                </Row>
                </div>

                {/* 3 检查信息 - the split picker (方案 C, user-approved prototype
                    2026-11-24): the picked list lives left, the common-item rail
                    right; the seek typeahead rides the trailing ADD row of the
                    picked table. Vertical spine (2026-11-25); the section fills
                    the remaining bench height. Ops-column family chrome:
                    trash-icon 删除 + the right-edge refresh glyph (the
                    ConsoleTable invariants). The add entry is the dashed-block
                    family pattern (the rep-template 添加测量项 ruling). */}
                <div style={{ display: 'flex', alignItems: 'stretch', gap: 10, borderTop: `1px solid ${line}`, paddingTop: 10, flex: 1, minHeight: 0 }}>
                  <div style={{ flex: 'none', width: 24, alignSelf: 'flex-start', borderRadius: 6, background: token.colorPrimaryBg, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 7, padding: '7px 0 9px' }}>
                    <span style={{ width: 16, height: 16, borderRadius: '50%', background: token.colorPrimary, color: token.colorBgContainer, fontSize: 11, fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>3</span>
                    <span style={{ writingMode: 'vertical-rl', letterSpacing: 4, marginBottom: -4, fontSize: 13, color: token.colorPrimary, lineHeight: '14px' }}>检查信息</span>
                  </div>
                  <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', gap: 16, alignItems: 'stretch', flex: 1 }}>
                  <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                      <thead>
                        <tr>
                          <th style={thStyle}>检查项目</th>
                          <th style={{ ...thStyle, width: 220 }}>检查部位</th>
                          <th style={{ ...thStyle, width: 88, textAlign: 'right' }}>单价(元)</th>
                          <th style={{ ...thStyle, width: 72 }}>
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                              <span>操作</span>
                              <Tooltip title="刷新列表">
                                <Button
                                  type="text"
                                  size="small"
                                  aria-label="刷新列表"
                                  icon={<ReloadOutlined style={{ color: token.colorPrimary }} />}
                                  loading={itemsBusy}
                                  onClick={() => void refreshItems()}
                                />
                              </Tooltip>
                            </div>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {picked.map((item) => (
                          <tr key={item.id}>
                            <td style={tdStyle}>{item.exam}</td>
                            <td style={tdStyle}>{item.bodyName ?? '—'}</td>
                            <td style={{ ...tdStyle, textAlign: 'right', fontFamily: 'monospace' }}>{(item.price ?? 0).toFixed(2)}</td>
                            <td style={{ ...tdStyle, textAlign: 'center' }}>
                              <Tooltip title="删除">
                                <Button type="text" size="small" danger aria-label={`删除 ${item.exam}`} icon={<DeleteOutlined />} onClick={() => removePick(item.id)} />
                              </Tooltip>
                            </td>
                          </tr>
                        ))}
                        {/* the trailing ADD row (user ruling 2026-11-24): its 检查项目 cell
                            IS the seek typeahead - picking a candidate appends the row
                            above and clears the keyword for the next add */}
                        <tr>
                          <td style={tdStyle}>
                            <AutoComplete
                              aria-label="搜索检查项目"
                              style={{ width: '100%' }}
                              value={seekKw}
                              onSearch={(v) => setSeekKw(v)}
                              onSelect={(value) => {
                                const item = items.find((i) => String(i.id) === value)
                                if (item && !isPicked(item.id)) togglePick(item)
                                setSeekKw('')
                              }}
                              options={seekOptions}
                              filterOption={false}
                              popupMatchSelectWidth={false}
                              notFoundContent={null}
                              placeholder="搜项目名/代码，回车添加"
                            />
                          </td>
                          <td style={tdStyle} />
                          <td style={tdStyle} />
                        </tr>
                      </tbody>
                    </table>
                    {hasPerm('us:order:create') && (
                      <Button color="primary" variant="dashed" block aria-label="添加项目" icon={<PlusOutlined />} style={{ marginTop: 10 }} onClick={() => setItemDialogOpen(true)}>
                        添加项目
                      </Button>
                    )}
                    {/* the selection summary docked at the LEFT COLUMN bottom (user
                        ruling 2026-11-26): marginTop auto pins it level with the
                        常用项目 rail bottom, never spanning under the rail */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: token.colorSuccessBg, borderRadius: 5, padding: '5px 12px', marginTop: 'auto', fontWeight: 600 }}>
                      <span>已选择<span style={{ color: token.colorPrimary, margin: '0 3px' }}>{picked.length}</span>个检查项目</span>
                      <span>合计：¥{pickedTotal.toFixed(2)}</span>
                    </div>
                  </div>
                  <div style={{ flex: 'none', width: 260, borderLeft: `1px solid ${line}`, paddingLeft: 16 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, fontSize: 12, fontWeight: 700, color: token.colorTextTertiary, letterSpacing: '0.08em' }}>
                      常用项目
                      <span style={{ flex: 1, height: 1, background: line }} />
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'stretch' }}>
                      {commonItems.map((item) => {
                        const used = isPicked(item.id)
                        return (
                          <span
                            key={item.id}
                            role="button"
                            aria-label={`${item.exam}${used ? '（已选，点击移除）' : ''}`}
                            title={used ? '点击移除' : '点击加入'}
                            style={{
                              display: 'inline-flex', alignItems: 'center', gap: 5, height: 30, padding: '0 11px',
                              fontSize: 13,
                              borderRadius: 7, border: `1px solid ${used ? token.colorPrimary : token.colorPrimaryBorder}`,
                              background: used ? token.colorPrimary : token.colorPrimaryBg,
                              color: used ? token.colorBgContainer : token.colorPrimary,
                              cursor: 'pointer', userSelect: 'none',
                            }}
                            onClick={() => togglePick(item)}
                          >
                            <span style={{ display: used ? 'inline' : 'none', fontWeight: 700 }}>✓</span>
                            <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.exam}</span>
                          </span>
                        )
                      })}
                      {commonItems.length === 0 && (
                        <span style={{ color: token.colorTextTertiary, fontSize: 12 }}>暂无常用项目</span>
                      )}
                    </div>
                  </div>
                </div>
                  </div>
                </div>
              </div>
            </Form>

            {/* the bench foot: 重置/提交登记 (the long hint removed, user ruling 2026-11-24) */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 10, padding: '9px 14px', borderTop: `1px solid ${line}`, background: headBg }}>
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
                    <span style={{ color: token.colorTextSecondary, fontWeight: 600 }}>{identity.gender != null ? resolveDictLabel(genderItems, identity.gender) : ''}</span>
                    <span style={{ color: token.colorTextTertiary, fontFamily: 'monospace' }}>{identity.birthDate ?? ''}</span>
                  </div>
                  <div style={{ color: token.colorTextSecondary, lineHeight: 1.75 }}>
                    <span style={{ color: token.colorTextTertiary, marginRight: 2 }}>就诊号</span>{identity.cardNo ?? '—'} · <span style={{ color: token.colorTextTertiary, marginRight: 2 }}>证件号</span>{identity.idNo ?? '—'}
                  </div>
                </div>
                {checked && <span style={{ marginLeft: 'auto', width: 18, height: 18, borderRadius: '50%', background: token.colorBgContainer, color: token.colorPrimary, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>✓</span>}
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

      {/* the 添加项目 dialog - the precision picker (2026-11-24 redesign): the
          search live-filters with no dead 查询 button, rows toggle from the
          antd Checkbox or anywhere on the row itself, picked rows tint and
          common items carry the 常用 chip, the list scrolls under a sticky
          header, and the footer band carries the live 已选/合计 summary (the
          bench-foot chrome). */}
      <Modal title="添加检查项目" open={itemDialogOpen} onCancel={() => setItemDialogOpen(false)} footer={null} width={760}>
        <div style={{ padding: '14px 16px 12px', display: 'flex', gap: 8 }}>
          <Select
            aria-label="部位条件"
            style={{ width: 150, flex: 'none' }}
            placeholder="检查部位"
            allowClear
            value={dialogBody ?? undefined}
            onChange={(v) => setDialogBody(v ?? null)}
            options={bodyOptions.map((b) => ({ value: b, label: b }))}
          />
          <Input
            aria-label="项目搜索"
            allowClear
            style={{ flex: 1, minWidth: 0 }}
            prefix={<SearchOutlined style={{ color: token.colorTextTertiary }} />}
            placeholder="搜项目名称 / 编码，即时过滤"
            value={itemSearch}
            onChange={(event) => setItemSearch(event.target.value)}
          />
        </div>
        <div style={{ padding: '0 16px', maxHeight: '44vh', overflow: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed' }}>
            <thead>
              <tr>
                <th style={{ ...thStyle, width: 38, whiteSpace: 'nowrap', position: 'sticky', top: 0, background: token.colorBgContainer, boxShadow: `inset 0 -1px 0 ${line}` }}></th>
                <th style={{ ...thStyle, whiteSpace: 'nowrap', position: 'sticky', top: 0, background: token.colorBgContainer, boxShadow: `inset 0 -1px 0 ${line}` }}>检查项目</th>
                <th style={{ ...thStyle, width: 150, whiteSpace: 'nowrap', position: 'sticky', top: 0, background: token.colorBgContainer, boxShadow: `inset 0 -1px 0 ${line}` }}>检查部位</th>
                <th style={{ ...thStyle, width: 84, textAlign: 'right', whiteSpace: 'nowrap', position: 'sticky', top: 0, background: token.colorBgContainer, boxShadow: `inset 0 -1px 0 ${line}` }}>单价(元)</th>
              </tr>
            </thead>
            <tbody>
              {dialogItems.map((item) => {
                const used = isPicked(item.id)
                return (
                  <tr
                    key={item.id}
                    onClick={() => togglePick(item)}
                    style={{ background: used ? token.colorPrimaryBg : undefined, cursor: 'pointer' }}
                  >
                    <td style={{ ...tdStyle, padding: '7px 10px' }} onClick={(e) => e.stopPropagation()}>
                      <Checkbox aria-label={`选择 ${item.exam}`} checked={used} onChange={() => togglePick(item)} />
                    </td>
                    <td
                      title={item.exam}
                      style={{ ...tdStyle, padding: '7px 10px', display: 'flex', alignItems: 'center', fontWeight: used ? 600 : undefined, color: used ? token.colorPrimary : undefined }}
                    >
                      <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.exam}</span>
                      {item.isCommon && (
                        <span
                          style={{
                            marginLeft: 6, flex: 'none', fontSize: 11, lineHeight: '16px', padding: '0 6px', borderRadius: 3,
                            background: token.colorPrimaryBg, border: `1px solid ${token.colorPrimaryBorder}`,
                            color: token.colorPrimary, fontWeight: 400, whiteSpace: 'nowrap',
                          }}
                        >
                          常用
                        </span>
                      )}
                    </td>
                    <td title={item.bodyName ?? '—'} style={{ ...tdStyle, padding: '7px 10px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.bodyName ?? '—'}</td>
                    <td style={{ ...tdStyle, padding: '7px 10px', textAlign: 'right', fontFamily: 'monospace', whiteSpace: 'nowrap' }}>{item.price != null ? item.price.toFixed(2) : '—'}</td>
                  </tr>
                )
              })}
              {dialogItems.length === 0 && (
                <tr><td colSpan={4} style={{ padding: '18px 9px', textAlign: 'center', color: token.colorTextTertiary, fontSize: 13 }}>无匹配检查项目</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div style={{ margin: '10px 16px 0', padding: '9px 0', borderTop: `1px solid ${line}`, display: 'flex', alignItems: 'center' }}>
          <span style={{ fontSize: 13, fontWeight: 600, display: 'inline-flex', alignItems: 'center' }}>
            <span style={{ color: token.colorPrimary }}>已选 {picked.length} 项</span>
            <span style={{ margin: '0 8px', color: token.colorTextTertiary }}>·</span>
            <span style={{ color: token.colorTextSecondary }}>合计：¥{pickedTotal.toFixed(2)}</span>
          </span>
          <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
            <Button onClick={() => setItemDialogOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => setItemDialogOpen(false)}>确认添加</Button>
          </span>
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
              padding: '6px 8px', borderRadius: 6, borderBottom: `1px solid ${token.colorFillSecondary}`,
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
                <Button size="small" type="text" style={{ color: token.colorTextTertiary }} onClick={() => void markDefaultSearch(filter)}>
                  设默认
                </Button>
              )}
              <Button size="small" type="text" danger onClick={() => void removeSavedSearch(filter)}>
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

      {/* the booking slot dialog (us-booking-slot-picker, the 2026-11-24 redesign):
          the ALWAYS-VISIBLE mini month calendar (D+1 floor, today grayed with the
          即到即检 note, day-level 余量 badges aggregated client-side over the
          per-date read) + the 上午/下午 slot grid with per-slot capacity meters +
          the day summary. 确定 writes the date+slot pair; the trigger × clears. */}
      <Modal
        title="选择预约时段"
        open={slotDlgOpen}
        onCancel={() => setSlotDlgOpen(false)}
        width={840}
        styles={{ body: { maxHeight: '62vh', overflowY: 'auto' } }}
        footer={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10 }}>
            <Button onClick={() => setSlotDlgOpen(false)}>取消</Button>
            <Button type="primary" disabled={!dlgSlot} onClick={confirmSlotDialog}>
              确定
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', gap: 14, alignItems: 'stretch' }}>
          <div style={{ flex: '0 0 250px', display: 'flex', flexDirection: 'column', border: `1px solid ${token.colorBorderSecondary}`, borderRadius: 8, background: token.colorBgContainer, padding: '10px 10px 8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
            <Button size="small" type="text" aria-label="上一月" onClick={() => setCalMonth((prev) => prev.subtract(1, 'month'))}>‹</Button>
            <span style={{ fontWeight: 600, fontSize: 14, letterSpacing: '0.05em' }}>{calMonth.format('YYYY 年 MM 月')}</span>
            <Button size="small" type="text" aria-label="下一月" onClick={() => setCalMonth((prev) => prev.add(1, 'month'))}>›</Button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 2 }}>
            {['一', '二', '三', '四', '五', '六', '日'].map((week) => (
              <span key={week} style={{ fontSize: 12, color: token.colorTextTertiary, textAlign: 'center', paddingBottom: 2 }}>{week}</span>
            ))}
            {Array.from({ length: (calMonth.day() + 6) % 7 }, (_, i) => (
              <span key={`cal-blank-${i}`} />
            ))}
            {Array.from({ length: calMonth.daysInMonth() }, (_, i) => calMonth.date(i + 1)).map((day) => {
              const past = day.isBefore(tomorrow, 'day')
              const today = day.isSame(dayjs(), 'day')
              const selected = day.isSame(dlgDate, 'day')
              const badge = dayBadges[day.format('YYYY-MM-DD')]
              return (
                <button
                  key={day.format('YYYY-MM-DD')}
                  type="button"
                  disabled={past}
                  aria-label={day.format('YYYY-MM-DD')}
                  title={today ? '今天即到即检，不在此预约' : undefined}
                  onClick={() => {
                    setDlgDate(day)
                    setDlgSlot(null)
                  }}
                  style={{
                    height: 40, fontFamily: 'monospace', borderRadius: 6, border: 'none', padding: 0,
                    display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1,
                    cursor: past ? 'not-allowed' : 'pointer',
                    background: selected ? token.colorPrimary : 'transparent',
                    color: selected ? token.colorBgContainer : past ? token.colorTextQuaternary : token.colorText,
                    fontWeight: selected || today ? 700 : 400,
                    boxShadow: today && !selected ? `inset 0 0 0 1px ${token.colorPrimaryBorder}` : undefined,
                  }}
                >
                  <span style={{ fontSize: 13 }}>{day.date()}</span>
                  {badge != null && (
                    <span style={{ fontSize: 10, lineHeight: 1, fontWeight: 400, color: selected ? token.colorBgContainer : badge > 0 ? token.colorPrimary : token.colorTextTertiary }}>
                      {badge > 0 ? `余 ${badge}` : '满'}
                    </span>
                  )}
                </button>
              )
            })}
          </div>
          </div>
          <div style={{ flex: 1, minWidth: 0, paddingRight: 8 }}>
          {dlgGroups ? (
          dlgGroups.map((group) => (
            <div key={group.label} style={{ marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 8 }}>
                <span style={{ fontSize: 14, fontWeight: 700, letterSpacing: '0.12em', padding: '1px 10px', borderRadius: 999, background: group.info ? token.colorInfoBg : token.colorPrimaryBg, color: group.info ? token.colorInfo : token.colorPrimary }}>
                  {group.label}
                </span>
                <span style={{ fontSize: 13.5, color: token.colorTextTertiary, fontFamily: 'monospace', whiteSpace: 'nowrap' }}>
                  {group.list[0].start}–{group.list[group.list.length - 1].end}
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 8 }}>
                {group.list.map(renderSlotCard)}
              </div>
            </div>
          ))
        ) : (
          <div style={{ fontSize: 14, color: token.colorTextTertiary, textAlign: 'center', padding: '18px 0' }}>时段加载中…</div>
          )}
          </div>
        </div>
      </Modal>
    </ConsolePage>
  )
}

/** The 88px-right-label + flexing-control row, WITHOUT the grid cell - the
 *  unit every Field cell wraps and the booking/pregnancy stack composes
 *  directly (user ruling 2026-11-26 round 2). */
function FieldRow({ label, required, children }: {
  label: string
  required?: boolean
  children: React.ReactNode
}) {
  const { token } = theme.useToken()
  return (
    <div style={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>
      <span style={{ flex: '0 0 88px', width: 88, color: token.colorTextSecondary, textAlign: 'right', paddingRight: 8, whiteSpace: 'nowrap' }}>
        {required && <i style={{ color: token.colorError, fontStyle: 'normal' }}>* </i>}
        {label}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
    </div>
  )
}

/** The prototype's `field` row wrapped in the RESPONSIVE grid cell (xl 三列 /
 *  md 两列 / xs 单列 - the prototype's own 1180px media collapse, antd-native):
 *  a FieldRow inside the cell. Compound placements (the booking/pregnancy
 *  stack, the diagnosis band) compose FieldRow inside their own Col. */
function Field({ label, required, children }: {
  label: string
  required?: boolean
  children: React.ReactNode
}) {
  return (
    <Col xs={24} md={12} xl={8}>
      <FieldRow label={label} required={required}>{children}</FieldRow>
    </Col>
  )
}

import { Button, Col, Drawer, Form, Input, InputNumber, Modal, Radio, Row, Select, Switch, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import {
  cancelOrder,
  createOrder,
  pageOrders,
  registerOrder,
  resolvePatients,
} from '@/api/us-orders'
import type { UsLookupResult, UsOrderCreateBody, UsOrderStatus, UsOrderView, UsPatientIdentity } from '@/api/us-orders'
import { pageExamItems } from '@/api/exam-item'
import type { ExamItem } from '@/api/types'
import { ApiError } from '@/api/client'
import { usePerm } from '@/utils/perm'
import { PatientDemographicsForm } from '@/components/patient-demographics-form'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsoleFormHint,
  ConsolePage,
  ConsolePanel,
  ConsolePagination,
  ConsoleTable,
  ConsoleToolRow,
} from '@/pages/system/console-shell'

/**
 * US 工作站登记页 (us-station-registration-web tasks 1.1-2.3; spec delta
 * `web-us-station`): the first department-workstation page, against the REAL
 * `/api/v1/us/**` backend (no mock layer - ruling 2026-11-05). Mounted under
 * the US workstation W node (component key `us/registration/index`, the V3
 * seed); every affordance rides the `us:order:*` perm family, patient data
 * flows ONLY through the us endpoints (the server-side adapter owns the
 * patient domain - the page never calls patient APIs directly).
 *
 * The resolution chain flow (patient decision #2): lookup bar (证件号/就诊卡号)
 * -> exact match fills the demographics / soft match offers the candidate list /
 * no match opens an empty form with the 急诊占位 (PROVISIONAL) toggle. The
 * chosen-or-typed demographics ride the create body - the SERVER resolves-or-
 * registers (the A0715 conflict surfaces verbatim). The order form carries the
 * 检查项目 (setting catalog, US modality) and the 孕检 extension block
 * (decision #5 科室扩展归 us 域); the accession returns with the success toast.
 */
const STATUS_LABELS: Record<UsOrderStatus, { label: string; color: string }> = {
  BOOKED: { label: '预约', color: 'blue' },
  REGISTERED: { label: '已登记', color: 'green' },
  IN_PROGRESS: { label: '处理中', color: 'processing' },
  COMPLETED: { label: '已完成', color: 'default' },
  BOOKING_CANCELLED: { label: '预约已取消', color: 'default' },
  CANCELLED: { label: '已取消', color: 'default' },
}

const LIVE_STATES: UsOrderStatus[] = ['BOOKED', 'REGISTERED']
const PREGNANCY_OPTIONS = ['未孕', '早孕', '中晚孕', '未知'].map((label) => ({ value: label, label }))

interface RegistrationFormValues {
  placeholder?: boolean
  name?: string | null
  gender?: number | null
  idType?: string | null
  idNo?: string | null
  cardNo?: string | null
  phone?: string | null
  age?: string | null
  patientSource?: string | null
  admissionNumber?: string | null
  bedNumber?: string | null
  height?: number | null
  weight?: number | null
  itemIds?: number[]
  emgFlag?: boolean
  pregnancyStatus?: string | null
  gestationalWeeks?: number | null
  note?: string | null
}

export default function UsRegistrationPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [rows, setRows] = useState<UsOrderView[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)

  const [lookupField, setLookupField] = useState<'idNo' | 'cardNo'>('idNo')
  const [lookupValue, setLookupValue] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [saving, setSaving] = useState(false)

  const [candidates, setCandidates] = useState<UsPatientIdentity[] | null>(null)
  const [matchedPatId, setMatchedPatId] = useState<string | null>(null)
  const [cancelTarget, setCancelTarget] = useState<UsOrderView | null>(null)
  const [cancelReason, setCancelReason] = useState('')

  const [items, setItems] = useState<ExamItem[]>([])
  const [form] = Form.useForm<RegistrationFormValues>()
  const placeholder = Form.useWatch('placeholder', form)

  const load = useCallback(
    async (page: { pageNum: number; pageSize: number }) => {
      setLoading(true)
      try {
        const result = await pageOrders(page)
        setRows(result.records)
        setTotal(result.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '登记单列表加载失败')
      } finally {
        setLoading(false)
      }
    },
    [messageApi],
  )

  useEffect(() => {
    void load({ pageNum, pageSize })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageNum, pageSize])

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

  const openDrawer = (values?: Partial<RegistrationFormValues>, patId?: string | null) => {
    form.resetFields()
    form.setFieldsValue({ itemIds: [], emgFlag: false, placeholder: false, ...values })
    setMatchedPatId(patId ?? null)
    setDrawerOpen(true)
  }

  /** The resolution-chain flow: one lookup, three outcomes, no side effect. */
  const runLookup = async () => {
    const key = lookupValue.trim()
    if (!key) {
      messageApi.warning('请输入解析键值')
      return
    }
    const query = lookupField === 'idNo' ? { idNo: key } : { cardNo: key }
    try {
      const result: UsLookupResult = await resolvePatients(query)
      if (result.result === 'MATCHED' && result.identity) {
        openDrawer({ ...identityToForm(result.identity) }, result.identity.patId)
        messageApi.success(`已匹配档案 ${result.identity.patId ?? ''}`)
      } else if (result.result === 'CANDIDATES' && result.candidates?.length) {
        setCandidates(result.candidates)
      } else {
        openDrawer()
        messageApi.info('未匹配到档案，请建档登记')
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '患者解析失败')
    }
  }

  const pickCandidate = (identity: UsPatientIdentity) => {
    setCandidates(null)
    openDrawer(identityToForm(identity), identity.patId)
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      const demographics = stripPlaceholder(values)
      const body: UsOrderCreateBody = {
        ...demographics,
        examItems: (values.itemIds ?? []).map((id) => {
          const item = items.find((candidate) => candidate.id === id)
          return { itemId: id, itemName: item?.exam ?? '' }
        }),
        emgFlag: values.emgFlag ? 1 : 0,
        ext: {
          pregnancyStatus: values.pregnancyStatus ?? null,
          gestationalWeeks: values.gestationalWeeks ?? null,
          note: values.note ?? null,
        },
      }
      const created = await createOrder(body)
      messageApi.success(`登记单已创建，检查号 ${created.accessionNo}`)
      setDrawerOpen(false)
      await load({ pageNum, pageSize })
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg) // backend refusals (A0715 conflict, A0400) surface verbatim
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return // AntD validation rejection - keep the drawer open on the fields
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
      await load({ pageNum, pageSize })
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
      await load({ pageNum, pageSize })
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '取消失败')
    }
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
      width: 110,
      align: 'center',
      render: (value: UsOrderStatus) => {
        const meta = STATUS_LABELS[value]
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: '急诊',
      dataIndex: 'emgFlag',
      key: 'emgFlag',
      width: 80,
      align: 'center',
      render: (value: number) => (value === 1 ? <Tag color="orange">急诊</Tag> : null),
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
    {
      // the flex column absorbs leftover space under tableLayout=fixed
      title: '备注',
      dataIndex: 'note',
      key: 'note',
      render: (value: string | null) =>
        value ? (
          <span style={{ color: token.colorTextSecondary }}>{value}</span>
        ) : (
          <span style={{ color: token.colorTextTertiary }}>—</span>
        ),
    },
  ]

  return (
    <ConsolePage>
      {contextHolder}
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            hasPerm('us:order:create') && (
              <ConsoleAddButton label="新建登记" onClick={() => openDrawer()} />
            )
          }
        >
          {/* the resolution-chain lookup bar (children = the tool row's leading slot) */}
          <Radio.Group
            aria-label="解析字段"
            value={lookupField}
            onChange={(event) => setLookupField(event.target.value)}
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: 'idNo', label: '证件号' },
              { value: 'cardNo', label: '就诊卡号' },
            ]}
          />
          <Input
            aria-label="解析键值"
            style={{ width: 220 }}
            placeholder={lookupField === 'idNo' ? '身份证号' : '就诊卡号'}
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
        </ConsoleToolRow>
        <ConsoleTable<UsOrderView>
          rowKey={(row) => row.orderId}
          loading={loading}
          columns={columns}
          dataSource={rows}
          onRefresh={() => void load({ pageNum, pageSize })}
          actions={{
            width: 130,
            render: (row) => (
              <>
                {LIVE_STATES.includes(row.status) && hasPerm('us:order:update') && (
                  <ConsoleAction
                    label="登记"
                    onClick={() => void transitionRegister(row)}
                  />
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
      </ConsolePanel>

      {/* the candidate list (soft match - a LIST, never an error) */}
      <Modal
        title="请选择患者档案"
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
            onClick={() => pickCandidate(identity)}
            onKeyDown={(event) => event.key === 'Enter' && pickCandidate(identity)}
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

      <Drawer
        title="新建登记"
        aria-label="新建登记"
        width={560}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void save()}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={form} layout="vertical" disabled={saving} initialValues={{ placeholder: false }}>
          <PatientDemographicsForm />
          <Form.Item
            name="itemIds"
            label="检查项目"
            rules={[{ required: true, message: '请选择检查项目' }]}
          >
            <Select
              mode="multiple"
              optionFilterProp="label"
              placeholder="US 模态项目（setting 目录）"
              notFoundContent={items.length === 0 ? 'US 项目目录为空' : undefined}
              options={items.map((item) => ({ value: item.id, label: item.exam }))}
            />
          </Form.Item>
          {/* the lone 急诊 boolean rides the switch-row pattern (ruling 2026-11-19) */}
          <div
            data-item-common-row
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: token.marginLG,
            }}
          >
            <label htmlFor="us-emg">急诊</label>
            <Form.Item name="emgFlag" valuePropName="checked" noStyle>
              <Switch id="us-emg" aria-label="急诊" />
            </Form.Item>
          </div>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="pregnancyStatus" label="妊娠状态">
                <Select allowClear options={PREGNANCY_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="gestationalWeeks" label="孕周(周)">
                <InputNumber aria-label="孕周" style={{ width: '100%' }} min={1} max={45} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="note" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
          {matchedPatId && (
            <div style={{ marginBottom: token.marginSM, color: token.colorTextSecondary }}>
              已匹配档案 <span className="mono">{matchedPatId}</span>
            </div>
          )}
          <ConsoleFormHint>
            {placeholder
              ? '急诊占位：无名氏占位档案，检查号照发，补录时走解析-合并链（决策 #3）'
              : '保存后系统按解析链建档/匹配，检查号由平台号规则发放'}
          </ConsoleFormHint>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

/** A resolved identity fills the demographics block (uid/patId/state stay server-side). */
function identityToForm(identity: UsPatientIdentity): Partial<RegistrationFormValues> {
  return {
    idType: identity.idType ?? null,
    idNo: identity.idNo ?? null,
    cardNo: identity.cardNo ?? null,
    name: identity.name ?? null,
    gender: identity.gender ?? null,
    phone: identity.phone ?? null,
  }
}

/** The form values -> the create-body demographics (drop UI-only fields; audit-safe:
 * only whitelisted demographics keys ride the body - the server owns the rest). */
function stripPlaceholder(values: RegistrationFormValues): Partial<UsOrderCreateBody> {
  return {
    idType: values.idType ?? null,
    idNo: values.idNo ?? null,
    cardNo: values.cardNo ?? null,
    name: values.placeholder ? null : (values.name ?? null),
    gender: values.gender ?? null,
    phone: values.phone ?? null,
    age: values.age ?? null,
    patientSource: values.patientSource ?? null,
    admissionNumber: values.admissionNumber ?? null,
    bedNumber: values.bedNumber ?? null,
    height: values.height ?? null,
    weight: values.weight ?? null,
  }
}

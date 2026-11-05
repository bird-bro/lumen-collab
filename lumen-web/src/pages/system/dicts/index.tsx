import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { Button, Form, Input, InputNumber, Modal, Popconfirm, Tooltip, Typography, message } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  createCatal,
  createDetail,
  deleteCatal,
  deleteDetail,
  listCatal,
  listDetails,
  updateCatal,
  updateDetail,
} from '@/api/dicts'
import { ApiError } from '@/api/client'
import type { DictCatal, DictDetail } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePanel,
  ConsolePagination,
  ConsoleTable,
  ConsoleToolRow,
  readForm,
} from '../console-shell'

/**
 * 字典管理 (batch 6b, task 6.6; spec `specs/web-system-console`
 * § Dictionary Management). Layout source: `prototype/dicts-logs.html` upper
 * half - the catalog table with the detail table BENEATH (the prototype's
 * confirmed stacked form, not a drawer). Selecting a catalog row scopes the
 * detail table (its 新建明细 stays disabled until a row is selected, which is
 * also the guard against the backend's missing-caCode A0410).
 */

interface CatalValues {
  code: string
  name: string
  sort?: number
  remark?: string
}

interface DetailValues {
  label: string
  value?: string
  sort?: number
  remark?: string
}

export default function DictsPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:dict:list')

  const [catals, setCatals] = useState<DictCatal[]>([])
  const [catalTotal, setCatalTotal] = useState(0)
  const [catalPage, setCatalPage] = useState(1)
  const [catalPageSize, setCatalPageSize] = useState(20)
  const [catalLoading, setCatalLoading] = useState(true)
  const [queryDraft, setQueryDraft] = useState('')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<DictCatal | null>(null)
  const [details, setDetails] = useState<DictDetail[]>([])
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailPage, setDetailPage] = useState(1)
  const [messageApi, messageHolder] = message.useMessage()
  const [saving, setSaving] = useState(false)

  const [catalOpen, setCatalOpen] = useState(false)
  const [catalEditTarget, setCatalEditTarget] = useState<DictCatal | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailEditTarget, setDetailEditTarget] = useState<DictDetail | null>(null)
  const [catalForm] = Form.useForm<CatalValues>()
  const [detailForm] = Form.useForm<DetailValues>()

  const reloadCatal = useCallback(
    async (nextPage = catalPage, nextSize = catalPageSize) => {
      setCatalLoading(true)
      try {
        const data = await listCatal(nextPage, nextSize)
        setCatals(
          query.trim()
            ? data.records.filter((row) => row.name.toLowerCase().includes(query.trim().toLowerCase()))
            : data.records,
        )
        setCatalTotal(data.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '字典目录加载失败')
      } finally {
        setCatalLoading(false)
      }
    },
    [catalPage, catalPageSize, messageApi, query],
  )

  useEffect(() => {
    void reloadCatal(catalPage, catalPageSize)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [catalPage, catalPageSize])

  const queryMounted = useRef(false)
  useEffect(() => {
    // Skip the mount run: the page/pageSize effect already issued the first read.
    if (!queryMounted.current) {
      queryMounted.current = true
      return
    }
    void reloadCatal(1, catalPageSize)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query])

  const reloadDetails = useCallback(
    async (caCode: string) => {
      setDetailLoading(true)
      try {
        setDetails(await listDetails(caCode))
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '字典明细加载失败')
      } finally {
        setDetailLoading(false)
      }
    },
    [messageApi],
  )

  const selectCatal = useCallback(
    (row: DictCatal) => {
      setSelected(row)
      setDetailPage(1)
      void reloadDetails(row.code)
    },
    [reloadDetails],
  )
  const DETAIL_PAGE_SIZE = 10
  const detailRows = details.slice((detailPage - 1) * DETAIL_PAGE_SIZE, detailPage * DETAIL_PAGE_SIZE)

  const closeCatal = useCallback(() => {
    setCatalOpen(false)
    setCatalEditTarget(null)
    catalForm.resetFields()
  }, [catalForm])

  const submitCatal = useCallback(async () => {
    const values = await readForm(catalForm)
    if (!values) return
    setSaving(true)
    try {
      if (catalEditTarget) {
        await updateCatal(catalEditTarget.id, values)
      } else {
        await createCatal(values)
      }
      closeCatal()
      await reloadCatal()
    } catch (error) {
      if (error instanceof ApiError) {
        // A0714 duplicate catalog code lands ON THE FORM; dialog stays open.
        catalForm.setFields([{ name: 'code', errors: [error.msg] }])
      } else {
        messageApi.error('保存字典目录失败')
      }
    } finally {
      setSaving(false)
    }
  }, [catalEditTarget, catalForm, closeCatal, messageApi, reloadCatal])

  const closeDetail = useCallback(() => {
    setDetailOpen(false)
    setDetailEditTarget(null)
    detailForm.resetFields()
  }, [detailForm])

  const submitDetail = useCallback(async () => {
    if (!selected) return
    const values = await readForm(detailForm)
    if (!values) return
    setSaving(true)
    try {
      if (detailEditTarget) {
        await updateDetail(detailEditTarget.id, values)
      } else {
        await createDetail({ caId: selected.id, ...values })
      }
      closeDetail()
      await reloadDetails(selected.code)
    } catch (error) {
      if (error instanceof ApiError) {
        // A0714 duplicate (caCode,value) lands ON THE FORM; dialog stays open.
        detailForm.setFields([{ name: 'value', errors: [error.msg] }])
      } else {
        messageApi.error('保存字典明细失败')
      }
    } finally {
      setSaving(false)
    }
  }, [closeDetail, detailEditTarget, detailForm, messageApi, reloadDetails, selected])

  // 目录 + ID as two columns, ID first and CENTERED (user request 2026-11-05).
  const catalColumns: TableColumnsType<DictCatal> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 140,
      align: 'center',
      // Same type treatment as the 目录 column (user request 2026-11-05).
      render: (id: number) => <span style={{ fontVariantNumeric: 'tabular-nums' }}>{id}</span>,
    },
    {
      // 表头居中、内容居左 (user request 2026-11-05) - AntD column `align`
      // applies to both th and td, so the split uses per-cell overrides.
      title: '目录',
      dataIndex: 'name',
      onHeaderCell: () => ({ style: { textAlign: 'center' } }),
      render: (name: string) => <span style={{ fontWeight: 500 }}>{name}</span>,
    },
  ]

  const detailColumns: TableColumnsType<DictDetail> = [
    { title: '标签', dataIndex: 'label', width: 160 },
    { title: '值', dataIndex: 'value', width: 140, render: (v: string | null) => v ?? '—' },
    { title: '排序', dataIndex: 'sort', width: 90 },
    { title: '备注', dataIndex: 'remark', render: (remark: string | null) => remark ?? '—' },
  ]

  return (
    // 左右双列(随访 EIS 字典页布局,用户 2026-11-04):左 = 目录表,右 = 选中目录的明细表。
    <div style={{ height: '100%', display: 'flex', gap: 16 }}>
      {messageHolder}
      <div style={{ flex: '0 0 460px', width: 460, minWidth: 0, display: 'flex' }}>
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            canManage ? (
              <ConsoleAddButton
                label="新建目录"
                onClick={() => {
                  setCatalEditTarget(null)
                  setCatalOpen(true)
                }}
              />
            ) : undefined
          }
        >
          <Input
            allowClear
            style={{ width: 200, minWidth: 0 }}
            placeholder="目录名称"
            aria-label="目录名称"
            value={queryDraft}
            onChange={(event) => setQueryDraft(event.target.value)}
            onPressEnter={() => {
              setQuery(queryDraft)
              setCatalPage(1)
            }}
          />
          <Tooltip title="查询">
            <Button
              aria-label="查询"
              icon={<SearchOutlined />}
              onClick={() => {
                setQuery(queryDraft)
                setCatalPage(1)
              }}
            />
          </Tooltip>
        </ConsoleToolRow>

        <ConsoleTable<DictCatal>
          rowKey={(row) => row.id}
          columns={catalColumns}
          dataSource={catals}
          loading={catalLoading}
          onRow={(row) => ({
            onClick: () => selectCatal(row),
            style: { cursor: 'pointer' },
          })}
          actions={
            canManage
              ? {
                  width: 120,
                  render: (row) => [
                    <ConsoleAction
                      key="edit"
                      label="编辑目录"
                      onClick={() => {
                        setCatalEditTarget(row)
                        catalForm.setFieldsValue({
                          code: row.code,
                          name: row.name,
                          sort: row.sort,
                          remark: row.remark ?? '',
                        })
                        setCatalOpen(true)
                      }}
                    />,
                    <Popconfirm
                      key="delete"
                      title="删除目录"
                      description="同时删除该目录下全部明细的引用关系,确认删除?"
                      okText="删除"
                      cancelText="取消"
                      onConfirm={() => {
                        void (async () => {
                          try {
                            await deleteCatal(row.id)
                            if (selected?.id === row.id) setSelected(null)
                            await reloadCatal()
                          } catch (error) {
                            messageApi.error(error instanceof ApiError ? error.msg : '删除目录失败')
                          }
                        })()
                      }}
                    >
                      <ConsoleAction label="删除目录" icon="remove" danger onClick={() => undefined} />
                    </Popconfirm>,
                  ],
                }
              : undefined
          }
          onRefresh={() => void reloadCatal()}
        />

        <ConsolePagination
          total={catalTotal}
          current={catalPage}
          pageSize={catalPageSize}
          onChange={(next, nextSize) => {
            setCatalPage(next)
            setCatalPageSize(nextSize)
          }}
          onShowSizeChange={(_current, size) => {
            setCatalPage(1)
            setCatalPageSize(size)
          }}
        />
      </ConsolePanel>
      </div>

      <div style={{ flex: '1 1 0', minWidth: 0, display: 'flex' }}>
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            canManage ? (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                disabled={!selected}
                title={selected ? undefined : '先在上方选择一个字典目录'}
                onClick={() => {
                  setDetailEditTarget(null)
                  detailForm.setFieldsValue({ sort: (details.length + 1) * 10 })
                  setDetailOpen(true)
                }}
              >
                新建明细
              </Button>
            ) : undefined
          }
        >
          <Typography.Text style={{ color: 'inherit' }}>
            {selected ? (
              <>
                明细于目录 <Typography.Text code>{selected.code}</Typography.Text>
              </>
            ) : (
              '点击左侧目录行以加载其明细(sort 升序)'
            )}
          </Typography.Text>
        </ConsoleToolRow>

        <ConsoleTable<DictDetail>
          rowKey={(row) => row.id}
          columns={detailColumns}
          dataSource={detailRows}
          loading={detailLoading}
          actions={
            canManage
              ? {
                  width: 120,
                  render: (row) => [
                    <ConsoleAction
                      key="edit"
                      label="编辑明细"
                      onClick={() => {
                        setDetailEditTarget(row)
                        detailForm.setFieldsValue({
                          label: row.label,
                          value: row.value ?? '',
                          sort: row.sort,
                          remark: row.remark ?? '',
                        })
                        setDetailOpen(true)
                      }}
                    />,
                    <Popconfirm
                      key="delete"
                      title="删除明细"
                      okText="删除"
                      cancelText="取消"
                      onConfirm={() => {
                        void (async () => {
                          try {
                            await deleteDetail(row.id)
                            if (selected) await reloadDetails(selected.code)
                          } catch (error) {
                            messageApi.error(error instanceof ApiError ? error.msg : '删除明细失败')
                          }
                        })()
                      }}
                    >
                      <ConsoleAction label="删除明细" icon="remove" danger onClick={() => undefined} />
                    </Popconfirm>,
                  ],
                }
              : undefined
          }
        />

        <ConsolePagination
          total={details.length}
          current={detailPage}
          pageSize={DETAIL_PAGE_SIZE}
          showSizeChanger={false}
          onChange={(next) => setDetailPage(next)}
        />
      </ConsolePanel>
      </div>

      <Modal
        open={catalOpen}
        title={catalEditTarget ? '编辑目录' : '新建目录'}
        okText="保 存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void submitCatal()}
        onCancel={closeCatal}
        destroyOnClose
        width={520}
      >
        <Form<CatalValues> form={catalForm} layout="vertical" disabled={saving}>
          <Form.Item
            name="code"
            label="目录编码"
            extra="编码重复时,后端 A 类错误 msg 将显示于此表单,弹窗保持打开"
            rules={[{ required: true, message: '请输入目录编码' }]}
          >
            <Input placeholder="如 common_status" autoComplete="off" />
          </Form.Item>
          <Form.Item name="name" label="目录名称" rules={[{ required: true, message: '请输入目录名称' }]}>
            <Input placeholder="如 通用状态" autoComplete="off" />
          </Form.Item>
          <Form.Item name="sort" label="排序" initialValue={10}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={detailOpen}
        title={detailEditTarget ? '编辑明细' : '新建明细'}
        okText="保 存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void submitDetail()}
        onCancel={closeDetail}
        destroyOnClose
        width={520}
      >
        <Form<DetailValues> form={detailForm} layout="vertical" disabled={saving}>
          <Form.Item name="label" label="标签" rules={[{ required: true, message: '请输入标签' }]}>
            <Input placeholder="如 启用" autoComplete="off" />
          </Form.Item>
          <Form.Item name="value" label="值">
            <Input placeholder="如 1" autoComplete="off" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

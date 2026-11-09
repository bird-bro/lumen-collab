import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { Button, ColorPicker, Drawer, Form, Input, InputNumber, Popconfirm, Space, Tooltip, Typography, message, theme } from 'antd'
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
  ConsolePage,
  ConsolePagination,
  ConsoleQueryButton,
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
 * also the guard against a detail without a caId - the backend's A0400).
 */

interface CatalValues {
  name: string
  sort?: number
  remark?: string
}

interface DetailValues {
  code?: number | null
  value: string
  tag?: string
  flag?: string
  sort?: number
  color?: string
  css?: string
  remark?: string
}

export default function DictsPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const canManage = hasPerm('system:dict:list')
  // button-level gating (button-perm-enforcement-web): the two panels answer to
  // their own perms strings (the V15 seeded F nodes pinned server-side)
  const canCatalCreate = canManage && hasPerm('system:dict-catal:create')
  const canCatalUpdate = canManage && hasPerm('system:dict-catal:update')
  const canCatalDelete = canManage && hasPerm('system:dict-catal:delete')
  const canDetailCreate = canManage && hasPerm('system:dict-detail:create')
  const canDetailUpdate = canManage && hasPerm('system:dict-detail:update')
  const canDetailDelete = canManage && hasPerm('system:dict-detail:delete')

  const [catals, setCatals] = useState<DictCatal[]>([])
  const [catalTotal, setCatalTotal] = useState(0)
  const [catalPage, setCatalPage] = useState(1)
  const [catalPageSize, setCatalPageSize] = useState(20)
  const [catalLoading, setCatalLoading] = useState(true)
  const [queryDraft, setQueryDraft] = useState('')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<DictCatal | null>(null)
  const selectedRef = useRef<DictCatal | null>(null)
  const [detailQueryDraft, setDetailQueryDraft] = useState('')
  const [detailQuery, setDetailQuery] = useState('')
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

  const reloadDetails = useCallback(
    async (caId: number) => {
      setDetailLoading(true)
      try {
        setDetails(await listDetails(caId))
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
      selectedRef.current = row
      setSelected(row)
      setDetailPage(1)
      void reloadDetails(row.id)
    },
    [reloadDetails],
  )

  const reloadCatal = useCallback(
    async (nextPage = catalPage, nextSize = catalPageSize) => {
      setCatalLoading(true)
      try {
        const data = await listCatal(nextPage, nextSize)
        const rows = query.trim()
          ? data.records.filter((row) => row.name.toLowerCase().includes(query.trim().toLowerCase()))
          : data.records
        setCatals(rows)
        setCatalTotal(data.total)
        // 打开页面/查询后默认选中第一条目录(随访行为,用户 2026-11-04)
        if (rows.length > 0 && !selectedRef.current) selectCatal(rows[0])
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

  const [detailPageSize, setDetailPageSize] = useState(20)
  const detailRows = details
    .filter((row) => !detailQuery || row.value?.toLowerCase().includes(detailQuery.toLowerCase()))
    .slice((detailPage - 1) * detailPageSize, detailPage * detailPageSize)

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
        // A0714 duplicate catalog name lands ON THE FORM (V12: the name is the
        // only unique key left); dialog stays open.
        catalForm.setFields([{ name: 'name', errors: [error.msg] }])
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
      await reloadDetails(selected.id)
    } catch (error) {
      if (error instanceof ApiError) {
        // A0714 duplicate (caId,value) lands ON THE FORM; dialog stays open.
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

  // OPS dict detail columns, slimmed to the V9 model (value 是唯一文本字段，标签列
  // 随 label 列删除；备用值对改名 标签值/标记值): 编码/数据值/标签值/标记值/排序/
  // 颜色/样式/备注. 颜色 renders a chip when set.
  const detailColumns: TableColumnsType<DictDetail> = [
    { title: '编码', dataIndex: 'code', width: 130, onCell: () => ({ style: { textAlign: 'center' } }) },
    { title: '数据值', dataIndex: 'value', width: 160, render: (v: string | null) => (v ? <span style={{ fontWeight: 500 }}>{v}</span> : '') },
    { title: '标签', dataIndex: 'tag', width: 110, onCell: () => ({ style: { textAlign: 'center' } }) },
    { title: '标记', dataIndex: 'flag', width: 110, onCell: () => ({ style: { textAlign: 'center' } }) },
    {
      title: '颜色',
      dataIndex: 'color',
      width: 150,
      onCell: () => ({ style: { textAlign: 'center' } }),
      render: (v: string | null) =>
        v ? (
          <Space size={6}>
            <span
              style={{ width: 26, height: 26, borderRadius: 4, display: 'inline-block', border: `1px solid ${token.colorBorder}`, background: v }}
              title={v}
            />
            <Typography.Text>{v}</Typography.Text>
          </Space>
        ) : (
          ''
        ),
    },
    {
      title: '样式',
      dataIndex: 'css',
      width: 200,
      onCell: () => ({ style: { textAlign: 'center' } }),
      render: (v: string | null) =>
        v ? <Typography.Text code>{v}</Typography.Text> : '',
    },
    { title: '排序', dataIndex: 'sort', width: 80, onCell: () => ({ style: { textAlign: 'center' } }) },
    { title: '备注', dataIndex: 'remark' },
  ]

  return (
    // 左右双列(随访 EIS 字典页布局,用户 2026-11-04):左 = 目录表,右 = 选中目录的明细表。
    <div style={{ height: '100%', display: 'flex', gap: 6 }}>
      {messageHolder}
      <div style={{ flex: '0 0 460px', width: 460, minWidth: 0, display: 'flex' }}>
      <ConsolePage>
        <ConsoleToolRow
          trailing={
            canCatalCreate ? (
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
          // OPS dict-page highlight-current-row: the selection state above now
          // also renders as the primary hairline + left-bar chrome.
          selectedRowKey={selected?.id ?? null}
          onRow={(row) => ({
            onClick: () => selectCatal(row),
            style: { cursor: 'pointer' },
          })}
          actions={
            canManage
              ? {
                  width: 120,
                  render: (row) => [
                    canCatalUpdate ? (
                      <ConsoleAction
                        key="edit"
                        label="编辑目录"
                        onClick={() => {
                          setCatalEditTarget(row)
                          catalForm.setFieldsValue({
                            name: row.name,
                            sort: row.sort,
                            remark: row.remark ?? '',
                          })
                          setCatalOpen(true)
                        }}
                      />
                    ) : null,
                    canCatalDelete ? (
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
                      </Popconfirm>
                    ) : null,
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
          showSizeChanger={false}
          onChange={(next, nextSize) => {
            setCatalPage(next)
            setCatalPageSize(nextSize)
          }}
          onShowSizeChange={(_current, size) => {
            setCatalPage(1)
            setCatalPageSize(size)
          }}
        />
      </ConsolePage>
      </div>

      <div style={{ flex: '1 1 0', minWidth: 0, display: 'flex' }}>
      <ConsolePage>
        <ConsoleToolRow
          trailing={
            canDetailCreate ? (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                disabled={!selected}
                title={selected ? undefined : '先在上方选择一个字典目录'}
                onClick={() => {
                  setDetailEditTarget(null)
                  // 字典编码默认值: 目录 id 为前序, 目录内自增 (用户 2026-11-05)。
                  const nextCode = selected ? selected.id * 100 + details.length + 1 : null
                  detailForm.setFieldsValue({ sort: (details.length + 1) * 10, code: nextCode })
                  setDetailOpen(true)
                }}
              >
                新建明细
              </Button>
            ) : undefined
          }
        >
          <Input
            allowClear
            style={{ width: 200, minWidth: 0 }}
            placeholder="数据值"
            aria-label="数据值"
            value={detailQueryDraft}
            onChange={(event) => setDetailQueryDraft(event.target.value)}
            onPressEnter={() => {
              setDetailQuery(detailQueryDraft)
              setDetailPage(1)
            }}
          />
          <ConsoleQueryButton
            onClick={() => {
              setDetailQuery(detailQueryDraft)
              setDetailPage(1)
            }}
          />
        </ConsoleToolRow>

        <ConsoleTable<DictDetail>
          rowKey={(row) => row.id}
          columns={detailColumns}
          dataSource={detailRows}
          loading={detailLoading}
          onRefresh={selected ? () => void reloadDetails(selected.id) : undefined}
          actions={
            canManage
              ? {
                  width: 120,
                  render: (row) => [
                    canDetailUpdate ? (
                      <ConsoleAction
                        key="edit"
                        label="编辑明细"
                        onClick={() => {
                          setDetailEditTarget(row)
                          detailForm.setFieldsValue({
                            code: row.code ?? undefined,
                            value: row.value ?? '',
                            tag: row.tag ?? '',
                            flag: row.flag ?? '',
                            sort: row.sort,
                            color: row.color ?? '',
                            css: row.css ?? '',
                            remark: row.remark ?? '',
                          })
                          setDetailOpen(true)
                        }}
                      />
                    ) : null,
                    canDetailDelete ? (
                      <Popconfirm
                        key="delete"
                        title="删除明细"
                        okText="删除"
                        cancelText="取消"
                        onConfirm={() => {
                          void (async () => {
                            try {
                              await deleteDetail(row.id)
                              if (selected) await reloadDetails(selected.id)
                            } catch (error) {
                              messageApi.error(error instanceof ApiError ? error.msg : '删除明细失败')
                            }
                          })()
                        }}
                      >
                        <ConsoleAction label="删除明细" icon="remove" danger onClick={() => undefined} />
                      </Popconfirm>
                    ) : null,
                  ],
                }
              : undefined
          }
        />

        <ConsolePagination
          total={details.length}
          current={detailPage}
          pageSize={detailPageSize}
          onChange={(next, nextSize) => {
            setDetailPage(next)
            setDetailPageSize(nextSize)
          }}
          onShowSizeChange={(_current, size) => {
            setDetailPage(1)
            setDetailPageSize(size)
          }}
        />
      </ConsolePage>
      </div>

      <Drawer
        open={catalOpen}
        title={catalEditTarget ? '编辑目录' : '新建目录'}
        width={460}
        onClose={closeCatal}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeCatal}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitCatal()}>
              保 存
            </Button>
          </div>
        }
      >
        <Form<CatalValues> form={catalForm} layout="vertical" disabled={saving}>
          {/* 目录编码 field dropped (user ruling 2026-11-05, backend V12): the
              catalog anchors by id and the display name carries the uniqueness. */}
          <Form.Item name="name" label="目录名称" rules={[{ required: true, message: '请输入目录名称' }]}>
            <Input placeholder="如 通用状态" autoComplete="off" />
          </Form.Item>
          <Form.Item name="sort" label="排序" initialValue={10}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        open={detailOpen}
        title={detailEditTarget ? '编辑明细' : '新建明细'}
        width={460}
        onClose={closeDetail}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeDetail}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitDetail()}>
              保 存
            </Button>
          </div>
        }
      >
        <Form<DetailValues> form={detailForm} layout="vertical" disabled={saving}>
          <Form.Item
            name="code"
            label="字典编码"
            rules={[{ required: true, message: '请输入字典编码' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="如 101" />
          </Form.Item>
          <Form.Item
            name="value"
            label="数据值"
            rules={[{ required: true, message: '请输入数据值' }]}
          >
            <Input placeholder="如 启用" autoComplete="off" />
          </Form.Item>
          <Form.Item name="tag" label="标签">
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="flag" label="标记">
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="sort" label="排序" initialValue={10}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="color"
            label="颜色"
            getValueFromEvent={(color) => (color ? `#${color.toHex()}` : '')}
          >
            <ColorPicker showText allowClear disabledAlpha style={{ width: '100%', height: 32, justifyContent: 'space-between' }} />
          </Form.Item>
          <Form.Item name="css" label="样式">
            <Input placeholder="color:rgb(82,196,26);" autoComplete="off" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}

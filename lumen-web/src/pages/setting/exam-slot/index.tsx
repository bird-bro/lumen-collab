import {
  Button,
  InputNumber,
  Popconfirm,
  Select,
  TimePicker,
  Drawer,
  Form,
  Tag,
  message,
  theme,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useState } from 'react'
import dayjs from 'dayjs'
import {
  createSlotConfig,
  deleteSlotConfig,
  listSlotConfigs,
  updateSlotConfig,
} from '@/api/us-slot-configs'
import type { SlotConfigView, SlotWindow } from '@/api/us-slot-configs'
import { ApiError } from '@/api/client'
import { usePerm } from '@/utils/perm'
import { dictItemsById, MODALITY_DICT_ID } from '@/utils/dict-items'
import type { DictItem } from '@/api/types'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsoleFormHint,
  ConsolePage,
  ConsolePanel,
  ConsoleTable,
  ConsoleToolRow,
} from '@/pages/system/console-shell'

/**
 * 预约设置 page (us-registration-redesign tasks 5.1-5.3; spec delta
 * `web-system-console` § 预约设置 Page - 拍板 06-A): the per-modality pre-registration
 * policy CRUD against the REAL `/api/v1/us/slot-configs` (the us-schema owner serves
 * it; the page sits in the 检查配置 family behind `setting:slot:*` - the V51-seeded
 * leaf). The modality token is the pool IDENTITY: the dict-1024 SELECT renders
 * DISABLED on edit and only the policy fields submit. The windows editor is the
 * start/end pair list; an EMPTY window set = the modality refuses pre-registration
 * (the fail-closed default, surfaced by ConsoleFormHint).
 */

interface SlotFormValues {
  modality?: string
  granularityMinutes?: number
  slotCapacity?: number
}

export default function SlotConfigPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [rows, setRows] = useState<SlotConfigView[]>([])
  const [loading, setLoading] = useState(false)
  const [dictItems, setDictItems] = useState<DictItem[]>([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<SlotConfigView | null>(null)
  const [saving, setSaving] = useState(false)
  const [windows, setWindows] = useState<SlotWindow[]>([])
  const [typeFilter, setTypeFilter] = useState<string | null>(null)
  const [form] = Form.useForm<SlotFormValues>()

  // The 检查类型 filter narrows IN MEMORY (us-slot-list-filter): the endpoint
  // returns one policy per 检查类型 - a catalog-bounded list - so the client-side
  // narrowing rides the mount-time payload and issues no additional request.
  const visibleRows = useMemo(
    () => (typeFilter ? rows.filter((row) => row.modality === typeFilter) : rows),
    [rows, typeFilter],
  )

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await listSlotConfigs())
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '预约设置加载失败')
    } finally {
      setLoading(false)
    }
  }, [messageApi])

  useEffect(() => {
    void load()
    let alive = true
    void (async () => {
      try {
        const items = await dictItemsById(MODALITY_DICT_ID)
        if (alive) setDictItems(items)
      } catch {
        if (alive) setDictItems([]) // dict failure degrades to an empty select (the dict-degrade precedent)
      }
    })()
    return () => {
      alive = false
    }
  }, [load])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ granularityMinutes: 60, slotCapacity: 4 })
    setWindows([{ start: '', end: '' }]) // one empty pair ready to fill
    setDrawerOpen(true)
  }

  const openEdit = (row: SlotConfigView) => {
    setEditing(row)
    form.resetFields()
    form.setFieldsValue({
      modality: row.modality,
      granularityMinutes: row.granularityMinutes,
      slotCapacity: row.slotCapacity,
    })
    setWindows(row.openWindows ?? [])
    setDrawerOpen(true)
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      const modality = (values.modality ?? '').trim()
      if (!modality) {
        messageApi.warning('请选择检查类型')
        return
      }
      const body = {
        modality,
        granularityMinutes: values.granularityMinutes ?? 60,
        slotCapacity: values.slotCapacity ?? 4,
        openWindows: windows.filter((w) => w.start && w.end),
      }
      if (editing) {
        await updateSlotConfig(editing.id, body)
        messageApi.success('预约设置已更新')
      } else {
        await createSlotConfig(body)
        messageApi.success('预约设置已创建')
      }
      setDrawerOpen(false)
      await load()
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg) // the duplicate-token A0400 surfaces verbatim, drawer kept
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setSaving(false)
    }
  }

  const remove = async (row: SlotConfigView) => {
    try {
      await deleteSlotConfig(row.id)
      messageApi.success('已删除（该检查类型即刻不开放预约）')
      await load()
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const columns: TableColumnsType<SlotConfigView> = [
    {
      title: '检查类型',
      dataIndex: 'modality',
      key: 'modality',
      width: 100,
      align: 'center',
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '时段时长(分钟)',
      dataIndex: 'granularityMinutes',
      key: 'granularityMinutes',
      width: 130,
      align: 'center',
    },
    {
      title: '号源数量',
      dataIndex: 'slotCapacity',
      key: 'slotCapacity',
      width: 120,
      align: 'center',
    },
    {
      title: '放号时段',
      dataIndex: 'openWindows',
      key: 'openWindows',
      width: 280,
      render: (_: unknown, row) => {
        const pairs = row.openWindows ?? []
        if (pairs.length === 0) {
          return <span style={{ color: token.colorTextTertiary }}>未放号（预约拒绝）</span>
        }
        return (
          <span>
            {pairs.map((pair, index) => (
              // Tag chrome defaults to 12px — bumped to the table's 14px so the
              // window tags read the same size as the neighboring cells
              <Tag key={index} style={{ fontSize: token.fontSize }}>{`${pair.start}–${pair.end}`}</Tag>
            ))}
          </span>
        )
      },
    },
  ]

  return (
    <ConsolePage>
      {contextHolder}
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            hasPerm('setting:slot:create') && (
              <ConsoleAddButton label="新建" onClick={openCreate} />
            )
          }
        >
          <Select
            aria-label="检查类型过滤"
            allowClear
            placeholder="检查类型"
            style={{ width: 160 }}
            value={typeFilter}
            options={dictItems.map((item) => ({ value: item.value ?? '', label: item.value ?? '' }))}
            onChange={(next) => setTypeFilter(next ?? null)}
          />
        </ConsoleToolRow>
        <ConsoleTable<SlotConfigView>
          rowKey={(row) => row.id}
          loading={loading}
          columns={columns}
          dataSource={visibleRows}
          onRefresh={() => void load()}
          actions={{
            width: 120,
            render: (row) => (
              <>
                {hasPerm('setting:slot:update') && (
                  <ConsoleAction label="编辑" onClick={() => openEdit(row)} />
                )}
                {hasPerm('setting:slot:delete') && (
                  <Popconfirm
                    title={`删除 ${row.modality} 的预约设置？`}
                    description="删除后该检查类型即刻不开放预约，患者将无法预约该检查"
                    onConfirm={() => void remove(row)}
                  >
                    <ConsoleAction label="删除" icon="remove" danger onClick={() => {}} />
                  </Popconfirm>
                )}
              </>
            ),
          }}
        />
      </ConsolePanel>

      <Drawer
        title={editing ? `编辑预约设置 ${editing.modality}` : '新建预约设置'}
        width={480}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        styles={{ body: { display: 'flex', flexDirection: 'column' } }}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void save()}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={form} layout="vertical" disabled={saving}>
          <Form.Item name="modality" label="检查类型" rules={[{ required: true, message: '请选择检查类型' }]}>
            <Select
              aria-label="检查类型"
              disabled={editing != null}
              options={dictItems.map((item) => ({ value: item.value ?? '', label: item.value ?? '' }))}
            />
          </Form.Item>
          <Form.Item name="granularityMinutes" label="时段时长">
            <InputNumber aria-label="时段时长" style={{ width: '100%' }} min={5} max={240} addonAfter="分钟" />
          </Form.Item>
          <Form.Item name="slotCapacity" label="号源数量">
            <InputNumber aria-label="号源数量" style={{ width: '100%' }} min={1} max={99} />
          </Form.Item>
          <Form.Item label="放号时段">
            {windows.map((pair, index) => (
              <div key={index} style={{ display: 'flex', gap: 6, marginBottom: 6, alignItems: 'center' }}>
                <TimePicker
                  aria-label="窗口开始"
                  format="HH:mm"
                  placeholder="开始"
                  needConfirm={false}
                  style={{ flex: 1, minWidth: 0 }}
                  value={pair.start ? dayjs(pair.start, 'HH:mm') : null}
                  onChange={(t) =>
                    setWindows((prev) =>
                      prev.map((item, i) =>
                        i === index ? { ...item, start: t ? t.format('HH:mm') : '' } : item,
                      ),
                    )
                  }
                />
                <span aria-hidden="true" style={{ color: token.colorTextTertiary }}>–</span>
                <TimePicker
                  aria-label="窗口结束"
                  format="HH:mm"
                  placeholder="结束"
                  needConfirm={false}
                  style={{ flex: 1, minWidth: 0 }}
                  value={pair.end ? dayjs(pair.end, 'HH:mm') : null}
                  onChange={(t) =>
                    setWindows((prev) =>
                      prev.map((item, i) =>
                        i === index ? { ...item, end: t ? t.format('HH:mm') : '' } : item,
                      ),
                    )
                  }
                />
                <Button
                  type="text"
                  size="small"
                  danger
                  aria-label="移除时段"
                  icon={<DeleteOutlined />}
                  onClick={() => setWindows((prev) => prev.filter((_, i) => i !== index))}
                />
              </div>
            ))}
            {/* the rep-template 添加节 idiom (user ruling 2026-11-24): dashed
                block button with visible text for dynamic-row adds */}
            <Button
              color="primary"
              variant="dashed"
              block
              aria-label="添加时段"
              icon={<PlusOutlined />}
              onClick={() => setWindows((prev) => [...prev, { start: '', end: '' }])}
            >
              添加时段
            </Button>
          </Form.Item>
        </Form>
        {/* pinned just above the drawer footer (user ruling 2026-11-24, re-affirmed
            during the drawer-family unification review): the flex body seats the
            band at the bottom on short content, sticky keeps it visible above the
            footer while the form scrolls */}
        <div style={{ marginTop: 'auto', position: 'sticky', bottom: 0 }}>
          <ConsoleFormHint>
            时段列表留空表示该检查类型不开放预约，患者将无法预约该检查；检查类型创建后不可修改。
          </ConsoleFormHint>
        </div>
      </Drawer>
    </ConsolePage>
  )
}

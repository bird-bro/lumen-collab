import {
  Button,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Drawer,
  Form,
  Tag,
  message,
  theme,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
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
  const [form] = Form.useForm<SlotFormValues>()

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
    form.setFieldsValue({ granularityMinutes: 30, slotCapacity: 4 })
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
        messageApi.warning('请选择模态')
        return
      }
      const body = {
        modality,
        granularityMinutes: values.granularityMinutes ?? 30,
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
      messageApi.success('已删除（该模态预约即刻 fail-closed）')
      await load()
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const columns: TableColumnsType<SlotConfigView> = [
    {
      title: '模态',
      dataIndex: 'modality',
      key: 'modality',
      width: 100,
      render: (value: string) => <span className="mono">{value}</span>,
    },
    {
      title: '开台时段粒度',
      dataIndex: 'granularityMinutes',
      key: 'granularityMinutes',
      width: 130,
      render: (value: number) => `${value} 分钟`,
    },
    {
      title: '每时段容量',
      dataIndex: 'slotCapacity',
      key: 'slotCapacity',
      width: 120,
      render: (value: number) => `容量 ${value}`,
    },
    {
      title: '开台时间窗',
      dataIndex: 'openWindows',
      key: 'openWindows',
      render: (_: unknown, row) => {
        const pairs = row.openWindows ?? []
        if (pairs.length === 0) {
          return <span style={{ color: token.colorTextTertiary }}>未开台（预约拒绝）</span>
        }
        return (
          <span>
            {pairs.map((pair, index) => (
              <Tag key={index}>{`${pair.start}–${pair.end}`}</Tag>
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
          <span style={{ color: token.colorTextSecondary }}>
            每模态一条预约策略：时段粒度 / 每时段容量 / 开台时间窗；未配置的模态拒绝预约
          </span>
        </ConsoleToolRow>
        <ConsoleTable<SlotConfigView>
          rowKey={(row) => row.id}
          loading={loading}
          columns={columns}
          dataSource={rows}
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
                    description="删除后该模态预约即刻拒绝（fail-closed）"
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
        aria-label="预约设置抽屉"
        width={480}
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
        <Form form={form} layout="horizontal" labelCol={{ style: { width: 88 } }} disabled={saving}>
          <Form.Item name="modality" label="模态" rules={[{ required: true, message: '请选择模态' }]}>
            <Select
              aria-label="模态"
              disabled={editing != null}
              placeholder={editing ? undefined : 'dict-1024 模态'}
              options={dictItems.map((item) => ({ value: item.value ?? '', label: item.value ?? '' }))}
            />
          </Form.Item>
          <Form.Item name="granularityMinutes" label="时段粒度">
            <InputNumber aria-label="时段粒度" style={{ width: '100%' }} min={5} max={240} />
          </Form.Item>
          <Form.Item name="slotCapacity" label="时段容量">
            <InputNumber aria-label="时段容量" style={{ width: '100%' }} min={1} max={99} />
          </Form.Item>
          <div style={{ marginBottom: token.marginSM }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
              <span>开台时间窗</span>
              <Button
                size="small"
                onClick={() => setWindows((prev) => [...prev, { start: '', end: '' }])}
              >
                添加窗口
              </Button>
            </div>
            {windows.map((pair, index) => (
              <div key={index} style={{ display: 'flex', gap: 6, marginBottom: 6, alignItems: 'center' }}>
                <Input
                  aria-label="窗口开始"
                  placeholder="HH:mm"
                  style={{ width: 110 }}
                  value={pair.start}
                  onChange={(event) =>
                    setWindows((prev) =>
                      prev.map((item, i) =>
                        i === index ? { ...item, start: event.target.value } : item,
                      ),
                    )
                  }
                />
                <Input
                  aria-label="窗口结束"
                  placeholder="HH:mm"
                  style={{ width: 110 }}
                  value={pair.end}
                  onChange={(event) =>
                    setWindows((prev) =>
                      prev.map((item, i) =>
                        i === index ? { ...item, end: event.target.value } : item,
                      ),
                    )
                  }
                />
                <Button
                  size="small"
                  danger
                  onClick={() => setWindows((prev) => prev.filter((_, i) => i !== index))}
                >
                  移除
                </Button>
              </div>
            ))}
          </div>
          <ConsoleFormHint>
            空窗口 = 未开台：该模态预约即刻拒绝（fail-closed）。模态创建后不可变更（时段池身份）。
          </ConsoleFormHint>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

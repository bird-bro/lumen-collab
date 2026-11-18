import { Button, Drawer, Form, Input, InputNumber, Popconfirm, Switch, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { createNumberRule, deleteNumberRule, pageNumberRules, updateNumberRule } from '@/api/patient'
import type { PatientNumberRule } from '@/api/patient'
import { ApiError } from '@/api/client'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePage,
  ConsolePanel,
  ConsolePagination,
  ConsoleTable,
  ConsoleToolRow,
} from '@/pages/system/console-shell'

/**
 * 号码规则 page (patient-domain task 5.1; spec delta `web-system-console`
 * § Number Rule Page Shell / § Number Rule Form and Delete): the paged rule
 * table against the real `/api/v1/patient/**` backend (no mock layer - ruling
 * 2026-11-05), the `patient:rule:*` perm-gated affordances, the drawer
 * create/edit with the fixed 影像号 type echo (v1), the field-level
 * serial-length validation (1-10), and the confirm-delete whose active-rule
 * refusal surfaces verbatim.
 *
 * ruleType values are codes ('pat_id' today); the label map renders the
 * console vocabulary and falls back to the raw code for future types (the
 * exam-side accession rules follow the same per-service pattern - design D3).
 * The stamp flag IS data (`stamp` carries the Java date pattern); v1's form
 * exposes only the on/off switch - a null stamp defaults server-side to
 * `yyMMdd`, so the drawer never edits the pattern itself.
 */

const RULE_TYPE_LABELS: Record<string, string> = { pat_id: '影像号' }
/** The fixed v1 rule type (the archive-creation issuance source). */
const V1_RULE_TYPE = 'pat_id'

interface NumberRuleFormValues {
  prefix?: string | null
  suffix?: string | null
  serialLength: number
  isStamp: boolean
}

function ruleTypeLabel(ruleType: string): string {
  return RULE_TYPE_LABELS[ruleType] ?? ruleType
}

function paddedSerial(row: PatientNumberRule): string {
  return String(row.serialCur).padStart(Math.max(1, row.serialLength), '0')
}

export default function NumberRulePage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [rows, setRows] = useState<PatientNumberRule[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<PatientNumberRule | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<NumberRuleFormValues>()
  const isStamp = Form.useWatch('isStamp', form)
  const prefix = Form.useWatch('prefix', form)
  const serialLength = Form.useWatch('serialLength', form)

  const load = useCallback(
    async (page: { pageNum: number; pageSize: number }) => {
      setLoading(true)
      try {
        const result = await pageNumberRules(page)
        setRows(result.records)
        setTotal(result.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '号码规则列表加载失败')
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

  const openAdd = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ serialLength: 4, isStamp: true, prefix: null, suffix: null })
    setDrawerOpen(true)
  }

  const openEdit = (row: PatientNumberRule) => {
    setEditing(row)
    form.setFieldsValue({
      prefix: row.prefix,
      suffix: row.suffix,
      serialLength: row.serialLength,
      isStamp: row.isStamp === 1,
    })
    setDrawerOpen(true)
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      // stamp: null + isStamp=1 defaults server-side to yyMMdd (b4 decision 5)
      const body = {
        ruleType: editing ? editing.ruleType : V1_RULE_TYPE,
        prefix: values.prefix || null,
        suffix: values.suffix || null,
        serialLength: values.serialLength,
        isStamp: values.isStamp ? 1 : 0,
        stamp: null,
        active: editing ? editing.active : 1,
      }
      if (editing) {
        await updateNumberRule(editing.id, body)
        messageApi.success('修改成功')
      } else {
        await createNumberRule(body)
        messageApi.success('添加成功')
      }
      setDrawerOpen(false)
      await load({ pageNum, pageSize })
    } catch (error) {
      if (error instanceof ApiError) {
        // Backend refusals (active-rule A0714 conflict, A0400 refusal) surface verbatim.
        messageApi.error(error.msg)
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        // AntD validation rejection - keep the drawer open on the fields.
        return
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setSaving(false)
    }
  }

  const remove = async (row: PatientNumberRule) => {
    try {
      await deleteNumberRule(row.id)
      messageApi.success('删除成功')
      await load({ pageNum, pageSize })
    } catch (error) {
      // The active-rule refusal ("启用的规则不可删除，请先停用...") surfaces verbatim.
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const columns: TableColumnsType<PatientNumberRule> = [
    {
      title: '规则类型',
      dataIndex: 'ruleType',
      key: 'ruleType',
      width: 110,
      render: (value: string) => <Tag style={{ marginInlineEnd: 0 }}>{ruleTypeLabel(value)}</Tag>,
    },
    {
      title: '前缀',
      dataIndex: 'prefix',
      key: 'prefix',
      width: 90,
      render: (value: string | null) =>
        value ? <span className="mono">{value}</span> : <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    {
      title: '后缀',
      dataIndex: 'suffix',
      key: 'suffix',
      width: 90,
      render: (value: string | null) =>
        value ? <span className="mono">{value}</span> : <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    { title: '流水长度', dataIndex: 'serialLength', key: 'serialLength', width: 90 },
    {
      title: '日期戳',
      dataIndex: 'isStamp',
      key: 'isStamp',
      width: 130,
      render: (value: number, row) =>
        value === 1 ? (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span
              style={{ width: 6, height: 6, borderRadius: '50%', background: token.colorSuccess, display: 'inline-block' }}
            />
            <span style={{ color: token.colorTextTertiary }}>{row.stamp}</span>
          </span>
        ) : (
          <span style={{ color: token.colorTextTertiary }}>不带</span>
        ),
    },
    {
      title: '当前流水',
      dataIndex: 'serialCur',
      key: 'serialCur',
      width: 110,
      render: (_: number, row) => <span className="mono">{paddedSerial(row)}</span>,
    },
    {
      title: '状态',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (value: number) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span
            style={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              background: value === 1 ? token.colorSuccess : token.colorTextQuaternary,
              display: 'inline-block',
            }}
          />
          {value === 1 ? '启用' : '停用'}
        </span>
      ),
    },
  ]

  return (
    <ConsolePage>
      {contextHolder}
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            hasPerm('patient:rule:create') && <ConsoleAddButton label="新建规则" onClick={openAdd} />
          }
        >
          <span style={{ color: token.colorTextTertiary, fontSize: token.fontSizeSM }}>
            影像号在创建患者档案时按启用规则自动发放
          </span>
        </ConsoleToolRow>
        <ConsoleTable<PatientNumberRule>
          rowKey={(row) => row.id}
          loading={loading}
          columns={columns}
          dataSource={rows}
          onRefresh={() => void load({ pageNum, pageSize })}
          actions={{
            width: 130,
            render: (row) => (
              <>
                {hasPerm('patient:rule:update') && (
                  <ConsoleAction label="编辑" onClick={() => openEdit(row)} />
                )}
                {hasPerm('patient:rule:delete') && (
                  <Popconfirm
                    title={`确定删除规则“${ruleTypeLabel(row.ruleType)}”？`}
                    onConfirm={() => void remove(row)}
                  >
                    <ConsoleAction label="删除" icon="remove" danger onClick={() => undefined} />
                  </Popconfirm>
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

      <Drawer
        title={editing ? '编辑规则' : '新建规则'}
        width={480}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnClose
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void save()}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={form} layout="vertical">
          {/* 规则类型 echo: plain label row (the 2026-11-16 ruling - info echoes
              merge into the label, no Form.Item control slot); v1 fixes 影像号. */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: token.marginLG,
              color: token.colorTextHeading,
              fontSize: token.fontSize,
            }}
          >
            规则类型：
            <Tag style={{ marginInlineEnd: 0, flex: '0 0 auto', fontSize: token.fontSize, fontWeight: 600 }}>
              {ruleTypeLabel(editing ? editing.ruleType : V1_RULE_TYPE)}
            </Tag>
          </div>
          <Form.Item
            name="prefix"
            label="前缀"
            extra="≤8 位，字母或数字，可空"
            rules={[{ pattern: /^[A-Za-z0-9]{0,8}$/, message: '前缀仅支持 1-8 位字母或数字' }]}
          >
            <Input maxLength={8} placeholder="如 IM" />
          </Form.Item>
          <Form.Item
            name="suffix"
            label="后缀"
            extra="≤8 位，字母或数字，可空"
            rules={[{ pattern: /^[A-Za-z0-9]{0,8}$/, message: '后缀仅支持 1-8 位字母或数字' }]}
          >
            <Input maxLength={8} placeholder="可空" />
          </Form.Item>
          <Form.Item
            name="serialLength"
            label="流水长度"
            extra="流水号左补零至该长度，如 0001"
            rules={[
              { required: true, message: '流水长度不能为空' },
              // the ONLY bounds here: an InputNumber min/max prop would clamp the
              // typed value on blur and the field error could never surface
              // (web-system-console § Number Rule Form and Delete)
              { type: 'number', min: 1, max: 10, message: '流水长度须在 1–10 之间' },
            ]}
          >
            <InputNumber style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="isStamp" label="是否带日期戳" valuePropName="checked">
            <Switch />
          </Form.Item>
          <div style={{ color: token.colorTextTertiary, fontSize: token.fontSizeSM, marginTop: -12 }}>
            {isStamp
              ? `号码形如 ${(prefix || '') + '261118' + '0001'}（yyMMdd + ${serialLength ?? 4} 位流水）`
              : '号码形如 ' + (prefix || '') + '0001（仅流水）'}
          </div>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

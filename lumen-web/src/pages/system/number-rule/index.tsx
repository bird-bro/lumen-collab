import { Button, Col, Drawer, Form, Input, InputNumber, Popconfirm, Row, Select, Switch, Tag, message, theme } from 'antd'

const { TextArea } = Input
import type { TableColumnsType } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import {
  createNumberRule,
  deleteNumberRule,
  pageNumberRules,
  setNumberRuleActive,
  updateNumberRule,
} from '@/api/number-rules'
import type { NumberRule } from '@/api/number-rules'
import type { DictItem } from '@/api/types'
import { ApiError } from '@/api/client'
import {
  NUMBER_STAMP_FORMAT_DICT_ID,
  NUMBER_TYPE_DICT_ID,
  dictItemsById,
} from '@/utils/dict-items'
import { usePerm } from '@/utils/perm'
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
 * 号码规则 page (number-rule-unification-web task 1.2; spec delta `web-system-console`
 * § Number Rule Page Shell / § Number Rule Form and Delete after the 2026-11-19
 * platform migration): the paged rule table against the real `/api/v1/system/**`
 * backend (no mock layer - ruling 2026-11-05), the `system:number:*` perm-gated
 * affordances, the drawer create/edit with the dict-driven 规则类型 SELECT (the
 * 编号类型 catalog 1028, number-rule-type-dict - the stored token IS the dict
 * value, so the Tag/echo render it verbatim; the edit echo stays a Tag, the family
 * is immutable), the field-level serial-length validation (1-10), and the
 * confirm-delete whose active-rule refusal surfaces verbatim.
 *
 * The stamp flag IS data (`stamp` carries the Java date/time pattern); the drawer
 * offers the dict catalog 「编号格式」 (patterns are operator-maintained, including
 * timestamp patterns). A dict miss/failure degrades to an empty Select without
 * blocking the rule table (both catalogs - 编号类型 1028 and 编号格式 1029 -
 * degrade independently; lookups anchor by the catalog ID - ruling 2026-11-19).
 */

interface NumberRuleFormValues {
  ruleType?: string
  prefix?: string | null
  suffix?: string | null
  serialLength: number
  isStamp: boolean
  stamp?: string | null
  /** the EDIT drawer's status switch (create always starts enabled); boolean mirrors the row's 0/1 */
  active?: boolean
  remark?: string | null
}

function paddedSerial(row: NumberRule): string {
  return String(row.serialCur).padStart(Math.max(1, row.serialLength), '0')
}

/** Java date patterns → dayjs tokens (the 编号格式 vocabulary is Java patterns):
 * yy/yyyy→YY/YYYY (dayjs 2/4-digit year), dd→DD (zero-padded day); HH/mm/ss
 * carry the same meaning in both - without this, 'yyMMdd' renders the literal
 * 'yy' in the sample. */
function javaPatternToDayjs(pattern: string): string {
  return pattern.replace(/yyyy/g, 'YYYY').replace(/yy/g, 'YY').replace(/dd/g, 'DD')
}

/** The composed-number sample (prefix + date in the rule's pattern + a fresh
 * zero-padded serial + suffix) - what the NEXT burn through this rule looks
 * like. Shared by the table's 示例 column and the drawer's preview. */
function composeSample(
  prefix: string | null | undefined,
  stamp: string | null | undefined,
  serialLength: number | null | undefined,
  suffix: string | null | undefined,
  stamped: boolean,
): string {
  const stampPart = stamped && stamp ? dayjs().format(javaPatternToDayjs(stamp)) : ''
  const serial = '1'.padStart(Math.max(1, serialLength ?? 4), '0')
  return (prefix || '') + stampPart + serial + (suffix || '')
}

export default function NumberRulePage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [rows, setRows] = useState<NumberRule[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<NumberRule | null>(null)
  const [saving, setSaving] = useState(false)
  const [typeItems, setTypeItems] = useState<DictItem[]>([])
  const [stampItems, setStampItems] = useState<DictItem[]>([])
  const [form] = Form.useForm<NumberRuleFormValues>()
  const isStamp = Form.useWatch('isStamp', form)
  const stamp = Form.useWatch('stamp', form)
  const prefix = Form.useWatch('prefix', form)
  const suffix = Form.useWatch('suffix', form)
  const serialLength = Form.useWatch('serialLength', form)
  const stampOptions = stampItems
    .filter((item): item is DictItem & { value: string } => Boolean(item.value))
    .map((item) => ({ value: item.value, label: item.value }))
  // the rule-type vocabulary: the dict 编号类型 1028 tokens ARE both the stored value
  // and the display label (single-text dict model), so the options are the values
  const typeOptions = typeItems
    .filter((item): item is DictItem & { value: string } => Boolean(item.value))
    .map((item) => ({ value: item.value, label: item.value }))
  // the create picker refuses families that already hold a live rule (uk_number_rule_active
  // + the backend A0714 stay the hard guards) - the loaded rows are the source
  const activeTypes = new Set(rows.filter((row) => row.active === 1).map((row) => row.ruleType))
  const createTypeOptions = typeOptions.map((option) => ({
    ...option,
    disabled: activeTypes.has(option.value),
    title: activeTypes.has(option.value) ? '该类型已有启用规则' : undefined,
  }))

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

  useEffect(() => {
    let alive = true
    void (async () => {
      // both catalogs degrade independently - a missing/failing one yields an
      // empty Select without blocking the other or the rule table
      const [types, stamps] = await Promise.allSettled([
        dictItemsById(NUMBER_TYPE_DICT_ID),
        dictItemsById(NUMBER_STAMP_FORMAT_DICT_ID),
      ])
      if (!alive) return
      setTypeItems(types.status === 'fulfilled' ? types.value : [])
      setStampItems(stamps.status === 'fulfilled' ? stamps.value : [])
    })()
    return () => {
      alive = false
    }
  }, [])

  const openAdd = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      // the create picker's default: the FIRST family without a live rule (an
      // active family is disabled in the picker - preselecting it would be doomed)
      ruleType: createTypeOptions.find((option) => !option.disabled)?.value,
      serialLength: 4,
      isStamp: true,
      prefix: null,
      suffix: null,
      stamp: null,
      remark: null,
    })
    setDrawerOpen(true)
  }

  const openEdit = (row: NumberRule) => {
    setEditing(row)
    form.setFieldsValue({
      prefix: row.prefix,
      suffix: row.suffix,
      serialLength: row.serialLength,
      isStamp: row.isStamp === 1,
      stamp: row.stamp,
      // the edit drawer's status switch mirrors the row (create starts enabled)
      active: row.active === 1,
      remark: row.remark,
    })
    setDrawerOpen(true)
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      const body = {
        // the required-rule blocks submission before the '' tail can matter - the
        // fallbacks only satisfy the typed body when the dict catalog is empty
        ruleType: editing
          ? editing.ruleType
          : values.ruleType ?? createTypeOptions.find((option) => !option.disabled)?.value ?? '',
        prefix: values.prefix || null,
        suffix: values.suffix || null,
        serialLength: values.serialLength,
        isStamp: values.isStamp ? 1 : 0,
        stamp: values.stamp || null,
        // the edit drawer's status switch drives the full-surface save; create
        // starts enabled (the list's switch owns the lifecycle from there)
        active: editing ? (values.active ? 1 : 0) : 1,
        remark: values.remark || null,
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

  const remove = async (row: NumberRule) => {
    try {
      await deleteNumberRule(row.id)
      messageApi.success('删除成功')
      await load({ pageNum, pageSize })
    } catch (error) {
      // The active-rule refusal ("启用的规则不可删除，请先停用...") surfaces verbatim.
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  // The orgs-page 启停 pattern: optimistic flip, server rollback via the reload,
  // no success toast - the switch IS the feedback. The dedicated lifecycle
  // endpoint (PUT /{id}/active) replaces the full-surface PUT here.
  const toggleActive = async (row: NumberRule, next: 0 | 1) => {
    setRows((prev) => prev.map((r) => (r.id === row.id ? { ...r, active: next } : r)))
    try {
      await setNumberRuleActive(row.id, next)
    } catch (error) {
      // The duplicate-active refusal (activating while the family's other rule is
      // live - "同类型仅允许一条启用规则") surfaces verbatim; the reload below
      // re-syncs the switch from the server truth.
      messageApi.error(error instanceof ApiError ? error.msg : '启用状态更新失败')
    }
    await load({ pageNum, pageSize })
  }

  const columns: TableColumnsType<NumberRule> = [
    {
      title: '类型',
      dataIndex: 'ruleType',
      key: 'ruleType',
      align: 'center',
      width: 110,
      render: (value: string) => (
        // the stored token IS the dict 编号类型 value - the display label renders verbatim
        <Tag style={{ marginInlineEnd: 0, fontSize: token.fontSize }}>{value}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'active',
      key: 'active',
      align: 'center',
      width: 120,
      render: (value: number, row) => (
        // the orgs-page 启停 precedent: the inline switch IS the status (✓/✕
        // children), optimistic flip + server re-sync; the duplicate-active
        // refusal surfaces verbatim
        <Switch
          checked={value === 1}
          disabled={!hasPerm('system:number:update')}
          aria-label={`启用 ${row.ruleType}`}
          checkedChildren="✓"
          unCheckedChildren="✕"
          onChange={(checked) => void toggleActive(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: '前缀',
      dataIndex: 'prefix',
      key: 'prefix',
      align: 'center',
      width: 120,
      render: (value: string | null) =>
        value ? <span className="mono">{value}</span> : <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    {
      title: '后缀',
      dataIndex: 'suffix',
      key: 'suffix',
      align: 'center',
      width: 120,
      render: (value: string | null) =>
        value ? <span className="mono">{value}</span> : <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    { title: '编号长度', dataIndex: 'serialLength', key: 'serialLength', width: 120, align: 'center' },
    {
      title: '格式',
      dataIndex: 'isStamp',
      key: 'isStamp',
      align: 'center',
      width: 160,
      render: (value: number, row) =>
        value === 1 ? (
          <span style={{ color: token.colorTextTertiary }}>{row.stamp}</span>
        ) : (
          <span style={{ color: token.colorTextTertiary }}>不带</span>
        ),
    },
    {
      title: '示例',
      dataIndex: 'sample',
      key: 'sample',
      width: 200,
      render: (_: unknown, row) => <span className="mono">{composeSample(row.prefix, row.stamp, row.serialLength, row.suffix, row.isStamp === 1)}</span>,
    },
    {
      title: '当前编号',
      dataIndex: 'serialCur',
      key: 'serialCur',
      width: 160,
      render: (_: number, row) => <span className="mono">{paddedSerial(row)}</span>,
    },
    {
      // the flex column: absorbs the container's leftover space under
      // tableLayout=fixed so every fixed column renders its hint verbatim
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      render: (value: string | null) =>
        value ? <span style={{ color: token.colorTextSecondary }}>{value}</span> : <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
  ]

  return (
    <ConsolePage>
      {contextHolder}
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            hasPerm('system:number:create') && <ConsoleAddButton label="新建规则" onClick={openAdd} />
          }
        />
        <ConsoleTable<NumberRule>
          rowKey={(row) => row.id}
          loading={loading}
          columns={columns}
          dataSource={rows}
          onRefresh={() => void load({ pageNum, pageSize })}
          actions={{
            width: 130,
            render: (row) => (
              <>
                {hasPerm('system:number:update') && (
                  <ConsoleAction label="编辑" onClick={() => openEdit(row)} />
                )}
                {hasPerm('system:number:delete') && (
                  <Popconfirm
                    title={`确定删除规则“${row.ruleType}”？`}
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
        width={520}
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
        <Form form={form} layout="vertical" disabled={saving}>
          {/* 规则类型: the create drawer offers the platform FAMILY select (the
              number-rule-unification ruling - one page hosts every family); the edit
              echo stays a plain label row (the 2026-11-16 ruling - info echoes merge
              into the label, no Form.Item control slot) because the family is
              immutable after create. */}
          {editing ? (
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
                {editing.ruleType}
              </Tag>
            </div>
          ) : (
            <Form.Item name="ruleType" label="规则类型" rules={[{ required: true, message: '请选择规则类型' }]}>
              <Select
                options={createTypeOptions}
                notFoundContent={typeItems.length === 0 ? '编号类型字典未配置' : undefined}
              />
            </Form.Item>
          )}
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="prefix"
                label="前缀"
                rules={[{ pattern: /^[A-Za-z0-9]{0,8}$/, message: '前缀仅支持 1-8 位字母或数字' }]}
              >
                <Input maxLength={8} placeholder="如 IM" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="suffix"
                label="后缀"
                rules={[{ pattern: /^[A-Za-z0-9]{0,8}$/, message: '后缀仅支持 1-8 位字母或数字' }]}
              >
                <Input maxLength={8} placeholder="可空" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="serialLength"
            label="编号长度"
            rules={[
              { required: true, message: '编号长度不能为空' },
              // the ONLY bounds here: an InputNumber min/max prop would clamp the
              // typed value on blur and the field error could never surface
              // (web-system-console § Number Rule Form and Delete)
              { type: 'number', min: 1, max: 10, message: '编号长度须在 1–10 之间' },
            ]}
          >
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          {/* the switch-row pattern (exam-item 常用项目 precedent, user ruling
              2026-11-19): a lone boolean switch never gets a standalone Form.Item
              row - label pinned left, switch pinned right, one flex row */}
          <div
            data-item-common-row
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: token.marginLG,
            }}
          >
            <label htmlFor="isStamp">是否带日期/时间戳</label>
            <Form.Item name="isStamp" valuePropName="checked" noStyle>
              <Switch id="isStamp" />
            </Form.Item>
          </div>
          {isStamp ? (
            <Form.Item
              name="stamp"
              label="格式"
              rules={[{ required: true, message: '请选择格式' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="请选择格式"
                notFoundContent={stampItems.length === 0 ? '格式字典未配置' : undefined}
                options={stampOptions}
              />
            </Form.Item>
          ) : null}
          {editing ? (
            // the drawer's status switch sits ABOVE 备注 (user trial 2026-11-19);
            // flipping it saves through the full-surface PUT, the A0714
            // duplicate-active refusal surfaces verbatim
            <div
              data-item-common-row
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: token.marginLG,
              }}
            >
              <label htmlFor="ruleActive">状态</label>
              <Form.Item name="active" valuePropName="checked" noStyle>
                <Switch id="ruleActive" />
              </Form.Item>
            </div>
          ) : null}
          <Form.Item name="remark" label="备注">
            <TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
          <ConsoleFormHint>
            {`号码形如 ${composeSample(prefix, stamp, serialLength, suffix, Boolean(isStamp))}（${isStamp ? stamp ?? '待选择格式' : '仅编号'} + ${serialLength ?? 4} 位编号）`}
          </ConsoleFormHint>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

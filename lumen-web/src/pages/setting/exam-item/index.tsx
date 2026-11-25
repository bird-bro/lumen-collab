import { Button, Col, Drawer, Form, Input, InputNumber, Popconfirm, Row, Select, Switch, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import {
  createExamItem,
  deleteExamItem,
  getExamItem,
  pageExamItems,
  updateExamItem,
} from '@/api/exam-item'
import { listBodyGroups } from '@/api/exam-body-group'
import { listExamBodies } from '@/api/exam-body'
import { listExamMethods } from '@/api/exam-method'
import { ApiError } from '@/api/client'
import type {
  BodyTreeNode,
  ExamBodyGroup,
  ExamItem,
  ExamMethod,
} from '@/api/types'
import { dictItemsById, MODALITY_DICT_ID } from '@/utils/dict-items'
import type { DictItem } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePage,
  ConsolePanel,
  ConsolePagination,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
} from '@/pages/system/console-shell'

/**
 * 检查项目 page (exam-setting-web task 5.1; spec delta `web-system-console`
 * § Exam Item Page). The filter bar (检查类型 / 部位分组 / 关键词 — the 常用项目
 * toggle retired by exam-item-polish-web) pages server-side; the table renders the
 * SERVER-RESOLVED display names (bodyName/groupName/methodNames - D2, no client
 * joins) plus the ellipsized 备注 column. The 2026-11-17 list polish
 * (exam-item-table-polish-web): 项目名称/项目编码 collapse into ONE 项目 cell (the
 * in-hospital code stacks on the SECOND line in the tertiary small font - the
 * deps 科室 idiom), the external column reads 对照码,
 * 诊断计费数量 reads 计费数量, the count/score columns center, and 创建时间 leaves
 * the list (initialization-imported catalog data reads no create time) - the drawer
 * keeps the list. The 2026-11-17 drawer review re-orders the form: 编码 (now
 * REQUIRED) → 名称 (60-char cap) lead, 对照码 moves BELOW 检查方法, 排序 takes its
 * own row, the numeric band narrows to 单价/折扣/计费数量 (3-up), and 常用项目
 * closes BELOW 备注 (label left / switch right; round-4 revert). The ops column rides the shell
 * chrome: delete glyph on 删除 + the refresh affordance in the 操作 header.
 * The drawer's 部位分组 → 检查部位 cascade
 * reloads the body options and CLEARS the stale body on group change (the spec
 * scenario); 检查方法 is a multi-select bound to the ID array (the join table, not
 * RCP's comma string); the 常用项目 flag is an option line (menus 是否显示 pattern,
 * 2026-11-17 amendment - label + switch on one row between 检查方法 and the numeric
 * bands); the tail bands the small fields with real labels — 排序 joins
 * 单价/折扣/诊断计费数量 as a 4-up band, 诊断得分/审核得分 ride a labeled band
 * (留空 = 不计), 备注 closes the form.
 */

interface ItemFormValues {
  code?: string
  externalCode?: string
  deviceDesc?: string
  exam: string
  groupId?: number
  bodyId?: number
  methodIds?: number[]
  sort?: number | null
  price?: number | null
  rate?: number | null
  tag?: number | null
  scoreRep?: number | null
  scoreAud?: number | null
  isCommon?: boolean
  remark?: string
}

export default function ExamItemPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [modalityOpts, setModalityOpts] = useState<DictItem[]>([])
  const [modality, setModality] = useState<string>('')
  const [groupId, setGroupId] = useState<number | undefined>(undefined)
  const [keyword, setKeyword] = useState('')

  const [groups, setGroups] = useState<ExamBodyGroup[]>([])
  const [rows, setRows] = useState<ExamItem[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<ExamItem | null>(null)
  const [saving, setSaving] = useState(false)
  // The drawer's OWN filter state (independent from the page bar).
  const [drawerGroupId, setDrawerGroupId] = useState<number | undefined>(undefined)
  const [bodyOptions, setBodyOptions] = useState<BodyTreeNode[]>([])
  const [methodOptions, setMethodOptions] = useState<ExamMethod[]>([])
  const [form] = Form.useForm<ItemFormValues>()

  const load = useCallback(
    async (query: {
      modality: string
      groupId?: number
      keyword?: string
      pageNum: number
      pageSize: number
    }) => {
      setLoading(true)
      try {
        const page = await pageExamItems(query)
        setRows(page.records)
        setTotal(page.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '项目列表加载失败')
      } finally {
        setLoading(false)
      }
    },
    [messageApi],
  )

  useEffect(() => {
    let alive = true
    dictItemsById(MODALITY_DICT_ID)
      .then((items) => {
        if (!alive) return
        setModalityOpts(items)
        if (items.length > 0) setModality(items[0].value ?? '')
      })
      .catch(() => {
        if (alive) messageApi.error('检查类型字典加载失败')
      })
    return () => {
      alive = false
    }
  }, [messageApi])

  // Group options follow the page's modality (the cascade filter source).
  useEffect(() => {
    if (!modality) return
    let alive = true
    listBodyGroups(modality)
      .then((rows_) => {
        if (alive) setGroups(rows_)
      })
      .catch(() => {
        if (alive) setGroups([])
      })
    setGroupId(undefined)
    setPageNum(1)
    return () => {
      alive = false
    }
  }, [modality])

  useEffect(() => {
    if (modality) {
      void load({
        modality,
        groupId,
        keyword: keyword || undefined,
        pageNum,
        pageSize,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modality, groupId, pageNum, pageSize])

  const search = () => {
    setPageNum(1)
    void load({
      modality,
      groupId,
      keyword: keyword || undefined,
      pageNum: 1,
      pageSize,
    })
  }

  // ===================== drawer cascade =====================

  const openAdd = () => {
    setEditing(null)
    setDrawerGroupId(undefined)
    setBodyOptions([])
    form.resetFields()
    setDrawerOpen(true)
    void listExamMethods(modality)
      .then((methods) => setMethodOptions(methods))
      .catch(() => setMethodOptions([]))
  }

  const openEdit = async (row: ExamItem) => {
    try {
      // The detail echo carries the same display-resolved shape; use it so the
      // drawer edits the freshest server state.
      const detail = await getExamItem(row.id)
      setEditing(detail)
      // Load the FULL forest so the edit drawer can both show the current body and
      // derive its group (the detail carries bodyId but not groupId).
      const forest = await listExamBodies(detail.modality).catch(() => [])
      const flat = forest.flatMap((node) => [node, ...node.children])
      const currentNode = flat.find((node) => node.id === detail.bodyId)
      setBodyOptions(forest)
      setDrawerGroupId(currentNode?.groupId)
      form.setFieldsValue({
        code: detail.code ?? undefined,
        externalCode: detail.externalCode ?? undefined,
        deviceDesc: detail.deviceDesc ?? undefined,
        exam: detail.exam,
        groupId: currentNode?.groupId,
        bodyId: detail.bodyId,
        methodIds: detail.methodIds ?? [],
        sort: detail.sort,
        price: detail.price,
        rate: detail.rate,
        tag: detail.tag,
        scoreRep: detail.scoreRep,
        scoreAud: detail.scoreAud,
        isCommon: detail.isCommon,
        remark: detail.remark ?? undefined,
      })
      setDrawerOpen(true)
      void listExamMethods(detail.modality)
        .then((methods) => setMethodOptions(methods))
        .catch(() => setMethodOptions([]))
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '项目详情加载失败')
    }
  }

  /** The drawer's group select changed: reload body options and CLEAR the stale body. */
  const onDrawerGroupChange = (value: number) => {
    setDrawerGroupId(value)
    form.setFieldValue('bodyId', undefined)
    setBodyOptions([])
    void listExamBodies(modality, value)
      .then((forest) => setBodyOptions(forest))
      .catch(() => setBodyOptions([]))
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      if (!values.bodyId) {
        messageApi.warning('请选择检查部位')
        return
      }
      setSaving(true)
      if (editing) {
        await updateExamItem(editing.id, {
          modality: editing.modality,
          exam: values.exam,
          code: values.code ?? null,
          externalCode: values.externalCode ?? null,
          deviceDesc: values.deviceDesc?.trim() || null,
          bodyId: values.bodyId,
          methodIds: values.methodIds ?? [],
          price: values.price ?? null,
          rate: values.rate ?? null,
          tag: values.tag ?? null,
          scoreRep: values.scoreRep ?? null,
          scoreAud: values.scoreAud ?? null,
          isCommon: values.isCommon ?? false,
          sort: values.sort ?? null,
          enable: editing.enable,
          remark: values.remark ?? null,
        })
        messageApi.success('修改成功')
      } else {
        await createExamItem({
          modality,
          exam: values.exam,
          code: values.code ?? null,
          externalCode: values.externalCode ?? null,
          deviceDesc: values.deviceDesc?.trim() || null,
          bodyId: values.bodyId,
          methodIds: values.methodIds ?? [],
          price: values.price ?? null,
          rate: values.rate ?? null,
          tag: values.tag ?? null,
          scoreRep: values.scoreRep ?? null,
          scoreAud: values.scoreAud ?? null,
          isCommon: values.isCommon ?? false,
          sort: values.sort ?? null,
          enable: 1,
          remark: values.remark ?? null,
        })
        messageApi.success('添加成功')
      }
      setDrawerOpen(false)
      await load({
        modality,
        groupId,
        keyword: keyword || undefined,
        pageNum,
        pageSize,
      })
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

  const remove = async (row: ExamItem) => {
    try {
      await deleteExamItem(row.id)
      messageApi.success('删除成功')
      await load({
        modality,
        groupId,
        keyword: keyword || undefined,
        pageNum,
        pageSize,
      })
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const columns: TableColumnsType<ExamItem> = [
    {
      // 项目 (2026-11-17 header polish; user review - the cell STACKS two lines
      // like the deps 科室 / roles 创建人 idiom): the name truncates on the first
      // line, the in-hospital 编码 rides the SECOND line in the tertiary small
      // font; null codes render the name alone (no empty second line). No
      // column-level ellipsis - the spans truncate individually.
      title: '项目',
      dataIndex: 'exam',
      key: 'exam',
      render: (_, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {row.exam}
          </span>
          {row.code ? (
            <span
              style={{
                fontSize: token.fontSizeSM,
                lineHeight: '18px',
                color: token.colorTextTertiary,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {row.code}
            </span>
          ) : null}
        </div>
      ),
    },
    {
      title: '对照码',
      dataIndex: 'externalCode',
      key: 'externalCode',
      render: (externalCode: string | null) =>
        externalCode ? <span style={{ color: token.colorTextTertiary }}>{externalCode}</span> : '—',
    },
    {
      // 设备描述 (exam-catalog-device-fields): the MWL-facing Latin text; blank
      // rows show the em-dash.
      title: '设备描述',
      dataIndex: 'deviceDesc',
      key: 'deviceDesc',
      ellipsis: true,
      render: (deviceDesc: string | null) =>
        deviceDesc ? <span style={{ color: token.colorTextTertiary }}>{deviceDesc}</span> : '—',
    },
    { title: '部位', dataIndex: 'bodyName', key: 'bodyName', ellipsis: true },
    {
      title: '检查方法',
      dataIndex: 'methodNames',
      key: 'methodNames',
      width: 160,
      ellipsis: true,
      render: (names: string) =>
        names
          ? names.split(',').map((name) => (
              <Tag key={name} style={{ fontSize: token.fontSize }}>
                {name}
              </Tag>
            ))
          : '—',
    },
    {
      title: '单价（元）',
      dataIndex: 'price',
      key: 'price',
      width: 110,
      align: 'right',
      render: (price: number | null) =>
        price != null ? price.toFixed(2) : '—',
    },
    {
      title: '折扣',
      dataIndex: 'rate',
      key: 'rate',
      width: 100,
      align: 'right',
      render: (rate: number | null) => (rate != null ? rate.toFixed(2) : '—'),
    },
    // Column widths (2026-11-17 review round 3): 单价 110, the rest of the
    // numeric band unified at 100 (house band 70-120) - 备注/项目 keep the flex
    // space. 检查方法 pins 160 (2026-11-18, user direct: was flex; Tags wrap
    // within the width).
    { title: '计费数量', dataIndex: 'tag', key: 'tag', width: 100, align: 'center', render: (tag: number | null) => tag ?? '—' },
    {
      title: '诊断得分',
      dataIndex: 'scoreRep',
      key: 'scoreRep',
      width: 100,
      align: 'center',
      render: (score: number | null) => score ?? '—',
    },
    {
      title: '审核得分',
      dataIndex: 'scoreAud',
      key: 'scoreAud',
      width: 100,
      align: 'center',
      render: (score: number | null) => score ?? '—',
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      ellipsis: true,
      render: (remark: string | null) => remark || '—',
    },
  ]

  return (
    <ConsolePage>
      {contextHolder}
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            hasPerm('setting:item:create') && (
              <ConsoleAddButton label="新建项目" onClick={openAdd} />
            )
          }
        >
          <Select
            aria-label="检查类型"
            style={{ width: 130 }}
            value={modality || undefined}
            onChange={(value) => setModality(value)}
            options={modalityOpts.map((item) => ({ value: item.value, label: item.value }))}
          />
          <Select
            aria-label="部位分组"
            style={{ width: 150 }}
            allowClear
            placeholder="部位分组"
            value={groupId}
            onChange={(value) => setGroupId(value)}
            options={groups.map((group) => ({ value: group.id, label: group.name }))}
          />
          <Input
            aria-label="关键词"
            style={{ width: 200 }}
            placeholder="名称 / 编码"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={search}
          />
          <ConsoleQueryButton onClick={search} />
        </ConsoleToolRow>
        <ConsoleTable<ExamItem>
          rowKey={(row) => row.id}
          loading={loading}
          columns={columns}
          dataSource={rows}
          // Shared ops chrome (2026-11-17 review): the verbs move onto the
          // shell's action column - 删除 carries the delete glyph (it rode
          // ConsoleAction's edit-glyph default) and the 操作 header gains the
          // right-edge refresh affordance re-running the current page query.
          onRefresh={() =>
            void load({ modality, groupId, keyword: keyword || undefined, pageNum, pageSize })
          }
          actions={{
            width: 130,
            render: (row) => (
              <>
                {hasPerm('setting:item:update') && (
                  <ConsoleAction label="编辑" onClick={() => void openEdit(row)} />
                )}
                {hasPerm('setting:item:delete') && (
                  <Popconfirm title={`确定删除项目“${row.exam}”？`} onConfirm={() => void remove(row)}>
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
        title={editing ? '编辑项目' : '新建项目'}
        width={560}
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
          {/* 检查类型 echo: plain label row (2026-11-16 ruling - info echoes
              merge into the label, no Form.Item control slot). */}
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
            检查类型：
            <Tag
              style={{ marginInlineEnd: 0, flex: '0 0 auto', fontSize: token.fontSize, fontWeight: 600 }}
            >
              {editing ? editing.modality : modality}
            </Tag>
          </div>
          {/* 编码 REQUIRED (2026-11-17 drawer review): the in-hospital code is
              the operational key (modality-scoped uniqueness + keyword search);
              the label shortens with the list headers. */}
          <Form.Item
            name="code"
            label="编码"
            rules={[{ required: true, message: '编码不能为空' }]}
          >
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item
            name="exam"
            label="名称"
            rules={[{ required: true, message: '名称不能为空' }]}
          >
            <Input.TextArea maxLength={60} rows={2} showCount />
          </Form.Item>
          <Form.Item name="groupId" label="部位分组" rules={[{ required: true, message: '请选择部位分组' }]}>
            <Select
              aria-label="部位分组"
              placeholder="请选择部位分组"
              onChange={(value) => onDrawerGroupChange(value)}
              options={groups.map((group) => ({ value: group.id, label: group.name }))}
            />
          </Form.Item>
          <Form.Item name="bodyId" label="检查部位" rules={[{ required: true, message: '请选择检查部位' }]}>
            <Select
              aria-label="检查部位"
              placeholder={drawerGroupId ? '请选择检查部位' : '请先选分组'}
              disabled={!drawerGroupId}
              options={bodyOptions.flatMap((node) => [
                { value: node.id, label: node.name },
                ...node.children.map((child) => ({ value: child.id, label: `　${child.name}` })),
              ])}
            />
          </Form.Item>
          <Form.Item name="methodIds" label="检查方法">
            <Select
              aria-label="检查方法"
              mode="multiple"
              allowClear
              placeholder="点击选择（可多选）"
              options={methodOptions.map((method) => ({ value: method.id, label: method.name }))}
            />
          </Form.Item>
          {/* 对照码 (2026-11-17 drawer review): renamed and moved BELOW 检查方法 -
              the external mapping anchor reads after the clinical fields, no
              longer paired with the in-hospital code (exam-item-external-code-web
              pairing retired). Optional; the backend carries NO uniqueness
              (打包计费 multi-to-one). */}
          <Form.Item name="externalCode" label="对照码">
            <Input maxLength={32} />
          </Form.Item>
          {/* 设备描述 (exam-catalog-device-fields): the MWL-facing Latin text for
              charset-strict devices. */}
          <Form.Item name="deviceDesc" label="设备描述">
            <Input maxLength={128} placeholder="拉丁文本（供不支持中文的设备）" />
          </Form.Item>
          {/* 排序 on its own row (2026-11-17 drawer review - it left the numeric
              band); full-width per the group-drawer precedent. */}
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          {/* Banded tail (2026-11-17 drawer review): the numeric band narrows to
              单价/折扣/计费数量 (3-up, real labels); 诊断得分/审核得分 keep their
              labeled band (留空 = 不计). */}
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="price" label="单价（元）">
                <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="rate" label="折扣">
                <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="tag" label="计费数量">
                <InputNumber min={1} step={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="scoreRep" label="诊断得分" tooltip="留空 = 不计">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="scoreAud" label="审核得分" tooltip="留空 = 不计">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={4} maxLength={255} style={{ minHeight: 32 }} />
          </Form.Item>
          {/* 常用项目 closes the form BELOW 备注 (round 4 revert - the round-3
              spot above 备注 read worse): label pinned left, switch pinned right
              (space-between) so the flag reads across the full row width. */}
          <div
            data-item-common-row
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
          >
            <label htmlFor="isCommon">常用项目</label>
            <Form.Item name="isCommon" valuePropName="checked" initialValue={false} noStyle>
              <Switch id="isCommon" />
            </Form.Item>
          </div>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

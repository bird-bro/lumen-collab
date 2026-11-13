import { Button, Checkbox, Drawer, Form, Input, InputNumber, Popconfirm, Select, Switch, Tag, message, theme } from 'antd'
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
import { dictItemsByName } from '@/utils/dict-items'
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
 * § Exam Item Page). The filter bar (检查类型 / 部位分组 / 常用项目 / 关键词)
 * pages server-side; the table renders the SERVER-RESOLVED display names
 * (bodyName/groupName/methodNames - D2, no client joins). The drawer's
 * 部位分组 → 检查部位 cascade reloads the body options and CLEARS the stale body
 * on group change (the spec scenario); 检查方法 is a multi-select bound to the
 * ID array (the join table, not RCP's comma string); the numeric fields carry
 * the billing block + the performance-score weights (空 = 不计), and 常用项目
 * is the isCommon switch.
 */

const DICT_MODALITY = '检查类型'

interface ItemFormValues {
  code?: string
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
  const [commonOnly, setCommonOnly] = useState(false)
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
      isCommon?: boolean
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
    dictItemsByName(DICT_MODALITY)
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
        isCommon: commonOnly || undefined,
        keyword: keyword || undefined,
        pageNum,
        pageSize,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modality, groupId, commonOnly, pageNum, pageSize])

  const search = () => {
    setPageNum(1)
    void load({
      modality,
      groupId,
      isCommon: commonOnly || undefined,
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
        isCommon: commonOnly || undefined,
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
        isCommon: commonOnly || undefined,
        keyword: keyword || undefined,
        pageNum,
        pageSize,
      })
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const columns: TableColumnsType<ExamItem> = [
    { title: '项目名称', dataIndex: 'exam', key: 'exam', ellipsis: true },
    {
      title: '项目编码',
      dataIndex: 'code',
      key: 'code',
      render: (code: string | null) =>
        code ? <span style={{ color: token.colorTextTertiary }}>{code}</span> : '—',
    },
    { title: '检查部位', dataIndex: 'bodyName', key: 'bodyName', ellipsis: true },
    {
      title: '检查方法',
      dataIndex: 'methodNames',
      key: 'methodNames',
      ellipsis: true,
      render: (names: string) =>
        names
          ? names.split(',').map((name) => <Tag key={name}>{name}</Tag>)
          : '—',
    },
    {
      title: '单价（元）',
      dataIndex: 'price',
      key: 'price',
      align: 'right',
      render: (price: number | null) =>
        price != null ? price.toFixed(2) : '—',
    },
    {
      title: '折扣',
      dataIndex: 'rate',
      key: 'rate',
      align: 'right',
      render: (rate: number | null) => (rate != null ? rate.toFixed(2) : '—'),
    },
    { title: '诊断计费数量', dataIndex: 'tag', key: 'tag', align: 'right', render: (tag: number | null) => tag ?? '—' },
    {
      title: '诊断得分',
      dataIndex: 'scoreRep',
      key: 'scoreRep',
      align: 'right',
      render: (score: number | null) => score ?? '—',
    },
    {
      title: '审核得分',
      dataIndex: 'scoreAud',
      key: 'scoreAud',
      align: 'right',
      render: (score: number | null) => score ?? '—',
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, row) => (
        <>
          {hasPerm('setting:item:update') && (
            <ConsoleAction label="编辑" onClick={() => void openEdit(row)} />
          )}
          {hasPerm('setting:item:delete') && (
            <Popconfirm title={`确定删除项目“${row.exam}”？`} onConfirm={() => void remove(row)}>
              <ConsoleAction label="删除" danger onClick={() => undefined} />
            </Popconfirm>
          )}
        </>
      ),
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
          <Checkbox
            checked={commonOnly}
            onChange={(event) => {
              setCommonOnly(event.target.checked)
              setPageNum(1)
            }}
          >
            常用项目
          </Checkbox>
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
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item label="检查类型">
            <Tag>{editing ? editing.modality : modality}</Tag>
          </Form.Item>
          <Form.Item name="code" label="项目编码">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item
            name="exam"
            label="项目名称"
            rules={[{ required: true, message: '项目名称不能为空' }]}
          >
            <Input.TextArea maxLength={50} rows={2} showCount />
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
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="计费与绩效权重（空 = 不计）">
            <Input.Group compact>
              <Form.Item name="price" noStyle>
                <InputNumber min={0} step={0.01} placeholder="单价（元）" style={{ width: '25%' }} />
              </Form.Item>
              <Form.Item name="rate" noStyle>
                <InputNumber min={0} max={1} step={0.01} placeholder="折扣" style={{ width: '25%' }} />
              </Form.Item>
              <Form.Item name="tag" noStyle>
                <InputNumber min={1} step={1} placeholder="诊断计费数量" style={{ width: '25%' }} />
              </Form.Item>
            </Input.Group>
          </Form.Item>
          <Form.Item label="绩效得分权重">
            <Input.Group compact>
              <Form.Item name="scoreRep" noStyle>
                <InputNumber min={0} placeholder="诊断得分" style={{ width: '50%' }} />
              </Form.Item>
              <Form.Item name="scoreAud" noStyle>
                <InputNumber min={0} placeholder="审核得分" style={{ width: '50%' }} />
              </Form.Item>
            </Input.Group>
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item label="常用项目" valuePropName="checked" name="isCommon" initialValue={false}>
            <Switch />
          </Form.Item>
          <Button type="primary" block loading={saving} onClick={() => void save()}>
            保存
          </Button>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

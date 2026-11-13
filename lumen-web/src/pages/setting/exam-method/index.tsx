import { Button, Drawer, Form, Input, InputNumber, Popconfirm, Select, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { createExamMethod, deleteExamMethod, pageExamMethods, updateExamMethod } from '@/api/exam-method'
import { ApiError } from '@/api/client'
import type { ExamMethod, ExamMethodPageQuery } from '@/api/types'
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
 * 检查方法 page (exam-setting-web task 3.1; spec delta `web-system-console`
 * § Exam Method Page): the 检查类型 filter loads from the shared dict 检查类型
 * (resolved by catalog name server-side - the V27 ruling), the 名称/编码 query
 * types collapse into ONE keyword box matched against both columns, and the
 * table pages server-side (newest first). The drawer create/edits one method;
 * a delete refused for live item bindings surfaces the backend's count message
 * verbatim and the row stays.
 *
 * Permission gating: `setting:method:list` gates the page surface (the route
 * itself is menu-granted); each mutation affordance answers to its own F-node
 * string (`setting:method:create/update/delete`).
 */

const DICT_MODALITY = '检查类型'

interface MethodFormValues {
  name: string
  code?: string
  sort?: number | null
}

export default function ExamMethodPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  // 检查类型 options (dict 检查类型) + the filter state (defaults to the first value).
  const [modalityOpts, setModalityOpts] = useState<DictItem[]>([])
  const [modality, setModality] = useState<string>('')
  const [keyword, setKeyword] = useState('')

  const [rows, setRows] = useState<ExamMethod[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<ExamMethod | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<MethodFormValues>()

  const load = useCallback(
    async (query: ExamMethodPageQuery) => {
      setLoading(true)
      try {
        const page = await pageExamMethods(query)
        setRows(page.records)
        setTotal(page.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '方法列表加载失败')
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

  useEffect(() => {
    if (modality) {
      void load({ modality, keyword: keyword || undefined, pageNum, pageSize })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modality, pageNum, pageSize])

  const search = () => {
    setPageNum(1)
    void load({ modality, keyword: keyword || undefined, pageNum: 1, pageSize })
  }

  const openAdd = () => {
    setEditing(null)
    form.resetFields()
    setDrawerOpen(true)
  }

  const openEdit = (row: ExamMethod) => {
    setEditing(row)
    form.setFieldsValue({ name: row.name, code: row.code ?? undefined, sort: row.sort })
    setDrawerOpen(true)
  }

  const save = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      if (editing) {
        await updateExamMethod(editing.id, {
          modality: editing.modality,
          name: values.name,
          code: values.code ?? null,
          sort: values.sort ?? null,
          enable: editing.enable,
        })
        messageApi.success('修改成功')
      } else {
        await createExamMethod({
          modality,
          name: values.name,
          code: values.code ?? null,
          sort: values.sort ?? null,
          enable: 1,
        })
        messageApi.success('添加成功')
      }
      setDrawerOpen(false)
      await load({ modality, keyword: keyword || undefined, pageNum, pageSize })
    } catch (error) {
      if (error instanceof ApiError) {
        // Backend refusals (duplicate code A0400 etc.) surface verbatim.
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

  const remove = async (row: ExamMethod) => {
    try {
      await deleteExamMethod(row.id)
      messageApi.success('删除成功')
      await load({ modality, keyword: keyword || undefined, pageNum, pageSize })
    } catch (error) {
      // The guard's count message ("该方法仍被 N 个检查项目使用…") surfaces verbatim.
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const columns: TableColumnsType<ExamMethod> = [
    { title: '方法名称', dataIndex: 'name', key: 'name', ellipsis: true },
    {
      title: '方法编码',
      dataIndex: 'code',
      key: 'code',
      render: (code: string | null) => (code ? <span style={{ color: token.colorTextTertiary }}>{code}</span> : '—'),
    },
    {
      title: '检查类型',
      dataIndex: 'modality',
      key: 'modality',
      width: 110,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, row) => (
        <>
          {hasPerm('setting:method:update') && (
            <ConsoleAction label="编辑" onClick={() => openEdit(row)} />
          )}
          {hasPerm('setting:method:delete') && (
            <Popconfirm
              title={`确定删除方法“${row.name}”？`}
              onConfirm={() => void remove(row)}
            >
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
            hasPerm('setting:method:create') && (
              <ConsoleAddButton label="新建方法" onClick={openAdd} />
            )
          }
        >
          <Select
            aria-label="检查类型"
            style={{ width: 140 }}
            value={modality || undefined}
            onChange={(value) => {
              setModality(value)
              setKeyword('')
              setPageNum(1)
            }}
            options={modalityOpts.map((item) => ({ value: item.value, label: item.value }))}
          />
          <Input
            aria-label="关键词"
            style={{ width: 220 }}
            placeholder="请输入关键词"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={search}
          />
          <ConsoleQueryButton onClick={search} />
        </ConsoleToolRow>
        <ConsoleTable<ExamMethod>
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
        title={editing ? '编辑方法' : '新建方法'}
        width={480}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item label="检查类型">
            <Tag>{editing ? editing.modality : modality}</Tag>
          </Form.Item>
          <Form.Item
            name="name"
            label="方法名称"
            rules={[{ required: true, message: '方法名称不能为空' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="code" label="方法编码">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Button
            type="primary"
            block
            loading={saving}
            onClick={() => void save()}
          >
            保存
          </Button>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

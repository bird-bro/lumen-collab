import { Button, Drawer, Form, Input, InputNumber, Popconfirm, Select, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createBodyGroup,
  deleteBodyGroup,
  listBodyGroups,
  updateBodyGroup,
} from '@/api/exam-body-group'
import {
  createExamBody,
  deleteExamBody,
  listExamBodies,
  updateExamBody,
} from '@/api/exam-body'
import { ApiError } from '@/api/client'
import type {
  BodyTreeNode,
  ExamBody,
  ExamBodyGroup,
} from '@/api/types'
import { dictItemsByName } from '@/utils/dict-items'
import type { DictItem } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePage,
  ConsolePanel,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
  ConsoleWorkArea,
} from '@/pages/system/console-shell'

/**
 * 检查部位 page (exam-setting-web task 4.1; spec delta `web-system-console`
 * § Exam Body Page with In-Page Group Management). Layout (coordination ruling 9 -
 * one menu instead of two): LEFT column manages the modality's 部位分组 in-page
 * (the group table carries no code column - the NAME is the operational key),
 * RIGHT table lists the selected group's bodies flattened from the server's
 * up_id forest with the 上级部位 column resolved client-side from the same
 * payload. The body drawer's parent select stays WITHIN the selected group
 * (cross-group parents reject A0400 server-side anyway); a group delete the
 * backend refuses (live bodies) surfaces the count message verbatim and the
 * group stays. Permission gating: `setting:body:*` covers BOTH resources (the
 * one-resource-family ruling).
 */

const DICT_MODALITY = '检查类型'

interface GroupFormValues {
  name: string
  sort?: number | null
}

interface BodyFormValues {
  upId?: number | null
  name: string
  code?: string
  sort?: number | null
}

export default function ExamBodyPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [modalityOpts, setModalityOpts] = useState<DictItem[]>([])
  const [modality, setModality] = useState<string>('')
  const [modalityOpen, setModalityOpen] = useState(false)

  // LEFT: the modality's groups + the selected one.
  const [groups, setGroups] = useState<ExamBodyGroup[]>([])
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null)
  const [groupDrawerOpen, setGroupDrawerOpen] = useState(false)
  const [editingGroup, setEditingGroup] = useState<ExamBodyGroup | null>(null)
  const [groupSaving, setGroupSaving] = useState(false)
  const [groupForm] = Form.useForm<GroupFormValues>()

  // RIGHT: the bodies of the selected group (server forest → flattened rows).
  const [bodies, setBodies] = useState<BodyTreeNode[]>([])
  const [loading, setLoading] = useState(false)
  const [bodyDrawerOpen, setBodyDrawerOpen] = useState(false)
  const [editingBody, setEditingBody] = useState<ExamBody | null>(null)
  const [bodySaving, setBodySaving] = useState(false)
  const [bodyForm] = Form.useForm<BodyFormValues>()

  const loadGroups = useCallback(
    async (target: string) => {
      try {
        const rows = await listBodyGroups(target)
        setGroups(rows)
        setSelectedGroupId((current) =>
          current !== null && rows.some((row) => row.id === current)
            ? current
            : rows.length > 0
              ? rows[0].id
              : null,
        )
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '分组列表加载失败')
      }
    },
    [messageApi],
  )

  const loadBodies = useCallback(
    async (target: string, groupId: number | null) => {
      setLoading(true)
      try {
        const forest = await listExamBodies(target, groupId ?? undefined)
        setBodies(forest)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '部位列表加载失败')
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
    if (modality && modalityOpen) {
      void loadGroups(modality)
      setModalityOpen(false)
    }
  }, [modality, modalityOpen, loadGroups])

  useEffect(() => {
    if (modality) {
      void loadGroups(modality)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modality])

  useEffect(() => {
    if (modality) {
      void loadBodies(modality, selectedGroupId)
    }
  }, [modality, selectedGroupId, loadBodies])

  // The forest flattened for the table; the parent NAME resolves from the same
  // payload (no extra request, D2 spirit).
  const bodyRows = useMemo(() => {
    const flat: { row: ExamBody; parentName: string | null }[] = []
    const walk = (nodes: BodyTreeNode[], parentName: string | null) => {
      for (const node of nodes) {
        flat.push({ row: node, parentName })
        walk(node.children, node.name)
      }
    }
    walk(bodies, null)
    return flat
  }, [bodies])

  const selectedGroup = groups.find((group) => group.id === selectedGroupId) ?? null

  // ===================== group CRUD =====================

  const openGroupAdd = () => {
    setEditingGroup(null)
    groupForm.resetFields()
    setGroupDrawerOpen(true)
  }

  const openGroupEdit = (group: ExamBodyGroup) => {
    setEditingGroup(group)
    groupForm.setFieldsValue({ name: group.name, sort: group.sort })
    setGroupDrawerOpen(true)
  }

  const saveGroup = async () => {
    try {
      const values = await groupForm.validateFields()
      setGroupSaving(true)
      if (editingGroup) {
        await updateBodyGroup(editingGroup.id, {
          modality: editingGroup.modality,
          name: values.name,
          sort: values.sort ?? null,
          enable: editingGroup.enable,
        })
        messageApi.success('修改成功')
      } else {
        await createBodyGroup({ modality, name: values.name, sort: values.sort ?? null, enable: 1 })
        messageApi.success('添加成功')
      }
      setGroupDrawerOpen(false)
      await loadGroups(modality)
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg)
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setGroupSaving(false)
    }
  }

  const removeGroup = async (group: ExamBodyGroup) => {
    try {
      await deleteBodyGroup(group.id)
      messageApi.success('删除成功')
      await loadGroups(modality)
    } catch (error) {
      // "该分组下仍有 N 个部位…" surfaces verbatim; the group stays in the column.
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  // ===================== body CRUD =====================

  const openBodyAdd = () => {
    setEditingBody(null)
    bodyForm.resetFields()
    setBodyDrawerOpen(true)
  }

  const openBodyEdit = (row: ExamBody) => {
    setEditingBody(row)
    bodyForm.setFieldsValue({
      upId: row.upId,
      name: row.name,
      code: row.code ?? undefined,
      sort: row.sort,
    })
    setBodyDrawerOpen(true)
  }

  const saveBody = async () => {
    try {
      const values = await bodyForm.validateFields()
      if (!selectedGroupId) {
        messageApi.warning('请先选择部位分组')
        return
      }
      setBodySaving(true)
      if (editingBody) {
        await updateExamBody(editingBody.id, {
          modality: editingBody.modality,
          groupId: editingBody.groupId,
          upId: values.upId ?? null,
          name: values.name,
          code: values.code ?? null,
          sort: values.sort ?? null,
          enable: editingBody.enable,
        })
        messageApi.success('修改成功')
      } else {
        await createExamBody({
          modality,
          groupId: selectedGroupId,
          upId: values.upId ?? null,
          name: values.name,
          code: values.code ?? null,
          sort: values.sort ?? null,
          enable: 1,
        })
        messageApi.success('添加成功')
      }
      setBodyDrawerOpen(false)
      await loadBodies(modality, selectedGroupId)
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg)
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setBodySaving(false)
    }
  }

  const removeBody = async (row: ExamBody) => {
    try {
      await deleteExamBody(row.id)
      messageApi.success('删除成功')
      await loadBodies(modality, selectedGroupId)
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const bodyColumns: TableColumnsType<{ row: ExamBody; parentName: string | null }> = [
    { title: '部位名称', key: 'name', render: (_, item) => item.row.name },
    {
      title: '部位编码',
      key: 'code',
      render: (_, item) =>
        item.row.code ? (
          <span style={{ color: token.colorTextTertiary }}>{item.row.code}</span>
        ) : (
          '—'
        ),
    },
    { title: '上级部位', key: 'up', render: (_, item) => item.parentName ?? '—' },
    { title: '排序', key: 'sort', render: (_, item) => item.row.sort ?? '—' },
    { title: '创建时间', key: 'time', render: (_, item) => item.row.createTime ?? '—' },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, item) => (
        <>
          {hasPerm('setting:body:update') && (
            <ConsoleAction label="编辑" onClick={() => openBodyEdit(item.row)} />
          )}
          {hasPerm('setting:body:delete') && (
            <Popconfirm title={`确定删除部位“${item.row.name}”？`} onConfirm={() => void removeBody(item.row)}>
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
      <ConsoleWorkArea
        aside={
          <ConsolePanel>
            <div style={{ padding: '10px 12px 0' }}>
              <Select
                aria-label="检查类型"
                style={{ width: '100%' }}
                value={modality || undefined}
                onDropdownVisibleChange={(open) => setModalityOpen(open)}
                onChange={(value) => {
                  setModality(value)
                  setSelectedGroupId(null)
                }}
                options={modalityOpts.map((item) => ({ value: item.value, label: item.value }))}
              />
            </div>
            <ConsoleToolRow
              trailing={
                hasPerm('setting:body:create') && (
                  <ConsoleAddButton label="新建分组" onClick={openGroupAdd} />
                )
              }
            >
              {null}
            </ConsoleToolRow>
            <div>
              {groups.map((group) => (
                <div
                  key={group.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => setSelectedGroupId(group.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') setSelectedGroupId(group.id)
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '9px 14px',
                    cursor: 'pointer',
                    borderBottom: `1px solid ${token.colorBorderSecondary}`,
                    borderLeft: `3px solid ${group.id === selectedGroupId ? token.colorPrimary : 'transparent'}`,
                    background: group.id === selectedGroupId ? token.colorPrimaryBg : undefined,
                  }}
                >
                  <span
                    style={{
                      fontWeight: group.id === selectedGroupId ? 600 : 400,
                      color: group.id === selectedGroupId ? token.colorPrimary : undefined,
                    }}
                  >
                    {group.name}
                  </span>
                  <span style={{ display: 'flex', gap: 2 }}>
                    {hasPerm('setting:body:update') && (
                      <ConsoleAction label="编辑分组" icon="edit" onClick={() => openGroupEdit(group)} />
                    )}
                    {hasPerm('setting:body:delete') && (
                      <Popconfirm title={`确定删除分组“${group.name}”？`} onConfirm={() => void removeGroup(group)}>
                        <ConsoleAction label="删除分组" icon="remove" danger onClick={() => undefined} />
                      </Popconfirm>
                    )}
                  </span>
                </div>
              ))}
            </div>
          </ConsolePanel>
        }
      >
        <ConsolePanel>
          <ConsoleToolRow
            trailing={
              hasPerm('setting:body:create') && (
                <ConsoleAddButton label="新建部位" onClick={openBodyAdd} />
              )
            }
          >
            <Tag>检查类型 {modality}</Tag>
            <Tag>{selectedGroup ? `部位分组 ${selectedGroup.name}` : '未选择分组'}</Tag>
            <ConsoleQueryButton onClick={() => void loadBodies(modality, selectedGroupId)} />
          </ConsoleToolRow>
          <ConsoleTable
            rowKey={(item) => `${item.row.id}`}
            loading={loading}
            columns={bodyColumns}
            dataSource={bodyRows}
          />
        </ConsolePanel>
      </ConsoleWorkArea>

      {/* 分组抽屉 */}
      <Drawer
        title={editingGroup ? '编辑分组' : '新建分组'}
        width={440}
        open={groupDrawerOpen}
        onClose={() => setGroupDrawerOpen(false)}
        destroyOnClose
      >
        <Form form={groupForm} layout="vertical">
          <Form.Item label="检查类型">
            <Tag>{modality}</Tag>
          </Form.Item>
          <Form.Item
            name="name"
            label="分组名称"
            rules={[{ required: true, message: '分组名称不能为空' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Button type="primary" block loading={groupSaving} onClick={() => void saveGroup()}>
            保存
          </Button>
        </Form>
      </Drawer>

      {/* 部位抽屉 */}
      <Drawer
        title={editingBody ? '编辑部位' : '新建部位'}
        width={480}
        open={bodyDrawerOpen}
        onClose={() => setBodyDrawerOpen(false)}
        destroyOnClose
      >
        <Form form={bodyForm} layout="vertical">
          <Form.Item label="检查类型">
            <Tag>{editingBody ? editingBody.modality : modality}</Tag>
          </Form.Item>
          <Form.Item name="upId" label="上级部位（可选，限同组）">
            <Select
              allowClear
              placeholder="无（作为顶层）"
              options={bodyRows
                .filter((item) => !editingBody || item.row.id !== editingBody.id)
                .map((item) => ({ value: item.row.id, label: item.row.name }))}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label="部位名称"
            rules={[{ required: true, message: '部位名称不能为空' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="code" label="部位编码">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Button type="primary" block loading={bodySaving} onClick={() => void saveBody()}>
            保存
          </Button>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

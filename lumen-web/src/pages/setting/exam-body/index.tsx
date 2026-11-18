import { Button, Drawer, Form, Input, InputNumber, Popconfirm, Select, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { Key } from 'react'
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
  ConsolePagination,
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
 * RIGHT table renders the server's up_id forest as AntD tree rows (2026-11-16
 * ruling - the hierarchy IS the parent read, no 上级部位 column) and the
 * keyword filter prunes to hits plus their ancestors. The body drawer's parent
 * select stays WITHIN the selected group
 * (cross-group parents reject A0400 server-side anyway); a group delete the
 * backend refuses (live bodies) surfaces the count message verbatim and the
 * group stays. Permission gating: `setting:body:*` covers BOTH resources (the
 * one-resource-family ruling).
 */

const DICT_MODALITY = '检查类型'

/** AntD tree-row shape: the API keeps `children` on every node, but rc-table
 * reads an EMPTY array as "has children" and renders a dead collapsed expand
 * button on the leaf - strip empties so leaves render clean. */
type BodyTreeRow = Omit<BodyTreeNode, 'children'> & { children?: BodyTreeRow[] }
const toTreeRows = (nodes: BodyTreeNode[]): BodyTreeRow[] =>
  nodes.map(({ children, ...rest }) => ({
    ...rest,
    ...(children.length > 0 ? { children: toTreeRows(children) } : {}),
  }))

interface GroupFormValues {
  name: string
  sort?: number | null
  remark?: string
}

interface BodyFormValues {
  upId?: number | null
  name: string
  code?: string
  sort?: number | null
  remark?: string
}

export default function ExamBodyPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [modalityOpts, setModalityOpts] = useState<DictItem[]>([])
  const [modality, setModality] = useState<string>('')
  const [modalityOpen, setModalityOpen] = useState(false)

  // LEFT: the modality's groups + the selected one. The list renders as a
  // dicts-style paged table (user request 2026-11-16). `listBodyGroups` has no
  // server-paging contract - it returns the full sort-ordered array and the
  // per-modality group count is small, so the pager slices client-side.
  const [groups, setGroups] = useState<ExamBodyGroup[]>([])
  const [groupsLoading, setGroupsLoading] = useState(false)
  const [groupPage, setGroupPage] = useState(1)
  const [groupPageSize, setGroupPageSize] = useState(20)
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

  // RIGHT query row: 部位 fuzzy filter (user request 2026-11-16 - the
  // 检查类型/部位分组 tags left the tool row, the left panel's selected
  // highlight IS the context). Client-side prune-to-match over the loaded
  // forest (hits keep ancestors) - no new API contract. users-page idiom:
  // draft commits on 查询/Enter.
  const [nameDraft, setNameDraft] = useState('')
  const [nameFilter, setNameFilter] = useState('')

  const loadGroups = useCallback(
    async (target: string) => {
      setGroupsLoading(true)
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
      } finally {
        setGroupsLoading(false)
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

  // Context switch (检查类型 / 部位分组) invalidates the name filter - a stale
  // keyword would read as "this group has no rows" on a fresh selection.
  useEffect(() => {
    setNameDraft('')
    setNameFilter('')
  }, [modality, selectedGroupId])

  // 查询 commits the draft keyword and refreshes the group's bodies; the
  // fuzzy match itself applies client-side over the flattened rows.
  const runQuery = () => {
    setNameFilter(nameDraft)
    void loadBodies(modality, selectedGroupId)
  }

  // Tree table (2026-11-16 ruling): the forest feeds AntD's children-tree rows
  // directly - the hierarchy IS the parent read, so the 上级部位 column went
  // away. Expansion is controlled and re-opens every level on refetch (the
  // per-group dataset is small); users may still collapse rows.
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([])
  useEffect(() => {
    const keys: Key[] = []
    const walk = (nodes: BodyTreeNode[]) => {
      for (const node of nodes) {
        // only expandable rows ride the controlled keys - a leaf forced into
        // expandedRowKeys makes AntD render a dead expand button on it
        if (node.children.length > 0) {
          keys.push(node.id)
          walk(node.children)
        }
      }
    }
    walk(bodies)
    setExpandedKeys(keys)
  }, [bodies])

  // The forest flattened ONCE for the drawer's 上级 options - the select is
  // flat; only the table renders the tree.
  const flatBodies = useMemo(() => {
    const flat: ExamBody[] = []
    const walk = (nodes: BodyTreeNode[]) => {
      for (const node of nodes) {
        flat.push(node)
        walk(node.children)
      }
    }
    walk(bodies)
    return flat
  }, [bodies])

  // Prune-to-match keyword filter: a hit keeps its ancestor chain (肺 under
  // 胸部 still shows 胸部); branches without a match drop entirely.
  const filteredBodies = useMemo(() => {
    if (!nameFilter) return bodies
    const prune = (nodes: BodyTreeNode[]): BodyTreeNode[] => {
      const kept: BodyTreeNode[] = []
      for (const node of nodes) {
        const children = prune(node.children)
        if (node.name.includes(nameFilter) || children.length > 0) {
          kept.push({ ...node, children })
        }
      }
      return kept
    }
    return prune(bodies)
  }, [bodies, nameFilter])

  const bodyTreeRows = useMemo(() => toTreeRows(filteredBodies), [filteredBodies])

  // ===================== group CRUD =====================

  const openGroupAdd = () => {
    setEditingGroup(null)
    groupForm.resetFields()
    setGroupDrawerOpen(true)
  }

  const openGroupEdit = (group: ExamBodyGroup) => {
    setEditingGroup(group)
    groupForm.setFieldsValue({ name: group.name, sort: group.sort, remark: group.remark ?? undefined })
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
          remark: values.remark ?? null,
        })
        messageApi.success('修改成功')
      } else {
        await createBodyGroup({
          modality,
          name: values.name,
          sort: values.sort ?? null,
          enable: 1,
          remark: values.remark ?? null,
        })
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
      remark: row.remark ?? undefined,
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
          remark: values.remark ?? null,
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
          remark: values.remark ?? null,
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

  // dicts-style paged group table (2026-11-16): client-side slice of the full
  // sorted array; the pager hides itself when the modality has no groups.
  const pagedGroups = groups.slice((groupPage - 1) * groupPageSize, groupPage * groupPageSize)

  const groupColumns: TableColumnsType<ExamBodyGroup> = [
    { title: '分组', key: 'name', render: (_, row) => row.name },
  ]

  const bodyColumns: TableColumnsType<BodyTreeRow> = [
    { title: '部位', key: 'name', render: (_, node) => node.name },
    {
      // 编码 header (user request 2026-11-16; the drawer label stays 部位编码),
      // centered; 创建时间 left the list (2026-11-17 - the catalogs are
      // initialization-imported data).
      title: '编码',
      key: 'code',
      align: 'center',
      render: (_, node) =>
        node.code ? (
          <span style={{ color: token.colorTextTertiary }}>{node.code}</span>
        ) : (
          '—'
        ),
    },
    // 排序 left the table (user ruling 2026-11-16 - the drawer field stays);
    // the 备注 column (user request 2026-11-16) takes its slot, prose-aligned
    // and ellipsis-truncated per the roles-page idiom.
    { title: '备注', key: 'remark', ellipsis: true, render: (_, node) => node.remark ?? '—' },
  ]

  return (
    <ConsolePage>
      {contextHolder}
      <ConsoleWorkArea
        aside={
          <ConsolePanel>
            {/* 检查类型 select shares the 新建分组 tool row (user request
                2026-11-16): one-line toolbar, select narrowed to the
                rep-template 120px instead of filling the aside. */}
            <ConsoleToolRow
              trailing={
                hasPerm('setting:body:create') && (
                  <ConsoleAddButton label="新建分组" onClick={openGroupAdd} />
                )
              }
            >
              <Select
                aria-label="检查类型"
                style={{ width: 120 }}
                value={modality || undefined}
                onDropdownVisibleChange={(open) => setModalityOpen(open)}
                onChange={(value) => {
                  setModality(value)
                  setSelectedGroupId(null)
                  setGroupPage(1)
                }}
                options={modalityOpts.map((item) => ({ value: item.value, label: item.value }))}
              />
            </ConsoleToolRow>
            <ConsoleTable<ExamBodyGroup>
              rowKey={(row) => row.id}
              columns={groupColumns}
              dataSource={pagedGroups}
              loading={groupsLoading}
              // OPS dict-page highlight-current-row: selection = the master-side
              // chrome (hairline + left bar), same as the dicts catalog list.
              selectedRowKey={selectedGroupId}
              onRow={(row) => ({
                onClick: () => setSelectedGroupId(row.id),
                style: { cursor: 'pointer' },
              })}
              actions={{
                width: 120,
                render: (row) => [
                  hasPerm('setting:body:update') ? (
                    <ConsoleAction key="edit" label="编辑分组" onClick={() => openGroupEdit(row)} />
                  ) : null,
                  hasPerm('setting:body:delete') ? (
                    <Popconfirm
                      key="delete"
                      title={`确定删除分组“${row.name}”？`}
                      onConfirm={() => void removeGroup(row)}
                    >
                      <ConsoleAction label="删除分组" icon="remove" danger onClick={() => undefined} />
                    </Popconfirm>
                  ) : null,
                ],
              }}
              onRefresh={() => void loadGroups(modality)}
            />
            <ConsolePagination
              total={groups.length}
              current={groupPage}
              pageSize={groupPageSize}
              showSizeChanger={false}
              onChange={(next, nextSize) => {
                setGroupPage(next)
                setGroupPageSize(nextSize)
              }}
              onShowSizeChange={() => setGroupPage(1)}
            />
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
            <Input
              allowClear
              style={{ width: 220 }}
              placeholder="部位"
              aria-label="部位模糊查询"
              value={nameDraft}
              onChange={(event) => setNameDraft(event.target.value)}
              onPressEnter={runQuery}
            />
            <ConsoleQueryButton onClick={runQuery} />
          </ConsoleToolRow>
          <ConsoleTable<BodyTreeRow>
            rowKey={(node) => node.id}
            loading={loading}
            columns={bodyColumns}
            dataSource={bodyTreeRows}
            expandable={{
              expandedRowKeys: expandedKeys,
              onExpandedRowsChange: (keys) => setExpandedKeys([...keys]),
            }}
            // Shared console-table chrome (2026-11-16): the 操作 verbs move off
            // the hand-rolled column onto the shell's action column - the
            // header then carries the refresh glyph and 删除 gains the delete
            // glyph (it used to ride ConsoleAction's edit default).
            onRefresh={() => void loadBodies(modality, selectedGroupId)}
            actions={{
              width: 130,
              render: (node) => (
                <>
                  {hasPerm('setting:body:update') && (
                    <ConsoleAction label="编辑" onClick={() => openBodyEdit(node)} />
                  )}
                  {hasPerm('setting:body:delete') && (
                    <Popconfirm
                      title={`确定删除部位“${node.name}”？`}
                      onConfirm={() => void removeBody(node)}
                    >
                      <ConsoleAction label="删除" icon="remove" danger onClick={() => undefined} />
                    </Popconfirm>
                  )}
                </>
              ),
            }}
          />
        </ConsolePanel>
      </ConsoleWorkArea>

      {/* 分组抽屉 */}
      <Drawer
        title={editingGroup ? '编辑分组' : '新建分组'}
        width={440}
        open={groupDrawerOpen}
        onClose={() => setGroupDrawerOpen(false)}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnClose
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setGroupDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={groupSaving} onClick={() => void saveGroup()}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={groupForm} layout="vertical">
          {/* 检查类型 echo is informational only (2026-11-16): merged into a
              plain label row - a childless Form.Item still reserves its empty
              control slot, so no Form.Item here. The Tag echoes at the label
              font size, bold (2026-11-16). */}
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
              {modality}
            </Tag>
          </div>
          <Form.Item
            name="name"
            label="分组"
            rules={[{ required: true, message: '分组不能为空' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={4} maxLength={255} style={{ minHeight: 32 }} />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 部位抽屉 */}
      <Drawer
        title={editingBody ? '编辑部位' : '新建部位'}
        width={480}
        open={bodyDrawerOpen}
        onClose={() => setBodyDrawerOpen(false)}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnClose
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setBodyDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={bodySaving} onClick={() => void saveBody()}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={bodyForm} layout="vertical">
          {/* 检查类型 echo, same plain label row as the group drawer. */}
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
              {editingBody ? editingBody.modality : modality}
            </Tag>
          </div>
          <Form.Item name="upId" label="上级">
            <Select
              allowClear
              placeholder="无"
              options={flatBodies
                .filter((row) => !editingBody || row.id !== editingBody.id)
                .map((row) => ({ value: row.id, label: row.name }))}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label="部位"
            rules={[{ required: true, message: '部位不能为空' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="code" label="部位编码">
            <Input maxLength={32} />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={4} maxLength={255} style={{ minHeight: 32 }} />
          </Form.Item>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}

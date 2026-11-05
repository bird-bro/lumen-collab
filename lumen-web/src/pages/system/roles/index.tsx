import { TeamOutlined } from '@ant-design/icons'
import {
  Checkbox,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
  message,
  theme,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  listRoles,
  updateRole,
} from '@/api/roles'
import { menuTree } from '@/api/menus'
import { ApiError } from '@/api/client'
import type { MenuNode, Page, RoleItem } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePanel,
  ConsolePagination,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
  readForm,
} from '../console-shell'

/**
 * 角色管理 (batch 6b, task 6.4; spec `specs/web-system-console`
 * § Role Management and Menu Assignment). Layout source:
 * `prototype/role-menus.html` - the role table page plus the 菜单授权 modal
 * whose checkable tree full-replaces the grant and whose reopen shows exactly
 * the saved set.
 *
 * Data: `roles/list` is the api-delta GAP endpoint (IPage, unimplemented in
 * the backend until the follow-up); its rows may carry the `menuIds` grant
 * echo that drives the assignment dialog's initial checked keys (recorded in
 * `RoleItem.menuIds`).
 *
 * Assignment strategy (recorded per the brief): every node is checkable -
 * directories, menus and buttons (a button grant is what publishes a `perms`
 * string to the role). On save the checked set is UNIONed with the ancestors
 * of every checked node so the posted grant keeps a coherent tree (a granted
 * page whose group dirs are missing would not render in the user's menu
 * tree). The PUT is FULL-REPLACE: the posted set IS the role's complete grant.
 */

interface CreateValues {
  name: string
  type?: number
  remark?: string
}

interface EditValues {
  name: string
  type?: number
  remark?: string
}

/** Union of checked ids + all their ancestor ids (coherent full-replace grant). */
function withAncestors(tree: MenuNode[], checked: Set<number>): number[] {
  const result = new Set(checked)
  const walk = (nodes: MenuNode[], trail: number[]): void => {
    for (const node of nodes) {
      const nextTrail = [...trail, node.mid]
      if (checked.has(node.mid)) nextTrail.forEach((id) => result.add(id))
      if (node.children) walk(node.children, nextTrail)
    }
  }
  walk(tree, [])
  return [...result].sort((a, b) => a - b)
}

const TYPE_LABELS: Record<string, string> = { M: '目录', C: '菜单', F: '按钮' }

/**
 * The grant tree as a nested checkbox list (batch 6b): AntD `Tree`'s checkbox
 * mechanics (key stringification + cascade conduction) fight the full-replace
 * contract, so the dialog composes AntD `Checkbox` per node instead - each
 * node is independently toggled (checkStrict semantics made explicit), and
 * `withAncestors` unions the ancestors on save for a coherent grant.
 */
function GrantTree({
  nodes,
  checked,
  onToggle,
  depth = 0,
}: {
  nodes: MenuNode[]
  checked: Set<number>
  onToggle: (mid: number) => void
  depth?: number
}) {
  return (
    <>
      {nodes.map((node) => (
        <div key={node.mid} style={{ paddingInlineStart: depth * 20 }}>
          <Checkbox
            checked={checked.has(node.mid)}
            onChange={() => onToggle(node.mid)}
            aria-label={node.title}
          >
            <Space size={6}>
              <span>{node.title}</span>
              {node.type !== 'C' ? <Tag style={{ marginInlineEnd: 0 }}>{TYPE_LABELS[node.type] ?? node.type}</Tag> : null}
            </Space>
          </Checkbox>
          {node.children ? (
            <GrantTree nodes={node.children} checked={checked} onToggle={onToggle} depth={depth + 1} />
          ) : null}
        </div>
      ))}
    </>
  )
}

export default function RolesPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:role:list')
  const { token } = theme.useToken()

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [roles, setRoles] = useState<RoleItem[]>([])
  const [loading, setLoading] = useState(true)
  const [queryDraft, setQueryDraft] = useState('')
  const [query, setQuery] = useState('')
  const [messageApi, messageHolder] = message.useMessage()
  const [saving, setSaving] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<RoleItem | null>(null)
  const [assignTarget, setAssignTarget] = useState<RoleItem | null>(null)
  const [assignTree, setAssignTree] = useState<MenuNode[] | null>(null)
  const [assignLoading, setAssignLoading] = useState(false)
  const [checked, setChecked] = useState<Set<number>>(new Set())

  const [createForm] = Form.useForm<CreateValues>()
  const [editForm] = Form.useForm<EditValues>()

  const reload = useCallback(
    async (nextPage = page, nextSize = pageSize) => {
      setLoading(true)
      try {
        const data: Page<RoleItem> = await listRoles(nextPage, nextSize)
        setRoles(
          query.trim()
            ? data.records.filter((row) => row.name.toLowerCase().includes(query.trim().toLowerCase()))
            : data.records,
        )
        setTotal(data.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '角色列表加载失败')
      } finally {
        setLoading(false)
      }
    },
    [messageApi, page, pageSize, query],
  )

  useEffect(() => {
    void reload(page, pageSize)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize])

  const queryMounted = useRef(false)
  useEffect(() => {
    // Skip the mount run: the page/pageSize effect already issued the first read.
    if (!queryMounted.current) {
      queryMounted.current = true
      return
    }
    void reload(1, pageSize)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query])

  const runQuery = () => {
    setQuery(queryDraft)
    setPage(1)
  }

  const closeCreate = useCallback(() => {
    setCreateOpen(false)
    createForm.resetFields()
  }, [createForm])

  const submitCreate = useCallback(async () => {
    const values = await readForm(createForm)
    if (!values) return
    setSaving(true)
    try {
      await createRole({ name: values.name, type: values.type, remark: values.remark ?? null })
      closeCreate()
      await reload()
    } catch (error) {
      if (error instanceof ApiError) {
        // A0714 duplicate name lands ON THE FORM; the dialog stays open.
        createForm.setFields([{ name: 'name', errors: [error.msg] }])
      } else {
        messageApi.error('创建角色失败')
      }
    } finally {
      setSaving(false)
    }
  }, [closeCreate, createForm, messageApi, reload])

  const closeEdit = useCallback(() => {
    setEditTarget(null)
    editForm.resetFields()
  }, [editForm])

  const submitEdit = useCallback(async () => {
    if (!editTarget) return
    const values = await readForm(editForm)
    if (!values) return
    setSaving(true)
    try {
      await updateRole(editTarget.id, { name: values.name, type: values.type, remark: values.remark ?? null })
      closeEdit()
      await reload()
    } catch (error) {
      if (error instanceof ApiError) {
        editForm.setFields([{ name: 'name', errors: [error.msg] }])
      } else {
        messageApi.error('更新角色失败')
      }
    } finally {
      setSaving(false)
    }
  }, [closeEdit, editForm, editTarget, messageApi, reload])

  const openAssign = useCallback(async (role: RoleItem) => {
    setAssignTarget(role)
    setAssignLoading(true)
    setChecked(new Set(role.menuIds ?? []))
    try {
      setAssignTree(await menuTree())
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '菜单树加载失败')
      setAssignTarget(null)
    } finally {
      setAssignLoading(false)
    }
  }, [messageApi])

  const closeAssign = useCallback(() => {
    setAssignTarget(null)
    setAssignTree(null)
  }, [])

  const submitAssign = useCallback(async () => {
    if (!assignTarget || !assignTree) return
    setSaving(true)
    try {
      // FULL-REPLACE: the union of the checked ids and their ancestors becomes
      // the role's complete grant (the dialog's reopen reads it from the echo).
      await assignRoleMenus(assignTarget.id, withAncestors(assignTree, checked))
      closeAssign()
      await reload()
    } catch (error) {
      // A0400 unknown menu ids (and any other rejection): msg via message.error,
      // the dialog stays open so the administrator can correct the set.
      messageApi.error(error instanceof ApiError ? error.msg : '菜单授权保存失败')
    } finally {
      setSaving(false)
    }
  }, [assignTarget, assignTree, checked, closeAssign, messageApi, reload])

  const columns: TableColumnsType<RoleItem> = [
    {
      title: '角色名称',
      dataIndex: 'name',
      width: 200,
      render: (name: string, row: RoleItem) => (
        <Space size={6}>
          <TeamOutlined style={{ color: token.colorTextSecondary }} />
          <span>{name}</span>
          {row.type != null ? <Tag style={{ marginInlineEnd: 0 }}>{row.type}</Tag> : null}
        </Space>
      ),
    },
    { title: '备注', dataIndex: 'remark', render: (remark: string | null) => remark ?? '—' },
    {
      title: '授权菜单数',
      key: 'grants',
      width: 110,
      render: (_: unknown, row: RoleItem) => (
        <Typography.Text style={{ fontVariantNumeric: 'tabular-nums' }}>
          {row.menuIds?.length ?? '—'}
        </Typography.Text>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (createTime: string) => (
        <Typography.Text type="secondary" style={{ fontVariantNumeric: 'tabular-nums' }}>
          {createTime}
        </Typography.Text>
      ),
    },
  ]

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePanel>
        <ConsoleToolRow
          trailing={
            canManage ? (
              <ConsoleAddButton label="新建角色" onClick={() => setCreateOpen(true)} />
            ) : undefined
          }
        >
          <Input
            allowClear
            style={{ width: 220 }}
            placeholder="角色名称"
            aria-label="角色名称筛选"
            value={queryDraft}
            onChange={(event) => setQueryDraft(event.target.value)}
            onPressEnter={runQuery}
          />
          <ConsoleQueryButton onClick={runQuery} />
        </ConsoleToolRow>

        <ConsoleTable<RoleItem>
          rowKey={(row) => row.id}
          columns={columns}
          dataSource={roles}
          loading={loading}
          actions={
            canManage
              ? {
                  width: 150,
                  render: (row) => [
                    <ConsoleAction
                      key="assign"
                      label="菜单授权"
                      icon="assign"
                      onClick={() => void openAssign(row)}
                    />,
                    <ConsoleAction
                      key="edit"
                      label="编辑角色"
                      onClick={() => {
                        setEditTarget(row)
                        editForm.setFieldsValue({
                          name: row.name,
                          type: row.type,
                          remark: row.remark ?? '',
                        })
                      }}
                    />,
                    <Popconfirm
                      key="delete"
                      title="删除角色"
                      description="将级联解绑该角色的用户与菜单绑定,确认删除?"
                      okText="删除"
                      cancelText="取消"
                      onConfirm={() => {
                        void (async () => {
                          try {
                            await deleteRole(row.id)
                            await reload()
                          } catch (error) {
                            messageApi.error(error instanceof ApiError ? error.msg : '删除角色失败')
                          }
                        })()
                      }}
                    >
                      <ConsoleAction label="删除角色" icon="remove" danger onClick={() => undefined} />
                    </Popconfirm>,
                  ],
                }
              : undefined
          }
          onRefresh={() => void reload()}
        />

        <ConsolePagination
          total={total}
          current={page}
          pageSize={pageSize}
          onChange={(next, nextSize) => {
            setPage(next)
            setPageSize(nextSize)
          }}
          onShowSizeChange={(_current, size) => {
            setPage(1)
            setPageSize(size)
          }}
        />
      </ConsolePanel>

      <Modal
        open={createOpen}
        title="新建角色"
        okText="保 存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void submitCreate()}
        onCancel={closeCreate}
        destroyOnClose
        width={520}
      >
        <Form<CreateValues> form={createForm} layout="vertical" disabled={saving}>
          <Form.Item
            name="name"
            label="角色名称"
            extra="角色名重复时,后端 A 类错误 msg 将显示于此表单,弹窗保持打开"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="角色名称" autoComplete="off" />
          </Form.Item>
          <Form.Item name="type" label="类型" initialValue={10}>
            <Input type="number" min={1} placeholder="种子默认 10" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!editTarget}
        title="编辑角色"
        okText="保 存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void submitEdit()}
        onCancel={closeEdit}
        destroyOnClose
        width={520}
      >
        <Form<EditValues> form={editForm} layout="vertical" disabled={saving}>
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Input type="number" min={1} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!assignTarget}
        title={assignTarget ? `菜单授权 - ${assignTarget.name}` : '菜单授权'}
        okText="保存授权"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void submitAssign()}
        onCancel={closeAssign}
        width={560}
        styles={{ body: { maxHeight: 420, overflow: 'auto' } }}
      >
        <p style={{ margin: '0 0 12px', fontSize: token.fontSizeSM, color: token.colorTextTertiary }}>
          勾选集即该角色的<b>全量授权</b>(保存为全量替换);保存后重开弹窗将回显服务器保存的授权集。
        </p>
        {assignLoading || !assignTree ? (
          <div style={{ textAlign: 'center', padding: 32 }}>
            <Spin />
          </div>
        ) : (
          <GrantTree
            nodes={assignTree}
            checked={checked}
            onToggle={(mid) =>
              setChecked((prev) => {
                const next = new Set(prev)
                if (next.has(mid)) next.delete(mid)
                else next.add(mid)
                return next
              })
            }
          />
        )}
      </Modal>
    </div>
  )
}

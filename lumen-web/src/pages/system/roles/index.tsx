import {
  Button,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Switch,
  Spin,
  Tag,
  Tree,
  message,
  theme,
} from 'antd'
import type { TableColumnsType, TreeDataNode } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  listRoles,
  toggleRoleEnable,
  updateRole,
} from '@/api/roles'
import { menuTree } from '@/api/menus'
import { ApiError } from '@/api/client'
import type { DictItem, MenuNode, Page, RoleItem } from '@/api/types'
import { dictItemsByName, resolveDictLabel, ROLE_TYPE_DICT_NAME } from '@/utils/dict-items'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePage,
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
 * List columns (user review 2026-11-09): 序号 id lead column, 类型 as its own
 * styled column (blue system / cyan business), a bounded ellipsis remark, the
 * orgs/deps stacked 创建人 / 创建时间 audit cell - and NO 授权菜单数 column
 * (ruled a weak signal; the grant echo still feeds the assignment dialog).
 *
 * Data: `roles/list` is the api-delta GAP endpoint (IPage, unimplemented in
 * the backend until the follow-up); its rows may carry the `menuIds` grant
 * echo that drives the assignment dialog's initial checked keys (recorded in
 * `RoleItem.menuIds`).
 *
 * Assignment strategy (2026-11-09 form): the grant tree is an AntD Tree with
 * cascade conduction - checking a parent selects its whole subtree, unchecking
 * a leaf half-checks the ancestor path (rc-tree conduction semantics; the
 * independent-toggle take is retired). On save the checked set is UNIONed with
 * the ancestors of every checked node so the posted grant keeps a coherent
 * tree (a granted page whose group dirs are missing would not render in the
 * user's menu tree). The PUT is FULL-REPLACE: the posted set IS the role's
 * complete grant.
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

/** MenuNode -> rc-tree data: string keys (rc-tree keys are strings), plain
 * titles - no type tags (user ruling 2026-11-09: they read as clutter). */
function toTreeData(nodes: MenuNode[]): TreeDataNode[] {
  return nodes.map((node) => ({
    key: String(node.mid),
    title: node.title,
    children: node.children ? toTreeData(node.children) : undefined,
  }))
}

/** mids of the first two levels - the tree opens expanded through level 3
 * (user ruling 2026-11-09). */
function expandToLevel3(nodes: MenuNode[]): string[] {
  return nodes.flatMap((node) => [String(node.mid), ...(node.children ?? []).map((child) => String(child.mid))])
}

export default function RolesPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:role:list')
  // button-level gating (button-perm-enforcement-web): each affordance answers to
  // its own perms string (the V15 seeded F nodes pinned server-side)
  const canCreate = canManage && hasPerm('system:role:create')
  const canUpdate = canManage && hasPerm('system:role:update')
  const canDelete = canManage && hasPerm('system:role:delete')
  const canAssign = canManage && hasPerm('system:role:assign')
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
  // The 「角色类型」 dict items (role-type-dict-web D4): loaded ONCE on mount -
  // the resolver's session cache dedupes, and a failed fetch degrades to raw
  // codes with one message (the page never blocks).
  const [roleTypeItems, setRoleTypeItems] = useState<DictItem[]>([])

  const [createOpen, setCreateOpen] = useState(false)
  // The drawer 类型 Select options come straight from the 「角色类型」 dict
  // (option value = the numeric code the backend stores, label = dict value).
  const roleTypeOptions = roleTypeItems.map((item) => ({
    value: item.code as number,
    label: item.value ?? String(item.code),
  }))
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

  useEffect(() => {
    dictItemsByName(ROLE_TYPE_DICT_NAME)
      .then(setRoleTypeItems)
      .catch((error: unknown) => {
        messageApi.error(error instanceof ApiError ? error.msg : '角色类型字典加载失败')
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

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

  /**
   * Enable toggle (role-type-dict-web D7): posts the command, then ALWAYS re-lists -
   * success echoes the persisted value, failure surfaces the backend message and the
   * re-list restores the switch (no optimistic flip).
   */
  const toggleEnable = useCallback(
    async (row: RoleItem, enable: number) => {
      try {
        await toggleRoleEnable(row.id, enable)
        await reload()
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '启停角色失败')
        await reload()
      }
    },
    [messageApi, reload],
  )

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
      // No leading icon, no inline tag (user rulings 2026-11-09) - the type
      // moved to its own styled column below; the width carries long names.
      title: '名称',
      dataIndex: 'name',
      width: 260,
    },
    {
      // 状态 switch (role-type-dict-web D7): the orgs/deps chrome, centered -
      // the org-status-toggle mirror with the enable vocabulary.
      title: '状态',
      dataIndex: 'enable',
      width: 120,
      align: 'center',
      render: (enable: number, row: RoleItem) => (
        <Switch
          checked={enable === 1}
          disabled={!canUpdate}
          aria-label={`启用 ${row.name}`}
          checkedChildren="✓"
          unCheckedChildren="✕"
          onChange={(checked) => void toggleEnable(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      // 类型 from the 「角色类型」 dict (role-type-dict-web D4): label = dict value
      // matched by code, raw-number fallback for a dict miss. Chrome generalizes
      // the 2026-11-09 ruling: code 10 blue, everything else cyan.
      title: '类型',
      dataIndex: 'type',
      width: 150,
      align: 'center',
      render: (type: number | null) => {
        if (type == null) return '—'
        return (
          <Tag
            color={type === 10 ? 'blue' : 'cyan'}
            bordered={false}
            style={{ marginInlineEnd: 0, fontSize: token.fontSize }}
          >
            {resolveDictLabel(roleTypeItems, type)}
          </Tag>
        )
      },
    },
    // Bounded instead of stretching across the rest of the row; long remarks
    // truncate (user request 2026-11-09).
    { title: '备注', dataIndex: 'remark', ellipsis: true, render: (remark: string | null) => remark ?? '—' },
    {
      // Stacked audit cell per the orgs precedent; the API exposes the
      // audit creator as a numeric UID, so the cell keeps `UID n` without
      // inventing a client-side join. Font and width follow orgs (2026-11-11).
      title: '创建人 / 创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (_: unknown, row: RoleItem) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {row.createBy === null ? '—' : `UID ${row.createBy}`}
          </span>
          <span
            style={{
              fontSize: token.fontSizeSM,
              lineHeight: '18px',
              color: token.colorTextTertiary,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {row.createTime}
          </span>
        </div>
      ),
    },
  ]

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePage>
        <ConsoleToolRow
          trailing={
            canCreate ? (
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
                  render: (row) => [
                    canAssign ? (
                      <ConsoleAction
                        key="assign"
                        label="菜单授权"
                        icon="assign"
                        onClick={() => void openAssign(row)}
                      />
                    ) : null,
                    canUpdate ? (
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
                      />
                    ) : null,
                    canDelete ? (
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
                      </Popconfirm>
                    ) : null,
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
      </ConsolePage>

      <Drawer
        open={createOpen}
        title="新建角色"
        width={500}
        onClose={closeCreate}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeCreate}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitCreate()}>
              保 存
            </Button>
          </div>
        }
      >
        <Form<CreateValues> form={createForm} layout="vertical" disabled={saving}>
          <Form.Item
            name="name"
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input placeholder="角色名称" autoComplete="off" />
          </Form.Item>
          <Form.Item name="type" label="类型" initialValue={10}>
            {/* Dict-driven (role-type-dict-web D4): the vocabulary is operator-
                maintained in 字典管理; value = the numeric code, unchanged on the
                wire. Empty options = the dict never loaded - the round-trip
                still posts (the int is accepted as-is, backend D5). */}
            <Select options={roleTypeOptions} placeholder="角色类型" aria-label="类型" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            {/* Height pinned at 130px (user request 2026-11-09) - textareas are
                user-resizable in the browser; the inline height keeps the rest
                state deterministic. */}
            <Input.TextArea rows={2} placeholder="选填" style={{ height: 130 }} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        open={!!editTarget}
        title="编辑角色"
        width={500}
        onClose={closeEdit}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeEdit}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitEdit()}>
              保 存
            </Button>
          </div>
        }
      >
        <Form<EditValues> form={editForm} layout="vertical" disabled={saving}>
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Select options={roleTypeOptions} placeholder="角色类型" aria-label="类型" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} style={{ height: 130 }} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        open={!!assignTarget}
        title={assignTarget ? `菜单授权 - ${assignTarget.name}` : '菜单授权'}
        width={500}
        onClose={closeAssign}
        closable={{ placement: 'end' }}
        maskClosable={false}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeAssign}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitAssign()}>
              保存授权
            </Button>
          </div>
        }
      >
        {assignLoading || !assignTree ? (
          <div style={{ textAlign: 'center', padding: 32 }}>
            <Spin />
          </div>
        ) : (
          // AntD Tree with cascade conduction (user ruling 2026-11-09: check a
          // parent selects its subtree, unchecking a leaf half-checks the
          // ancestor path - the checkStrictly take is retired). `withAncestors`
          // still unions ancestors on save, so a half-checked dir with any
          // checked descendant stays granted and the FULL-REPLACE contract
          // keeps posting a coherent set.
          <Tree
            checkable
            blockNode
            defaultExpandedKeys={expandToLevel3(assignTree)}
            checkedKeys={[...checked].map(String)}
            onCheck={(keys) => {
              const checkedKeys = Array.isArray(keys) ? keys : keys.checked
              setChecked(new Set(checkedKeys.map((key) => Number(key))))
            }}
            treeData={toTreeData(assignTree)}
          />
        )}
      </Drawer>
    </div>
  )
}

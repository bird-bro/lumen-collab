import { Button, Drawer, Form, Input, Select, Space, Switch, Tag, Tooltip, Typography, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { createUser, enableUser, listUsers, resetPassword } from '@/api/users'
import { listRoles } from '@/api/roles'
import { ApiError } from '@/api/client'
import type { RoleItem, UserItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
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
 * 用户管理 (batch 6a, task 6.3; spec `specs/web-system-console`
 * § User Management: `users/list` is a PLAIN list the view paginates
 * client-side, with create, password reset and enable/disable).
 *
 * Layout: `prototype/console-shell.html` `.main > .panel` - the `.search` tool
 * row (f-label + select + 查询 LEFT, the `.push` primary 新建用户 RIGHT), the
 * `.tbl` columns 用户名/姓名/角色/启用/创建时间/操作 with the `.th-refresh`
 * glyph, the `.tag` chips, the `.switch` in 启用, the centered 60px `.pager`
 * with `共 N 条`, and the `.modal` create dialog whose 用户名 helper line
 * states the duplicate-username contract verbatim.
 *
 * Two recorded departures (both forced by the documented surface, never by
 * taste - `specs/web-app-shell` § API Client Seam Without Invented Endpoints):
 * 1. the prototype's tool-row control is a 科室 select, but no endpoint binds a
 *    user to a department (`users/list` takes no query and `UserItemDto` has no
 *    org/dep field), so a dept dropdown would be a dead control; the tool row's
 *    dropdown filters the ENABLE state, which the payload really carries, and
 *    the text query filters 用户名/姓名. The role catalog from `roles/list`
 *    still feeds the create dialog's 角色 select (CreateUserBody.roleIds).
 * 2. the prototype's 编辑/删除 row glyphs are NOT implemented: no user
 *    edit/delete endpoint exists. The operation column carries 重置密码 only.
 *    The 角色 column can only name the CURRENT session user's roles (that is
 *    what the payload publishes via the session profile); other rows show an
 *    explicit 未提供 chip instead of a fabricated grant.
 *
 * Permission gating: `system:user:list` (SEED_MENUS id 2 = the V2 seed row and
 * the backend `@PreAuthorize` on the user endpoints) gates the add button, the
 * reset action and the enable switch. No new perms string is invented.
 */

/** Prototype `.tbl` widths (200/200/200/110/auto/150). */
const COLUMN_WIDTHS = { username: 200, name: 200, roles: 200, enable: 110, actions: 150 } as const

const PAGE_SIZE = 10

/** The reset contract made visible: mustChangePassword 1 -> change at first login. */
const MUST_CHANGE_HINT = '首次登录需改密'

/** Tool-row dropdown values (the `enable` flag the payload publishes). */
const ENABLE_FILTERS = [
  { value: 'all', label: '全部状态' },
  { value: '1', label: '已启用' },
  { value: '0', label: '已停用' },
] as const

type EnableFilter = (typeof ENABLE_FILTERS)[number]['value']

interface CreateUserValues {
  username: string
  name: string
  roleIds?: number[]
  password: string
}

interface ResetValues {
  password: string
}

export default function UsersPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:user:list')
  // button-level gating (button-perm-enforcement-web): each affordance answers to
  // its own perms string (the V15 seeded F nodes pinned server-side)
  const canCreate = canManage && hasPerm('system:user:create')
  const canResetPwd = canManage && hasPerm('system:user:reset-pwd')
  const canEnable = canManage && hasPerm('system:user:enable')
  const profile = useAuthStore((state) => state.profile)
  const { token } = theme.useToken()

  const [users, setUsers] = useState<UserItem[]>([])
  const [roles, setRoles] = useState<RoleItem[]>([])
  const [loading, setLoading] = useState(true)
  const [queryDraft, setQueryDraft] = useState('')
  const [query, setQuery] = useState('')
  const [enableFilter, setEnableFilter] = useState<EnableFilter>('all')
  const [page, setPage] = useState(1)
  const [createOpen, setCreateOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [resetTarget, setResetTarget] = useState<UserItem | null>(null)
  const [form] = Form.useForm<CreateUserValues>()
  const [resetForm] = Form.useForm<ResetValues>()
  const [messageApi, messageHolder] = message.useMessage()

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setUsers(await listUsers())
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '用户列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [messageApi])

  useEffect(() => {
    void reload()
  }, [reload])

  // Role catalog for the create dialog's 角色 select. `roles/list` is the
  // api-delta gap endpoint (mock-served until the backend implements it), so a
  // failure leaves the catalog empty instead of blocking the user table.
  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const data = await listRoles()
        if (alive) setRoles(data.records)
      } catch {
        if (alive) setRoles([])
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  // Client-side view state over the one plain array (spec: the view paginates).
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase()
    return users.filter((user) => {
      if (enableFilter !== 'all' && String(user.enable) !== enableFilter) return false
      if (!needle) return true
      return (
        user.username.toLowerCase().includes(needle) || user.name.toLowerCase().includes(needle)
      )
    })
  }, [enableFilter, query, users])

  const pageRows = useMemo(
    () => filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
    [filtered, page],
  )

  /**
   * 查询 applies the client-side predicates AND re-issues the one documented
   * read: spec § User Management says the list "re-queries on filter/page
   * change" while the same requirement pins pagination to the view (the plain
   * array), so a filter press refreshes the array and page switches stay local.
   */
  const runQuery = () => {
    setQuery(queryDraft)
    setPage(1)
    void reload()
  }

  const closeCreate = useCallback(() => {
    setCreateOpen(false)
    form.resetFields()
  }, [form])

  const submitCreate = useCallback(async () => {
    // A rejected validateFields() must never escape the drawer footer submit handler
    // (an unhandled rejection would also swallow the dialog's field errors).
    const values = await readForm(form)
    if (!values) return
    setSaving(true)
    try {
      await createUser({
        username: values.username,
        password: values.password,
        name: values.name,
        ...(values.roleIds && values.roleIds.length > 0 ? { roleIds: values.roleIds } : {}),
      })
      closeCreate()
      await reload()
    } catch (error) {
      if (error instanceof ApiError) {
        // A0111 「用户名已存在」 and every other create rejection land ON THE
        // FORM and the dialog stays open (spec: Duplicate username feedback).
        form.setFields([{ name: 'username', errors: [error.msg] }])
      } else {
        messageApi.error('创建用户失败')
      }
    } finally {
      setSaving(false)
    }
  }, [closeCreate, form, messageApi, reload])

  const closeReset = useCallback(() => {
    setResetTarget(null)
    resetForm.resetFields()
  }, [resetForm])

  const submitReset = useCallback(async () => {
    if (!resetTarget) return
    // A rejected validateFields() must never escape the drawer footer submit handler
    // (an unhandled rejection would also swallow the dialog's field errors).
    const values = await readForm(resetForm)
    if (!values) return
    setSaving(true)
    try {
      await resetPassword(resetTarget.uid, values.password)
      closeReset()
      // The re-list echoes mustChangePassword = 1 (the reset's visible effect).
      await reload()
    } catch (error) {
      // A/B-class reset failures: msg via message.error, dialog stays open.
      messageApi.error(error instanceof ApiError ? error.msg : '重置密码失败')
    } finally {
      setSaving(false)
    }
  }, [closeReset, messageApi, reload, resetForm, resetTarget])

  const toggleEnable = useCallback(
    async (user: UserItem, next: 0 | 1) => {
      setUsers((prev) =>
        prev.map((row) => (row.uid === user.uid ? { ...row, enable: next } : row)),
      )
      try {
        await enableUser(user.uid, next)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '启用状态更新失败')
      }
      // Re-sync every column from the server (an A-class rejection rolls back
      // the optimistic flip).
      await reload()
    },
    [messageApi, reload],
  )

  const columns: TableColumnsType<UserItem> = [
    {
      title: '用户名',
      dataIndex: 'username',
      width: COLUMN_WIDTHS.username,
      render: (username: string) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>{username}</span>
      ),
    },
    { title: '姓名', dataIndex: 'name', width: COLUMN_WIDTHS.name },
    {
      key: 'roles',
      title: '角色',
      width: COLUMN_WIDTHS.roles,
      render: (_: unknown, row: UserItem) => {
        const names = row.uid === profile?.uid ? profile.roles : []
        if (names.length === 0) {
          return (
            <Tooltip title="用户列表接口未发布按用户的角色绑定(api delta 未提供该端点),故仅当前会话账号可显示角色">
              <Tag style={{ marginInlineEnd: 0 }}>未提供</Tag>
            </Tooltip>
          )
        }
        return (
          <Space size={4} wrap>
            {names.map((name) => (
              <Tag key={name} style={{ marginInlineEnd: 0 }}>
                {name}
              </Tag>
            ))}
          </Space>
        )
      },
    },
    {
      title: '启用',
      dataIndex: 'enable',
      width: COLUMN_WIDTHS.enable,
      render: (enable: number, row: UserItem) => (
        <Switch
          checked={enable === 1}
          disabled={!canEnable}
          aria-label={`启用 ${row.username}`}
          checkedChildren="✓"
          unCheckedChildren="✕"
          onChange={(checked) => void toggleEnable(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      render: (createTime: string, row: UserItem) => (
        <Space size={6} wrap>
          <Typography.Text type="secondary" style={{ fontVariantNumeric: 'tabular-nums' }}>
            {createTime}
          </Typography.Text>
          {row.mustChangePassword === 1 ? <Tag style={{ marginInlineEnd: 0 }}>{MUST_CHANGE_HINT}</Tag> : null}
        </Space>
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
              <ConsoleAddButton label="新建用户" onClick={() => setCreateOpen(true)} />
            ) : undefined
          }
        >
          <Typography.Text style={{ color: token.colorTextSecondary }}>状态</Typography.Text>
          <Select
            aria-label="启用状态筛选"
            style={{ width: 160 }}
            value={enableFilter}
            options={ENABLE_FILTERS.map((item) => ({ ...item }))}
            onChange={(value: EnableFilter) => {
              setEnableFilter(value)
              setPage(1)
            }}
          />
          <Input
            allowClear
            style={{ width: 220 }}
            placeholder="用户名 / 姓名"
            aria-label="用户名 / 姓名"
            value={queryDraft}
            onChange={(event) => setQueryDraft(event.target.value)}
            onPressEnter={runQuery}
          />
          <ConsoleQueryButton onClick={runQuery} />
        </ConsoleToolRow>

        <ConsoleTable<UserItem>
          rowKey={(row) => row.uid}
          columns={columns}
          dataSource={pageRows}
          loading={loading}
          actions={
            canManage
              ? {
                  width: COLUMN_WIDTHS.actions,
                  render: (row) =>
                    [
                      canResetPwd ? (
                        <ConsoleAction
                          key="reset"
                          label="重置密码"
                          icon="key"
                          onClick={() => {
                            setResetTarget(row)
                            resetForm.setFieldsValue({ password: '' })
                          }}
                        />
                      ) : null,
                    ].filter(Boolean),
                }
              : undefined
          }
          onRefresh={() => void reload()}
        />

        <ConsolePagination
          total={filtered.length}
          current={page}
          pageSize={PAGE_SIZE}
          showSizeChanger={false}
          onChange={(next) => setPage(next)}
        />
      </ConsolePage>

      <Drawer
        open={createOpen}
        title="新建用户"
        width={500}
        onClose={closeCreate}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeCreate}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitCreate()}>
              保存
            </Button>
          </div>
        }
      >
        <Form<CreateUserValues> form={form} layout="vertical" disabled={saving}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="登录账号,创建后不可修改" autoComplete="off" />
          </Form.Item>
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input placeholder="真实姓名" autoComplete="off" />
          </Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select
              mode="multiple"
              allowClear
              placeholder="绑定到该用户的角色"
              optionFilterProp="label"
              options={roles.map((role) => ({ value: role.id, label: role.name }))}
            />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[{ required: true, message: '请输入初始密码' }]}
          >
            <Input.Password placeholder="首次登录时将要求修改密码" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        open={!!resetTarget}
        title="重置密码"
        width={500}
        onClose={closeReset}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeReset}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitReset()}>
              保存
            </Button>
          </div>
        }
      >
        {resetTarget ? (
          <p style={{ margin: '0 0 16px', fontSize: token.fontSizeSM, color: token.colorTextTertiary }}>
            为用户 <strong>{resetTarget.username}</strong> 设置新密码;重置后该用户首次登录须修改密码。
          </p>
        ) : null}
        <Form<ResetValues> form={resetForm} layout="vertical" disabled={saving}>
          <Form.Item name="password" label="新密码" rules={[{ required: true, message: '请输入新密码' }]}>
            <Input.Password placeholder="新密码" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
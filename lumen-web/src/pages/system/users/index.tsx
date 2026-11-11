import { Button, Drawer, Form, Input, Popconfirm, Select, Space, Switch, Tag, Typography, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  createUser,
  deleteUser,
  enableUser,
  getUserDetail,
  listUsersWithProfile,
  resetPassword,
  updateUser,
} from '@/api/users'
import { listRoles } from '@/api/roles'
import { listDeps } from '@/api/org-deps'
import { ApiError } from '@/api/client'
import type { DictItem, OrgDep, RoleItem, UserItem } from '@/api/types'
import { usePerm } from '@/utils/perm'
import { dictItemsByName, resolveDictLabel } from '@/utils/dict-items'
import { SignatureUploadField } from './signature-upload'
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
 * 用户管理 (user-edit-delete-profile-web; spec `specs/web-system-console` §
 * User Management): the RCP-shaped console - the list reads
 * `GET /system/users/list/profile` (the rows carry the V26 profile columns and
 * the batch-resolved roleNames/depName, so the 角色 chips render for ANY row -
 * the former session-profile-only hack and its 未提供 chip are dead), the row
 * actions carry 编辑 (the shared drawer prefilled from `GET /users/{id}`,
 * 登录账号 disabled) and 删除 (Popconfirm -> the protected logical delete;
 * self/seed-admin rejections surface the backend msg), the create dialog grows
 * the same profile form, and the four dict-backed selects
 * (性别/职称/检查类型/审核权限) resolve through the session-cached by-name
 * lookup with the raw-code fallback (role-type-dict-web D4 precedent).
 *
 * 重置密码 and the 启用 switch stay byte-for-byte in behavior (the SM2
 * transport + the optimistic flip + the re-sync, password-transport-encryption
 * and button-perm precedents).
 *
 * Permission gating: `system:user:list` gates the page affordances; the new
 * 编辑/删除 answer to the V26-seeded `system:user:update` /
 * `system:user:delete` button perms (no invented strings).
 */

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

/** The dict catalogs the four selects consume (the V26 seeds anchor by NAME). */
const DICT_GENDER = '性别'
const DICT_TITLE = '职称'
const DICT_EXAM_TYPE = '检查类型'
const DICT_REP_AUDIT = '审核权限'

/** The 检查类型 CSV's separator - the RCP lineage's storage shape. */
const CSV_SEPARATOR = ','

interface UserFormValues {
  username: string
  name: string
  phone?: string
  email?: string
  gender?: number
  title?: number
  depId?: number
  roleIds?: number[]
  modalitys?: number[]
  repAudit?: number
  signature?: string | null
  password: string
}

interface ResetValues {
  password: string
}

export default function UsersPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:user:list')
  // button-level gating (button-perm-enforcement-web): each affordance answers to
  // its own perms string (the V15/V26 seeded F nodes pinned server-side)
  const canCreate = canManage && hasPerm('system:user:create')
  const canResetPwd = canManage && hasPerm('system:user:reset-pwd')
  const canEnable = canManage && hasPerm('system:user:enable')
  const canUpdate = canManage && hasPerm('system:user:update')
  const canDelete = canManage && hasPerm('system:user:delete')
  const { token } = theme.useToken()

  const [users, setUsers] = useState<UserItem[]>([])
  const [roles, setRoles] = useState<RoleItem[]>([])
  const [deps, setDeps] = useState<OrgDep[]>([])
  const [genderItems, setGenderItems] = useState<DictItem[]>([])
  // The ref mirror the prefill mapping reads (state may lag the click's closure).
  const examTypeItemsRef = useRef<DictItem[]>([])
  const [titleItems, setTitleItems] = useState<DictItem[]>([])
  const [examTypeItems, setExamTypeItems] = useState<DictItem[]>([])
  const [repAuditItems, setRepAuditItems] = useState<DictItem[]>([])
  const [loading, setLoading] = useState(true)
  const [queryDraft, setQueryDraft] = useState('')
  const [query, setQuery] = useState('')
  const [enableFilter, setEnableFilter] = useState<EnableFilter>('all')
  const [page, setPage] = useState(1)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [resetTarget, setResetTarget] = useState<UserItem | null>(null)
  const [form] = Form.useForm<UserFormValues>()
  const [resetForm] = Form.useForm<ResetValues>()
  const [messageApi, messageHolder] = message.useMessage()

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setUsers(await listUsersWithProfile())
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '用户列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [messageApi])

  useEffect(() => {
    void reload()
  }, [reload])

  // The create/edit dialog's 角色 catalog (the roles list; a failure leaves it
  // empty instead of blocking the user table - the batch-6a precedent).
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

  // The 所属科室 options (the departments list; same degrade-to-empty rule).
  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        if (alive) setDeps(await listDeps())
      } catch {
        if (alive) setDeps([])
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  // The four dict catalogs, one wire call per name per session (the
  // dict-items util's cache); a failure degrades to raw codes, never blocking.
  useEffect(() => {
    let alive = true
    const load = (name: string, set: (items: DictItem[]) => void) => {
      void dictItemsByName(name)
        .then((items) => {
          if (alive) set(items)
        })
        .catch((error: unknown) => {
          if (alive) messageApi.error(error instanceof ApiError ? error.msg : `${name}字典加载失败`)
        })
    }
    load(DICT_GENDER, setGenderItems)
    load(DICT_TITLE, setTitleItems)
    load(DICT_EXAM_TYPE, (items) => {
      examTypeItemsRef.current = items
      setExamTypeItems(items)
    })
    load(DICT_REP_AUDIT, setRepAuditItems)
    return () => {
      alive = false
    }
  }, [messageApi])

  // Client-side view state over the one plain array (the view paginates).
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

  const runQuery = () => {
    setQuery(queryDraft)
    setPage(1)
    void reload()
  }

  const closeDrawer = useCallback(() => {
    setDrawerOpen(false)
    setEditingId(null)
    form.resetFields()
  }, [form])

  const openCreate = useCallback(() => {
    setEditingId(null)
    form.resetFields()
    setDrawerOpen(true)
  }, [form])

  const openEdit = useCallback(
    async (row: UserItem) => {
      setEditingId(row.uid)
      try {
        // The four catalogs settle BEFORE the mapping: the prefill maps the CSV's
        // dict values to codes through examTypeItems, and racing the mount effects
        // would map against an empty catalog (NaN-filtered to a blank select).
        // dictItemsByName is session-cached - an already-loaded catalog resolves
        // instantly here.
        await Promise.allSettled([
          dictItemsByName(DICT_GENDER),
          dictItemsByName(DICT_TITLE),
          dictItemsByName(DICT_EXAM_TYPE),
          dictItemsByName(DICT_REP_AUDIT),
        ])
        const detail = await getUserDetail(row.uid)
        form.setFieldsValue({
          username: detail.username,
          name: detail.name,
          phone: detail.phone ?? undefined,
          email: detail.email ?? undefined,
          gender: detail.gender ?? undefined,
          title: detail.title ?? undefined,
          depId: detail.depId ?? undefined,
          roleIds: detail.roleIds,
          // The CSV splits back to the multi-select's array (the RCP precedent).
          // The RCP lineage stores the dict VALUES ('CT,MR') - map each entry to
          // its dict CODE (the Select's value), degrading to the raw entry when
          // the dict lacks it (the raw-code fallback's mirror).
          modalitys: detail.modalitys
            ? detail.modalitys
                .split(CSV_SEPARATOR)
                .map((entry) => {
                  const trimmed = entry.trim()
                  const match = examTypeItemsRef.current.find((item) => item.value === trimmed)
                  return match?.code ?? Number(trimmed)
                })
                .filter((code): code is number => Number.isFinite(code))
            : [],
          repAudit: detail.repAudit ?? undefined,
          signature: detail.signature,
          password: '',
        })
        setDrawerOpen(true)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '用户详情加载失败')
      }
    },
    [form, messageApi],
  )

  const submitDrawer = useCallback(async () => {
    const values = await readForm(form)
    if (!values) return
    setSaving(true)
    try {
      if (editingId != null) {
        await updateUser(editingId, {
          name: values.name,
          phone: values.phone ?? null,
          email: values.email ?? null,
          gender: values.gender ?? null,
          title: values.title ?? null,
          depId: values.depId ?? null,
          roleIds: values.roleIds ?? [],
          // the multi-select's array joins to the CSV the payload carries
          modalitys: values.modalitys && values.modalitys.length > 0
            ? values.modalitys.join(CSV_SEPARATOR)
            : null,
          repAudit: values.repAudit ?? null,
          signature: values.signature ?? null,
        })
      } else {
        await createUser({
          username: values.username,
          password: values.password,
          name: values.name,
          phone: values.phone ?? null,
          email: values.email ?? null,
          gender: values.gender ?? null,
          title: values.title ?? null,
          depId: values.depId ?? null,
          roleIds: values.roleIds,
          modalitys:
            values.modalitys && values.modalitys.length > 0
              ? values.modalitys.join(CSV_SEPARATOR)
              : null,
          repAudit: values.repAudit ?? null,
          signature: values.signature ?? null,
        })
      }
      closeDrawer()
      await reload()
    } catch (error) {
      if (error instanceof ApiError) {
        if (editingId == null) {
          // A0111 「用户名已存在」 and every other create rejection land ON THE
          // FORM and the dialog stays open (the duplicate-username contract).
          form.setFields([{ name: 'username', errors: [error.msg] }])
        } else {
          // an update rejection keeps the dialog with the msg surfaced
          messageApi.error(error.msg)
        }
      } else {
        messageApi.error(editingId == null ? '创建用户失败' : '保存用户失败')
      }
    } finally {
      setSaving(false)
    }
  }, [closeDrawer, editingId, form, messageApi, reload])

  const confirmDelete = useCallback(
    async (row: UserItem) => {
      try {
        await deleteUser(row.uid)
      } catch (error) {
        // the protected-account rejection (self / seed admin) surfaces and the row stays
        messageApi.error(error instanceof ApiError ? error.msg : '删除用户失败')
        return
      }
      await reload()
    },
    [messageApi, reload],
  )

  const closeReset = useCallback(() => {
    setResetTarget(null)
    resetForm.resetFields()
  }, [resetForm])

  const submitReset = useCallback(async () => {
    if (!resetTarget) return
    const values = await readForm(resetForm)
    if (!values) return
    setSaving(true)
    try {
      await resetPassword(resetTarget.uid, values.password)
      closeReset()
      // The re-list echoes mustChangePassword = 1 (the reset's visible effect).
      await reload()
    } catch (error) {
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
      title: '姓名',
      dataIndex: 'name',
      width: 160,
      render: (name: string, row: UserItem) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: '20px' }}>
          <span>{name}</span>
          <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM, fontVariantNumeric: 'tabular-nums' }}>
            {row.username}
          </Typography.Text>
        </div>
      ),
    },
    {
      key: 'roles',
      title: '角色',
      width: 180,
      render: (_: unknown, row: UserItem) => {
        if (!row.roleNames || row.roleNames.length === 0) {
          return <span style={{ color: token.colorTextTertiary }}>—</span>
        }
        return (
          <Space size={4} wrap>
            {row.roleNames.map((name) => (
              <Tag key={name} style={{ marginInlineEnd: 0 }}>
                {name}
              </Tag>
            ))}
          </Space>
        )
      },
    },
    {
      title: '科室',
      dataIndex: 'depName',
      width: 120,
      render: (depName: string | null) =>
        depName ? depName : <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    {
      title: '职称',
      dataIndex: 'title',
      width: 110,
      render: (title: number | null) =>
        title == null ? (
          <span style={{ color: token.colorTextTertiary }}>—</span>
        ) : (
          resolveDictLabel(titleItems, title)
        ),
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 140,
      render: (phone: string | null) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>
          {phone ?? <span style={{ color: token.colorTextTertiary }}>—</span>}
        </span>
      ),
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 180,
      ellipsis: true,
      render: (email: string | null) =>
        email ?? <span style={{ color: token.colorTextTertiary }}>—</span>,
    },
    {
      title: '启用',
      dataIndex: 'enable',
      width: 90,
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
      width: 160,
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

  const isEdit = editingId != null

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePage>
        <ConsoleToolRow
          trailing={
            canCreate ? (
              <ConsoleAddButton label="新建用户" onClick={openCreate} />
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
                  width: 170,
                  render: (row) =>
                    [
                      canUpdate ? (
                        <ConsoleAction
                          key="edit"
                          label="编辑"
                          icon="edit"
                          onClick={() => void openEdit(row)}
                        />
                      ) : null,
                      canDelete ? (
                        <Popconfirm
                          key="delete"
                          title={`确定删除用户 ${row.name}?`}
                          okText="确定"
                          cancelText="取消"
                          onConfirm={() => void confirmDelete(row)}
                        >
                          <ConsoleAction label="删除" icon="remove" danger onClick={() => undefined} />
                        </Popconfirm>
                      ) : null,
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
        open={drawerOpen}
        title={isEdit ? '编辑用户' : '新建用户'}
        width={520}
        onClose={closeDrawer}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeDrawer}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitDrawer()}>
              保存
            </Button>
          </div>
        }
      >
        <Form<UserFormValues> form={form} layout="vertical" disabled={saving}>
          <Form.Item
            name="username"
            label="登录账号"
            rules={[{ required: true, message: '请输入登录账号' }]}
            extra={isEdit ? undefined : '登录账号,创建后不可修改'}
          >
            <Input
              placeholder="登录账号"
              autoComplete="off"
              disabled={isEdit}
            />
          </Form.Item>
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input placeholder="真实姓名" autoComplete="off" />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input placeholder="手机号" maxLength={11} style={{ fontVariantNumeric: 'tabular-nums' }} />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input placeholder="邮箱" autoComplete="off" />
          </Form.Item>
          <Form.Item name="gender" label="性别">
            <Select
              allowClear
              placeholder="请选择性别"
              options={genderItems.map((item) => ({ value: item.code, label: item.value }))}
            />
          </Form.Item>
          <Form.Item name="title" label="职称">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="请选择职称"
              options={titleItems.map((item) => ({ value: item.code, label: item.value }))}
            />
          </Form.Item>
          <Form.Item name="depId" label="所属科室">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="请选择所属科室"
              options={deps.map((d) => ({ value: d.id, label: d.name }))}
            />
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
          <Form.Item name="modalitys" label="检查类型">
            <Select
              mode="multiple"
              allowClear
              placeholder="可查看的检查类型数据(CT/MR/US...)"
              optionFilterProp="label"
              options={examTypeItems.map((item) => ({ value: item.code, label: item.value }))}
            />
          </Form.Item>
          <Form.Item name="repAudit" label="审核权限">
            <Select
              allowClear
              placeholder="报告审核层级"
              options={repAuditItems.map((item) => ({ value: item.code, label: item.value }))}
            />
          </Form.Item>
          <Form.Item name="signature" label="签名">
            <SignatureUploadField />
          </Form.Item>
          {!isEdit ? (
            <Form.Item
              name="password"
              label="初始密码"
              rules={[{ required: true, message: '请输入初始密码' }]}
            >
              <Input.Password placeholder="首次登录时将要求修改密码" autoComplete="new-password" />
            </Form.Item>
          ) : null}
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

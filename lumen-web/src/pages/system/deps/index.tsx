import { Button, Drawer, Form, Input, Popconfirm, Typography, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { createDep, deleteDep, listDeps, updateDep } from '@/api/org-deps'
import { orgTree } from '@/api/orgs'
import { ApiError } from '@/api/client'
import type { OrgDep, OrgNode } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePanel,
  ConsolePagination,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
  ConsoleWorkArea,
  readForm,
} from '../console-shell'
import { OrgTree } from '../org-tree'

/**
 * 科室管理 (batch 6a, task 6.2; spec `specs/web-system-console`
 * § Department Management).
 *
 * Layout: `prototype/org-deps.html` `.work` block - the 250px left
 * `.tree-panel` carries the org SELECTION CONTEXT (`.tree-hd` 「机构」 +
 * `.tree-hint`; the row action glyphs and the dashed 「新建根机构」 foot button
 * of that prototype panel are org-management verbs and belong to task 6.1's
 * page, so this aside never mutates orgs - recorded in the batch report), and
 * the right `.list-panel` is the department list (`.tool-row` code/name input
 * + 查询 + the `.push` primary 新建科室, `.tbl` 科室名称/科室编码/创建时间/操作
 * with `.th-refresh`, centered `.pager`). The create dialog reproduces the
 * prototype `.modal`: 上级机构 context, required 科室编码 with its DICOM/HL7
 * purpose note, 科室名称.
 *
 * Data flow: `deps.listDeps(orgId)` is re-issued whenever the selected org
 * changes; the code/name query filters the already-listed rows client-side
 * (the documented endpoint takes only `orgId`, so a re-query would be a
 * request the API does not offer). No URL is built here.
 *
 * Duplicate live codes reject `A0712` and per the brief that msg lands INSIDE
 * the form on the code field with the dialog staying open.
 *
 * Permission gating: `system:dep:list` (SEED_MENUS id 8 / the backend
 * `@PreAuthorize`) gates the whole mutation surface - add button + row actions.
 */

/** Prototype `.tbl` column widths (220 / 200 / auto / 130). */
const COLUMN_WIDTHS = { name: 220, code: 200, actions: 130 } as const

const PAGE_SIZE = 10

interface DepFormValues {
  code: string
  name: string
}

type DepDialog = { kind: 'create' } | { kind: 'edit'; dep: OrgDep }

export default function DepsPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:dep:list')
  const { token } = theme.useToken()

  const [orgs, setOrgs] = useState<OrgNode[]>([])
  const [orgTreeLoading, setOrgTreeLoading] = useState(true)
  const [selectedOrgId, setSelectedOrgId] = useState<number | null>(null)
  const [deps, setDeps] = useState<OrgDep[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [queryDraft, setQueryDraft] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [dialog, setDialog] = useState<DepDialog | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<DepFormValues>()
  const [messageApi, messageHolder] = message.useMessage()

  const selectedOrg = useMemo(
    () => (selectedOrgId === null ? null : findOrg(orgs, selectedOrgId)),
    [orgs, selectedOrgId],
  )

  const loadDeps = useCallback(
    async (orgId: number) => {
      setListLoading(true)
      try {
        setDeps(await listDeps(orgId))
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '科室列表加载失败')
      } finally {
        setListLoading(false)
      }
    },
    [messageApi],
  )

  // Tree: one fetch on mount; the first root becomes the context node and the
  // department list follows it (spec: "only that org's departments are listed").
  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const tree = await orgTree()
        if (!alive) return
        setOrgs(tree)
        const root = tree[0]
        if (root) setSelectedOrgId((prev) => prev ?? root.id)
      } catch (error) {
        if (alive) messageApi.error(error instanceof ApiError ? error.msg : '机构树加载失败')
      } finally {
        if (alive) setOrgTreeLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [messageApi])

  useEffect(() => {
    if (selectedOrgId !== null) void loadDeps(selectedOrgId)
  }, [loadDeps, selectedOrgId])

  const reloadOrgTree = useCallback(async () => {
    setOrgTreeLoading(true)
    try {
      setOrgs(await orgTree())
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '机构树加载失败')
    } finally {
      setOrgTreeLoading(false)
    }
  }, [messageApi])

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (!needle) return deps
    return deps.filter(
      (dep) => dep.code.toLowerCase().includes(needle) || dep.name.toLowerCase().includes(needle),
    )
  }, [deps, query])

  const pageRows = useMemo(
    () => filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
    [filtered, page],
  )

  const runQuery = () => {
    setQuery(queryDraft)
    setPage(1)
  }

  const closeDialog = useCallback(() => {
    setDialog(null)
    form.resetFields()
  }, [form])

  const openDialog = useCallback(
    (next: DepDialog) => {
      form.setFieldsValue(
        next.kind === 'edit'
          ? { code: next.dep.code, name: next.dep.name }
          : { code: '', name: '' },
      )
      setDialog(next)
    },
    [form],
  )

  const submitDialog = useCallback(async () => {
    if (!dialog || selectedOrgId === null) return
    // A rejected validateFields() must never escape the drawer footer submit handler
    // (an unhandled rejection would also swallow the dialog's field errors).
    const values = await readForm(form)
    if (!values) return
    setSaving(true)
    try {
      if (dialog.kind === 'edit') {
        // 科室编码 is immutable after creation (it routes DICOM/HL7 traffic),
        // so the PUT carries the name only.
        await updateDep(dialog.dep.id, { name: values.name })
      } else {
        await createDep({ orgId: selectedOrgId, code: values.code, name: values.name })
      }
      closeDialog()
      await loadDeps(selectedOrgId)
    } catch (error) {
      if (error instanceof ApiError) {
        // Duplicate live code (A0712) and other validation-class rejections
        // belong to the code field, and the dialog STAYS OPEN (brief 6.2).
        form.setFields([{ name: 'code', errors: [error.msg] }])
      } else {
        messageApi.error('保存科室失败')
      }
    } finally {
      setSaving(false)
    }
  }, [closeDialog, dialog, form, loadDeps, messageApi, selectedOrgId])

  const removeDep = useCallback(
    async (dep: OrgDep) => {
      if (selectedOrgId === null) return
      try {
        await deleteDep(dep.id)
        await loadDeps(selectedOrgId)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '删除科室失败')
      }
    },
    [loadDeps, messageApi, selectedOrgId],
  )

  const columns: TableColumnsType<OrgDep> = [
    { title: '科室名称', dataIndex: 'name', width: COLUMN_WIDTHS.name },
    {
      title: '科室编码',
      dataIndex: 'code',
      width: COLUMN_WIDTHS.code,
      render: (code: string) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>{code}</span>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      render: (createTime: string) => (
        <Typography.Text type="secondary" style={{ fontVariantNumeric: 'tabular-nums' }}>
          {createTime}
        </Typography.Text>
      ),
    },
  ]

  // Selection context only: no org mutation is offered here (task 6.1 owns it).
  const aside = (
    <ConsolePanel>
      <OrgTree
        nodes={orgs}
        loading={orgTreeLoading}
        hint="选中机构后,右侧仅列出该机构下的科室。"
        selectedOrgId={selectedOrgId}
        onSelect={(node) => {
          setSelectedOrgId(node ? node.id : null)
          setPage(1)
        }}
        onRefresh={reloadOrgTree}
      />
    </ConsolePanel>
  )

  const editing = dialog?.kind === 'edit' ? dialog.dep : null

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsoleWorkArea
        aside={aside}
      >
        <ConsolePanel>
          <ConsoleToolRow
            trailing={
              canManage ? (
                <ConsoleAddButton label="新建科室" onClick={() => openDialog({ kind: 'create' })} />
              ) : undefined
            }
          >
            <Input
              allowClear
              style={{ width: 220 }}
              placeholder="科室编码 / 名称"
              aria-label="科室编码 / 名称"
              value={queryDraft}
              onChange={(event) => setQueryDraft(event.target.value)}
              onPressEnter={runQuery}
            />
            <ConsoleQueryButton onClick={runQuery} />
          </ConsoleToolRow>

          <ConsoleTable<OrgDep>
            rowKey={(dep) => dep.id}
            columns={columns}
            dataSource={pageRows}
            loading={listLoading}
            actions={
              canManage
                ? {
                    width: COLUMN_WIDTHS.actions,
                    render: (dep) => [
                      <ConsoleAction
                        key="edit"
                        label="编辑科室"
                        onClick={() => openDialog({ kind: 'edit', dep })}
                      />,
                      <Popconfirm
                        key="remove"
                        title="删除科室"
                        description={`确定删除「${dep.name}」吗?`}
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                        onConfirm={() => void removeDep(dep)}
                      >
                        <ConsoleAction label="删除科室" icon="remove" danger onClick={() => undefined} />
                      </Popconfirm>,
                    ],
                  }
                : undefined
            }
            onRefresh={() => {
              if (selectedOrgId !== null) void loadDeps(selectedOrgId)
            }}
          />

          <ConsolePagination
            total={filtered.length}
            current={page}
            pageSize={PAGE_SIZE}
            showSizeChanger={false}
            onChange={(next) => setPage(next)}
          />
        </ConsolePanel>
      </ConsoleWorkArea>

      <Drawer
        open={!!dialog}
        title={dialog?.kind === 'edit' ? '编辑科室' : '新建科室'}
        width={500}
        onClose={closeDialog}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeDialog}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitDialog()}>
              保存
            </Button>
          </div>
        }
      >
        <div style={{ marginBottom: 16 }}>
          <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
            上级机构
          </Typography.Text>
          <div>{selectedOrg?.name ?? '未选中机构'}</div>
        </div>
        <Form<DepFormValues> form={form} layout="vertical" disabled={saving}>
          <Form.Item
            name="code"
            label="科室编码"
            rules={[{ required: true, message: '请输入科室编码' }]}
          >
            {/* The code routes DICOM/HL7 traffic and never changes after creation. */}
            <Input placeholder="小写英文,如 radiology / us / endo" disabled={!!editing} autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="name"
            label="科室名称"
            rules={[{ required: true, message: '请输入科室名称' }]}
          >
            <Input placeholder="如 病理科" autoComplete="off" />
          </Form.Item>
        </Form>
      </Drawer>

    </div>
  )
}

/** Depth-first lookup of the selected org node (the aside's context label). */
function findOrg(nodes: OrgNode[], id: number): OrgNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children) {
      const found = findOrg(node.children, id)
      if (found) return found
    }
  }
  return null
}

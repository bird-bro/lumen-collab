import { Button, Drawer, Form, Input, Popconfirm, Select, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { createDep, deleteDep, pageDep, updateDep } from '@/api/org-deps'
import { orgTree } from '@/api/orgs'
import { ApiError } from '@/api/client'
import type { OrgDep, OrgNode, Page } from '@/api/types'
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
  readForm,
} from '../console-shell'
import { OrgTree } from '../org-tree'

/**
 * 科室管理 rewrite (org-management-redesign-web tasks 3.1-3.6; spec delta
 * `web-system-console` § Department Campus Filter Tree / § Department Table
 * Management / § Department Code Handling).
 *
 * Layout (design D4/D5): the LEFT tree is a hospital/campus FILTER only - it
 * never renders departments and offers no mutation (all department verbs live
 * in the right table). Selecting the hospital root means "all campuses" and
 * issues the page query WITHOUT `orgId`; selecting a campus filters to it.
 * The RIGHT table is server-side paginated (`pageDep`) with keyword/status
 * filters; cross-campus rows always show their campus names, so same-code
 * departments from different campuses stay distinguishable (delta: Same code
 * across campuses).
 *
 * Dialog (D5): the owner MUST be a live, enabled campus - the campus select
 * lists enabled campuses only, prefills when the filter sits on a campus, and
 * stays explicit (required) when the hospital root is selected. The code is
 * immutable after creation: the edit form disables it and the PUT body cannot
 * carry it (type-level, org-management-redesign-web 1.3). A same-campus
 * duplicate (A0712) surfaces on the code field with the dialog staying open.
 *
 * Permission gating (D6): `system:dep:list` gates every mutation affordance;
 * without it the tree and table stay readable.
 */

const COLUMN_WIDTHS = { campus: 120, name: 220, status: 90, audit: 170, actions: 130 } as const

const PAGE_SIZE = 10

interface DepFormValues {
  orgId: number
  code: string
  name: string
  status: number
}

type DepDialog = { kind: 'create' } | { kind: 'edit'; dep: OrgDep }

export default function DepsPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:dep:list')
  // button-level gating (button-perm-enforcement-web): each affordance answers to
  // its own perms string (the V15 seeded F nodes pinned server-side)
  const canCreate = canManage && hasPerm('system:dep:create')
  const canUpdate = canManage && hasPerm('system:dep:update')
  const canDelete = canManage && hasPerm('system:dep:delete')
  const { token } = theme.useToken()

  const [orgs, setOrgs] = useState<OrgNode[]>([])
  const [orgTreeLoading, setOrgTreeLoading] = useState(true)
  const [selectedOrgId, setSelectedOrgId] = useState<number | null>(null)
  const [keywordDraft, setKeywordDraft] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<number | null>(null)
  const [pageNum, setPageNum] = useState(1)
  const [page, setPage] = useState<Page<OrgDep> | null>(null)
  const [listLoading, setListLoading] = useState(true)
  const [dialog, setDialog] = useState<DepDialog | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<DepFormValues>()
  const [messageApi, messageHolder] = message.useMessage()

  // Org identity maps for the campus column and the dialog's campus select.
  const orgById = useMemo(() => {
    const map = new Map<number, OrgNode>()
    const walk = (nodes: OrgNode[]) => {
      for (const node of nodes) {
        map.set(node.id, node)
        if (node.children) walk(node.children)
      }
    }
    walk(orgs)
    return map
  }, [orgs])

  const selectedOrg = selectedOrgId === null ? null : (orgById.get(selectedOrgId) ?? null)
  const isCampusSelected = selectedOrg !== null && selectedOrg.upId !== 0
  /** Only a live, ENABLED campus may own a department (delta: Department create campus context). */
  const enabledCampuses = useMemo(
    () =>
      orgs.flatMap((root) => (root.children ?? []).filter((child) => child.status === 1)),
    [orgs],
  )

  // Tree: one fetch on mount; the OrgTree auto-selects the first root.
  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const tree = await orgTree()
        if (alive) setOrgs(tree)
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

  const loadPage = useCallback(
    async (effective: {
      orgId: number | undefined
      keyword: string | undefined
      status: number | undefined
      pageNum: number
    }) => {
      setListLoading(true)
      try {
        // The all-campuses view carries NO orgId key at all (absent = every campus).
        setPage(
          await pageDep({
            pageNum: effective.pageNum,
            pageSize: PAGE_SIZE,
            ...(effective.orgId !== undefined ? { orgId: effective.orgId } : {}),
            ...(effective.keyword ? { keyword: effective.keyword } : {}),
            ...(effective.status !== undefined ? { status: effective.status } : {}),
          }),
        )
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '科室列表加载失败')
      } finally {
        setListLoading(false)
      }
    },
    [messageApi],
  )

  useEffect(() => {
    void loadPage({
      orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
      keyword: keyword.trim() || undefined,
      status: statusFilter ?? undefined,
      pageNum,
    })
  }, [isCampusSelected, keyword, loadPage, pageNum, selectedOrgId, statusFilter])

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

  const runQuery = () => {
    setKeyword(keywordDraft)
    setPageNum(1)
  }

  const closeDialog = useCallback(() => {
    setDialog(null)
    form.resetFields()
  }, [form])

  const openDialog = useCallback(
    (next: DepDialog) => {
      if (next.kind === 'create') {
        // Campus-filtered view prefills the owner; the hospital-root view leaves
        // it empty and REQUIRES an explicit campus before submission.
        const prefill = isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined
        form.setFieldsValue({ orgId: prefill, code: '', name: '', status: 1 })
      } else {
        form.setFieldsValue({
          orgId: next.dep.orgId,
          code: next.dep.code,
          name: next.dep.name,
          status: next.dep.status,
        })
      }
      setDialog(next)
    },
    [form, isCampusSelected, selectedOrgId],
  )

  const submitDialog = useCallback(async () => {
    if (!dialog) return
    // A rejected validateFields() must never escape the drawer footer submit handler.
    const values = await readForm(form)
    if (!values) return
    setSaving(true)
    try {
      if (dialog.kind === 'edit') {
        // The code is immutable after creation - the PUT carries name/owner/status only.
        await updateDep(dialog.dep.id, {
          name: values.name,
          orgId: values.orgId,
          status: values.status,
        })
      } else {
        await createDep({ orgId: values.orgId, code: values.code, name: values.name, status: values.status })
      }
      closeDialog()
      await loadPage({
        orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
        keyword: keyword.trim() || undefined,
        status: statusFilter ?? undefined,
        pageNum,
      })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'A0712') {
        // Duplicate live code within the campus: the message lands ON the code
        // field and the dialog STAYS OPEN (delta: Duplicate code in one campus).
        form.setFields([{ name: 'code', errors: [error.msg] }])
      } else {
        messageApi.error(error instanceof ApiError ? error.msg : '保存科室失败')
      }
    } finally {
      setSaving(false)
    }
  }, [
    closeDialog,
    dialog,
    form,
    isCampusSelected,
    keyword,
    loadPage,
    messageApi,
    pageNum,
    selectedOrgId,
    statusFilter,
  ])

  const removeDep = useCallback(
    async (dep: OrgDep) => {
      try {
        await deleteDep(dep.id)
        await loadPage({
          orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
          keyword: keyword.trim() || undefined,
          status: statusFilter ?? undefined,
          pageNum,
        })
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '删除科室失败')
      }
    },
    [isCampusSelected, keyword, loadPage, messageApi, pageNum, selectedOrgId, statusFilter],
  )

  const columns: TableColumnsType<OrgDep> = [
    {
      title: '科室',
      dataIndex: 'name',
      width: COLUMN_WIDTHS.name,
      render: (_: unknown, row: OrgDep) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {row.name}
          </span>
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
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: COLUMN_WIDTHS.status,
      align: 'center',
      render: (status: number) =>
        status === 1 ? (
          <Tag
            bordered={false}
            color="blue"
            style={{ marginInlineEnd: 0, fontSize: token.fontSize }}
          >
            启用
          </Tag>
        ) : (
          <Tag
            bordered={false}
            style={{
              marginInlineEnd: 0,
              fontSize: token.fontSize,
              color: token.colorTextSecondary,
              background: token.colorBgContainer,
              borderColor: token.colorBorder,
            }}
          >
            停用
          </Tag>
        ),
    },
    {
      title: '院区',
      dataIndex: 'orgId',
      width: COLUMN_WIDTHS.campus,
      render: (orgId: number) => orgById.get(orgId)?.name ?? '—',
    },
    {
      title: '创建人 / 创建时间',
      dataIndex: 'createTime',
      width: COLUMN_WIDTHS.audit,
      render: (_: unknown, row: OrgDep) => (
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

  // Selection context only: no org mutation is offered here (the org page owns it).
  const aside = (
    <ConsolePanel>
      <OrgTree
        nodes={orgs}
        loading={orgTreeLoading}
        selectedOrgId={selectedOrgId}
        onSelect={(node) => {
          setSelectedOrgId(node ? node.id : null)
          setPageNum(1)
        }}
        onRefresh={reloadOrgTree}
      />
    </ConsolePanel>
  )

  const editing = dialog?.kind === 'edit' ? dialog.dep : null

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsoleWorkArea aside={aside}>
        <ConsolePage>
          <ConsoleToolRow
            trailing={
              canCreate ? (
                <ConsoleAddButton label="新建科室" onClick={() => openDialog({ kind: 'create' })} />
              ) : undefined
            }
          >
            <Input
              allowClear
              style={{ width: 220 }}
              placeholder="科室编码 / 名称"
              aria-label="科室编码 / 名称"
              value={keywordDraft}
              onChange={(event) => setKeywordDraft(event.target.value)}
              onPressEnter={runQuery}
            />
            <Select<number | null>
              allowClear
              style={{ width: 120 }}
              placeholder="状态"
              aria-label="状态过滤"
              value={statusFilter}
              onChange={(next) => {
                setStatusFilter(next ?? null)
                setPageNum(1)
              }}
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
            <ConsoleQueryButton onClick={runQuery} />
          </ConsoleToolRow>

          <ConsoleTable<OrgDep>
            rowKey={(row) => row.id}
            columns={columns}
            dataSource={page?.records ?? []}
            loading={listLoading}
            actions={
              canManage
                ? {
                    width: COLUMN_WIDTHS.actions,
                    render: (row) => [
                      canUpdate ? (
                        <ConsoleAction
                          key="edit"
                          label="编辑科室"
                          onClick={() => openDialog({ kind: 'edit', dep: row })}
                        />
                      ) : null,
                      canDelete ? (
                        <Popconfirm
                          key="remove"
                          title="删除科室"
                          description={`确定删除「${row.name}」吗?`}
                          okText="删除"
                          cancelText="取消"
                          okButtonProps={{ danger: true }}
                          onConfirm={() => void removeDep(row)}
                        >
                          <ConsoleAction label="删除科室" icon="remove" danger onClick={() => undefined} />
                        </Popconfirm>
                      ) : null,
                    ],
                  }
                : undefined
            }
            onRefresh={() => {
              void loadPage({
                orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
                keyword: keyword.trim() || undefined,
                status: statusFilter ?? undefined,
                pageNum,
              })
            }}
          />

          <ConsolePagination
            total={page?.total ?? 0}
            current={pageNum}
            pageSize={PAGE_SIZE}
            showSizeChanger={false}
            onChange={(next) => setPageNum(next)}
          />
        </ConsolePage>
      </ConsoleWorkArea>

      <Drawer
        open={!!dialog}
        title={editing ? '编辑科室' : '新建科室'}
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
        <p style={{ margin: '0 0 16px', fontSize: token.fontSizeSM, color: token.colorTextTertiary }}>
          {editing ? `科室编码创建后不可修改:${editing.code}` : '科室必须归属一个启用中的院区。'}
        </p>
        <Form<DepFormValues> form={form} layout="vertical" disabled={saving}>
          <Form.Item
            name="orgId"
            label="所属院区"
            rules={[{ required: true, message: '请选择所属院区' }]}
          >
            <Select
              placeholder="选择院区"
              options={enabledCampuses.map((campus) => ({ value: campus.id, label: campus.name }))}
            />
          </Form.Item>
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
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select
              aria-label="状态"
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}

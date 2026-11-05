import { Button, Drawer, Form, Input, message, theme } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { createOrg, deleteOrg, orgTree, updateOrg } from '@/api/orgs'
import type { OrgNode } from '@/api/types'
import { ApiError } from '@/api/client'
import { usePerm } from '@/utils/perm'
import { ConsolePanel, readForm } from '../console-shell'
import { OrgTree } from '../org-tree'

/**
 * 机构管理 (batch 6a, task 6.1; spec `specs/web-system-console`
 * § Org Tree Management).
 *
 * Layout: the user-confirmed prototype `org-deps.html` models the org as the
 * LEFT `.tree-panel` of a two-zone work area; on this page the tree IS the
 * work area (brief 6.1 「树为主体的工作区」), so the panel is that same block
 * (tree-hd + tree + tree-hint + the dashed `.ghost` 「新建根机构」 foot button,
 * which is the spec's multi-root affordance), widened from the prototype's
 * 250px so a row's hover action glyphs fit on one line. The modal shape
 * (上级机构 context line + 名称 + 取消/保存) is the prototype's `.modal` block.
 *
 * Data flow: local hooks only - one `orgTree()` on mount, and every mutation
 * re-queries (spec 「the tree reflects the result」). No URL is built here: all
 * traffic goes through the typed `@/api/orgs` client.
 *
 * Permission gating: the page's own documented perms string `system:org:list`
 * (seed menu id 7 in the backend `V2__seed.sql`, also the backend
 * `@PreAuthorize` on the list endpoint) gates the whole mutation surface - create-child, edit, delete
 * and 新建根机构. No new perms code is invented (brief § 权限门控).
 */

/** Prototype `.tree-panel{flex:0 0 250px}` widened for the row action glyphs. */
const ORG_PANEL_WIDTH = 360

interface OrgFormValues {
  name: string
}

type OrgDialog =
  | { kind: 'create-child'; parent: OrgNode }
  | { kind: 'create-root' }
  | { kind: 'edit'; node: OrgNode }

/** The dialog's title + submit label, per the prototype's modal header habit. */
function dialogCopy(dialog: OrgDialog): { title: string; context: string | null } {
  if (dialog.kind === 'create-child') {
    return { title: '新建机构', context: `上级机构:${dialog.parent.name}` }
  }
  if (dialog.kind === 'create-root') {
    return { title: '新建根机构', context: '上级机构:无(作为根节点)' }
  }
  return { title: '编辑机构', context: dialog.node.name }
}

export default function OrgsPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:org:list')
  const { token } = theme.useToken()

  const [nodes, setNodes] = useState<OrgNode[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedOrgId, setSelectedOrgId] = useState<number | null>(null)
  const [dialog, setDialog] = useState<OrgDialog | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<OrgFormValues>()
  const [messageApi, messageHolder] = message.useMessage()

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setNodes(await orgTree())
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '机构树加载失败')
    } finally {
      setLoading(false)
    }
  }, [messageApi])

  useEffect(() => {
    void reload()
  }, [reload])

  const closeDialog = useCallback(() => {
    setDialog(null)
    form.resetFields()
  }, [form])

  const openDialog = useCallback(
    (next: OrgDialog) => {
      form.setFieldsValue({ name: next.kind === 'edit' ? next.node.name : '' })
      setDialog(next)
    },
    [form],
  )

  const submitDialog = useCallback(async () => {
    if (!dialog) return
    // A rejected validateFields() must never escape the drawer footer submit handler.
    const values = await readForm(form)
    if (!values) return
    setSaving(true)
    try {
      if (dialog.kind === 'edit') await updateOrg(dialog.node.id, { name: values.name })
      else await createOrg({ upId: dialog.kind === 'create-root' ? 0 : dialog.parent.id, name: values.name })
      closeDialog()
      await reload()
    } catch (error) {
      // A-class failures (unknown parent, blank name): the backend msg lands on
      // the floating message and the dialog STAYS OPEN (brief § 错误反馈).
      messageApi.error(error instanceof ApiError ? error.msg : '保存机构失败')
    } finally {
      setSaving(false)
    }
  }, [closeDialog, dialog, form, messageApi, reload])

  const removeOrg = useCallback(
    async (node: OrgNode) => {
      try {
        await deleteOrg(node.id)
        if (selectedOrgId === node.id) setSelectedOrgId(null)
        await reload()
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '删除机构失败')
      }
    },
    [messageApi, reload, selectedOrgId],
  )

  const copy = dialog ? dialogCopy(dialog) : null

  return (
    <div style={{ height: '100%', minWidth: ORG_PANEL_WIDTH }}>
      {messageHolder}
      <ConsolePanel>
        <OrgTree
          nodes={nodes}
          loading={loading}
          hint="选中机构后,悬停或选中该行即可新建下级机构、编辑或删除;「新建根机构」创建并列的第二棵根。"
          selectedOrgId={selectedOrgId}
          onSelect={(node) => setSelectedOrgId(node ? node.id : null)}
          onCreateRoot={canManage ? () => openDialog({ kind: 'create-root' }) : undefined}
          actions={
            canManage
              ? {
                  onCreateChild: (parent) => openDialog({ kind: 'create-child', parent }),
                  onEdit: (node) => openDialog({ kind: 'edit', node }),
                  onDelete: (node) => void removeOrg(node),
                }
              : undefined
          }
          onRefresh={() => void reload()}
        />
      </ConsolePanel>

      <Drawer
        open={!!dialog}
        title={copy?.title}
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
        {copy?.context ? (
          <p style={{ margin: '0 0 16px', fontSize: token.fontSizeSM, color: token.colorTextTertiary }}>
            {copy.context}
          </p>
        ) : null}
        <Form<OrgFormValues> form={form} layout="vertical" requiredMark disabled={saving}>
          <Form.Item
            name="name"
            label="机构名称"
            rules={[{ required: true, message: '请输入机构名称' }]}
          >
            <Input placeholder="如 示例医院 / 华东分院" autoComplete="off" />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}

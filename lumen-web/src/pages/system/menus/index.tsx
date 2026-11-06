import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Radio,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  TreeSelect,
  message,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { createMenu, menuTree, removeMenu, updateMenu } from '@/api/menus'
import { ApiError } from '@/api/client'
import type { MenuNode } from '@/api/types'
import { usePerm } from '@/utils/perm'
import { ConsoleAction, ConsoleAddButton, ConsolePanel, ConsoleToolRow, readForm } from '../console-shell'

/**
 * 菜单管理 (menu-management-web, tasks 2.1-2.5; spec `specs/web-system-console`
 * § Menu Management, superseding the read-only Menu Tree Visibility view).
 * The management table-tree: per-node 新增下级/编辑/删除 behind the
 * `system:menu:list` permission (server stays authoritative), a type-aware
 * create/edit drawer authoring button (`F`) `perms` strings, and backend
 * rejections surfaced through the message API. Data rides the REAL backend
 * through the vite proxy (no mock layer - frontend-backend-first ruling).
 *
 * Dialog guards (spec scenario "Dialog guards the parent choice"): the parent
 * TreeSelect omits button (`F`) nodes - they are leaves - and, when editing,
 * the node itself and its own subtree. Editing pre-fills from the row; the
 * create default type follows the parent (under a `C` menu → button, under an
 * `M` group or at the root → menu/directory).
 */

const TYPE_LABELS: Record<string, string> = {
  M: '目录',
  C: '菜单',
  F: '按钮',
}

type MenuType = 'M' | 'C' | 'F'

interface MenuFormValues {
  type: MenuType
  upId?: number
  title?: string
  perms?: string
  path?: string
  component?: string
  icon?: string
  sort?: number
  visible: boolean
}

type DrawerState =
  | { mode: 'create'; parent: MenuNode | null }
  | { mode: 'edit'; node: MenuNode }

interface ParentOption {
  title: ReactNode
  value: number
  children?: ParentOption[]
}

/**
 * The parent TreeSelect data: every non-button node except the excluded
 * subtree (the edited node itself - its descendants drop out with it, so the
 * guard is one skip). Root-level creation does not render the selector at all.
 */
function buildParentOptions(nodes: MenuNode[], excludeId: number | null): ParentOption[] {
  const options: ParentOption[] = []
  for (const node of nodes) {
    if (node.type === 'F') continue
    if (excludeId !== null && node.mid === excludeId) continue
    const children = node.children ? buildParentOptions(node.children, excludeId) : []
    options.push({
      title: node.title,
      value: node.mid,
      children: children.length > 0 ? children : undefined,
    })
  }
  return options
}

export default function MenusPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:menu:list')
  const [tree, setTree] = useState<MenuNode[] | null>(null)
  const [messageApi, messageHolder] = message.useMessage()
  const [drawer, setDrawer] = useState<DrawerState | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<MenuFormValues>()
  const formType = Form.useWatch('type', form)

  const reloadTree = useCallback(async () => {
    try {
      setTree(await menuTree())
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '菜单树加载失败')
    }
  }, [messageApi])

  useEffect(() => {
    void reloadTree()
  }, [reloadTree])

  const closeDrawer = useCallback(() => {
    setDrawer(null)
    form.resetFields()
  }, [form])

  const openCreate = useCallback(
    (parent: MenuNode | null) => {
      form.setFieldsValue({
        type: parent ? (parent.type === 'C' ? 'F' : 'C') : 'M',
        upId: parent?.mid,
        sort: 1,
        visible: true,
      })
      setDrawer({ mode: 'create', parent })
    },
    [form],
  )

  const openEdit = useCallback(
    (node: MenuNode) => {
      form.setFieldsValue({
        type: node.type as MenuType,
        upId: node.upId,
        title: node.title,
        perms: node.perms ?? '',
        path: node.path ?? '',
        component: node.component ?? '',
        icon: node.icon ?? '',
        sort: node.sort ?? 1,
        visible: node.visible,
      })
      setDrawer({ mode: 'edit', node })
    },
    [form],
  )

  const submit = useCallback(async () => {
    if (!drawer) return
    const values = await readForm(form)
    if (!values) return
    setSaving(true)
    const body = {
      upId: drawer.mode === 'create' && !drawer.parent ? 0 : (values.upId as number),
      type: values.type,
      title: (values.title ?? '').trim(),
      perms: values.perms?.trim() || null,
      path: values.path?.trim() || null,
      component: values.component?.trim() || null,
      icon: values.icon?.trim() || null,
      sort: values.sort ?? 1,
      visible: values.visible ? 1 : 0,
    }
    try {
      if (drawer.mode === 'edit') {
        await updateMenu(drawer.node.mid, body)
      } else {
        await createMenu(body)
      }
      closeDrawer()
      await reloadTree()
    } catch (error) {
      // structural rejections (live children, button-leaf, cycle) land as the
      // backend's A-class msg; the drawer stays open for correction.
      messageApi.error(error instanceof ApiError ? error.msg : '保存菜单失败')
    } finally {
      setSaving(false)
    }
  }, [closeDrawer, drawer, form, messageApi, reloadTree])

  const remove = useCallback(
    async (node: MenuNode) => {
      try {
        await removeMenu(node.mid)
        await reloadTree()
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '删除菜单失败')
      }
    },
    [messageApi, reloadTree],
  )

  const columns: TableColumnsType<MenuNode> = [
    {
      title: '标题',
      dataIndex: 'title',
      render: (title: string, node: MenuNode) => (
        <Space size={6}>
          <span>{title}</span>
          <Tag style={{ marginInlineEnd: 0 }}>{TYPE_LABELS[node.type] ?? node.type}</Tag>
          {node.perms ? (
            <Tag color="blue" style={{ marginInlineEnd: 0, fontFamily: 'monospace' }}>
              {node.perms}
            </Tag>
          ) : null}
        </Space>
      ),
    },
    {
      title: '排序',
      dataIndex: 'sort',
      width: 80,
      render: (sort: number | null) => <Tag style={{ marginInlineEnd: 0 }}>{sort ?? '—'}</Tag>,
    },
    {
      title: '可见',
      dataIndex: 'visible',
      width: 80,
      render: (visible: boolean) => (visible ? '显示' : '隐藏'),
    },
  ]

  const actionColumn: TableColumnsType<MenuNode>[number] = {
    key: 'actions',
    width: 230,
    render: (_: unknown, node: MenuNode) => (
      <Space size={4}>
        <ConsoleAction
          label="新增下级"
          icon="assign"
          disabled={node.type === 'F'}
          onClick={() => openCreate(node)}
        />
        <ConsoleAction label="编辑节点" onClick={() => openEdit(node)} />
        <Popconfirm
          title="删除菜单"
          description="删除后该节点的角色授权将一并解除,确认删除?"
          okText="删除"
          cancelText="取消"
          onConfirm={() => {
            void remove(node)
          }}
        >
          <ConsoleAction label="删除节点" icon="remove" danger onClick={() => undefined} />
        </Popconfirm>
      </Space>
    ),
  }

  const editing = drawer?.mode === 'edit' ? drawer.node : null
  const parentOptions = buildParentOptions(tree ?? [], editing ? editing.mid : null)

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePanel>
        <div style={{ padding: 16, overflow: 'auto' }}>
          <ConsoleToolRow
            trailing={
              canManage ? (
                <ConsoleAddButton label="新建根节点" onClick={() => openCreate(null)} />
              ) : undefined
            }
          >
            {null}
          </ConsoleToolRow>
          {tree === null ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <Spin />
            </div>
          ) : (
            <Table<MenuNode>
              rowKey={(node) => node.mid}
              columns={canManage ? [...columns, actionColumn] : columns}
              dataSource={tree}
              defaultExpandAllRows
              pagination={false}
              size="middle"
            />
          )}
        </div>
      </ConsolePanel>

      <Drawer
        open={!!drawer}
        title={
          drawer?.mode === 'edit'
            ? `编辑节点 - ${drawer.node.title}`
            : drawer?.parent
              ? `新增下级 - ${drawer.parent.title}`
              : '新建根节点'
        }
        width={500}
        onClose={closeDrawer}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeDrawer}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submit()}>
              保 存
            </Button>
          </div>
        }
      >
        <Form<MenuFormValues> form={form} layout="vertical" disabled={saving}>
          <Form.Item name="type" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Radio.Group>
              <Radio value="M">目录</Radio>
              <Radio value="C">菜单</Radio>
              <Radio value="F">按钮</Radio>
            </Radio.Group>
          </Form.Item>
          {drawer?.mode === 'create' && drawer.parent ? (
            <Form.Item
              name="upId"
              label="上级节点"
              rules={[{ required: true, message: '请选择上级节点' }]}
            >
              <TreeSelect
                treeData={parentOptions}
                treeDefaultExpandAll
                placeholder="选择上级节点"
              />
            </Form.Item>
          ) : null}
          {drawer?.mode === 'edit' ? (
            <Form.Item
              name="upId"
              label="上级节点"
              rules={[{ required: true, message: '请选择上级节点' }]}
            >
              <TreeSelect
                treeData={parentOptions}
                treeDefaultExpandAll
                placeholder="选择上级节点"
              />
            </Form.Item>
          ) : null}
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="perms"
            label="权限标识"
            extra={
              formType === 'F'
                ? '约定 模块:资源:动作（如 system:user:create）'
                : '页面权限串（如 system:user:list），留空表示纯导航节点'
            }
          >
            <Input autoComplete="off" />
          </Form.Item>
          {formType !== 'F' ? (
            <>
              <Form.Item name="path" label="前端路径">
                <Input autoComplete="off" placeholder="路由 path（目录分组可留空）" />
              </Form.Item>
              <Form.Item name="component" label="前端组件">
                <Input autoComplete="off" placeholder="组件路径（如 system/user/index）" />
              </Form.Item>
            </>
          ) : null}
          <Form.Item name="icon" label="图标">
            <Input autoComplete="off" placeholder="AntD 图标名（选填）" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="visible" label="可见" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}

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
  Tooltip,
  TreeSelect,
  message,
  theme,
} from 'antd'
import type { TableColumnsType } from 'antd'
import {
  CheckCircleFilled,
  DeleteOutlined,
  EditOutlined,
  MinusCircleFilled,
  PlusCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useCallback, useEffect, useState } from 'react'
import type { Key, ReactNode } from 'react'
import { createMenu, menuTree, removeMenu, updateMenu } from '@/api/menus'
import { ApiError } from '@/api/client'
import type { MenuNode } from '@/api/types'
import { usePerm } from '@/utils/perm'
import { ConsolePanel, readForm } from '../console-shell'

/**
 * 菜单管理 (button-perm-enforcement-web tasks 2.2 + 3.1/3.2; spec
 * `specs/web-system-console` § Menu Management + user instruction 2026-11-06):
 * visual alignment with the 随访 reference (`随访/fu_web/src/views/system/menu/
 * index.vue`) - the 菜单/类型/路径/组件/权限标识/显示/排序 column set, a menu-name
 * filter tool row (subtree-keep filter applied on 查询/Enter), fixed-right icon
 * actions each gated by its own button perm, and the reference drawer wording.
 * Table alignment (user instruction 2026-11-06): the 显示/排序/操作 cells are
 * centered, the 类型 body cell stays left-aligned, and every header cell stays
 * centered without moving the left-aligned text columns' body content. Follow-up polish (same day): wider
 * 类型 column (工作站 tags use a dedicated purple color), 类型/显示 tag fonts
 * at the table body size (token.fontSize), the create/edit type radios follow the
 * 工作站/页面/目录/按钮 order (user instruction 2026-11-06), and a
 * larger gap between the three row actions (Space - the raw adjacent-button
 * margin invited mis-clicks), and the header 刷新 icon colored token.colorPrimary
 * to match the console-shell refresh buttons.
 * lumen deltas kept: no 重定向/是否缓存 (model columns trimmed), and 权限标识 stays
 * editable for every type (lumen C nodes carry page perms). Runtime rides the vite
 * proxy to the REAL backend (no mock layer - frontend-backend-first ruling).
 */

const TYPE_LABELS: Record<string, string> = {
  M: '目录',
  C: '页面',
  F: '按钮',
  W: '工作站',
}

type MenuType = 'M' | 'C' | 'F' | 'W'

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
  remark?: string
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
 * The parent TreeSelect data: every non-button node except the excluded subtree
 * (the edited node itself - its descendants drop out with it, so the guard is
 * one skip; the reference's parentOptions splice does the same).
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

/**
 * Default expansion stays shallow: expand only the first tree layer so a
 * deep menu/button tree does not flood the page on load; deeper levels open
 * on demand.
 */
function defaultExpandedKeys(nodes: MenuNode[], maxDepth = 1): number[] {
  const keys: number[] = []
  const walk = (list: MenuNode[], depth: number) => {
    for (const node of list) {
      if (node.children?.length && depth <= maxDepth) {
        keys.push(node.mid)
      }
      if (node.children?.length) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(nodes, 1)
  return keys
}

/**
 * The reference's filterTree semantics: a hit node keeps its whole subtree, a
 * child hit keeps the ancestor chain, everything else is pruned.
 */
function filterTree(nodes: MenuNode[], keyword: string): MenuNode[] {
  const key = keyword.trim().toLowerCase()
  if (!key) return nodes
  const walk = (list: MenuNode[]): MenuNode[] =>
    list
      .map((node) => {
        const hit = (node.title ?? '').toLowerCase().includes(key)
        const kids = node.children ? walk(node.children) : []
        if (hit || kids.length > 0) {
          return { ...node, children: kids.length > 0 ? kids : node.children }
        }
        return null
      })
      .filter((node): node is MenuNode => node !== null)
  return walk(nodes)
}

export default function MenusPage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [tree, setTree] = useState<MenuNode[] | null>(null)
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([])
  const [messageApi, messageHolder] = message.useMessage()
  const [drawer, setDrawer] = useState<DrawerState | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<MenuFormValues>()
  const formType = Form.useWatch('type', form)

  // reference filter: draft input, applied on 查询/Enter (appliedTitle)
  const [titleDraft, setTitleDraft] = useState('')
  const [appliedTitle, setAppliedTitle] = useState('')

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

  // Keep the shallow default after tree refreshes while allowing manual expansion.
  useEffect(() => {
    if (tree) setExpandedKeys(defaultExpandedKeys(tree))
  }, [tree])

  const closeDrawer = useCallback(() => {
    setDrawer(null)
    form.resetFields()
  }, [form])

  const openCreate = useCallback(
    (parent: MenuNode | null) => {
      form.setFieldsValue({
        type: parent ? (parent.type === 'C' ? 'F' : 'C') : 'C',
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
        remark: node.remark ?? '',
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
      remark: values.remark?.trim() || null,
      // F rows hide the switch (unregistered field) - visible defaults to 1
      visible: values.visible === undefined ? 1 : values.visible ? 1 : 0,
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

  const canCreate = hasPerm('system:menu:create')
  const canUpdate = hasPerm('system:menu:update')
  const canDelete = hasPerm('system:menu:delete')

  const columns: TableColumnsType<MenuNode> = [
    {
      title: '菜单',
      dataIndex: 'title',
      render: (title: string) => <span>{title}</span>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 140,
      onCell: () => ({ style: { textAlign: 'left' } }),
      render: (type: string) => (
        <Tag
          color={
            type === 'M'
              ? 'gold'
              : type === 'C'
                ? 'blue'
                : type === 'W'
                  ? 'purple'
                  : 'default'
          }
          style={{ marginInlineEnd: 0, fontSize: token.fontSize }}
        >
          {TYPE_LABELS[type] ?? type}
        </Tag>
      ),
    },
    {
      title: '路径',
      dataIndex: 'path',
      ellipsis: true,
      render: (path: string | null) => path ?? '',
    },
    {
      title: '组件',
      dataIndex: 'component',
      ellipsis: true,
      render: (component: string | null) => component ?? '',
    },
    {
      title: '权限标识',
      dataIndex: 'perms',
      ellipsis: true,
      render: (perms: string | null) =>
        perms ? (
          <span style={{ fontFamily: 'monospace' }}>{perms}</span>
        ) : null,
    },
    {
      title: '显示',
      dataIndex: 'visible',
      width: 100,
      align: 'center',
      render: (visible: boolean, node: MenuNode) =>
        node.type === 'F' ? (
          <span>-</span>
        ) : (
          <Tooltip title={visible ? '显示' : '隐藏'}>
            {visible ? (
              <CheckCircleFilled aria-label="显示" style={{ color: token.colorSuccess }} />
            ) : (
              <MinusCircleFilled aria-label="隐藏" style={{ color: token.colorTextQuaternary }} />
            )}
          </Tooltip>
        ),
    },
    {
      title: '排序',
      dataIndex: 'sort',
      width: 70,
      align: 'center',
    },
  ]

  const actionColumn: TableColumnsType<MenuNode>[number] = {
    title: (
      <span
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        操作
        <Tooltip title="刷新列表">
          <Button
            type="text"
            size="small"
            aria-label="刷新列表"
            icon={<ReloadOutlined style={{ color: token.colorPrimary }} />}
            style={{
              position: 'absolute',
              right: 0,
              top: '50%',
              transform: 'translateY(-50%)',
            }}
            onClick={() => {
              void reloadTree()
            }}
          />
        </Tooltip>
      </span>
    ),
    key: 'actions',
    // 130px console-wide action-column baseline (dicts keep their own 120px).
    width: 130,
    align: 'center',
    render: (_: unknown, node: MenuNode) => (
      // Space gap > the console-shell size-6: icon-only targets sit close enough
      // to mis-click at the default adjacent-button margin (user feedback).
      <Space size={12}>
        {canCreate ? (
          <Tooltip title="添加子菜单">
            <Button
              type="link"
              size="small"
              aria-label="添加子菜单"
              icon={<PlusCircleOutlined />}
              disabled={node.type === 'F'}
              onClick={() => openCreate(node)}
            />
          </Tooltip>
        ) : null}
        {canUpdate ? (
          <Tooltip title="编辑">
            <Button
              type="link"
              size="small"
              aria-label="编辑"
              icon={<EditOutlined />}
              onClick={() => openEdit(node)}
            />
          </Tooltip>
        ) : null}
        {canDelete ? (
          <Popconfirm
            title="删除菜单"
            description={`确认删除菜单「${node.title}」？存在子菜单时无法删除。`}
            okText="删除"
            cancelText="取消"
            onConfirm={() => {
              void remove(node)
            }}
          >
            <Tooltip title="删除">
              <Button
                type="link"
                size="small"
                danger
                aria-label="删除菜单"
                icon={<DeleteOutlined />}
                onClick={() => undefined}
              />
            </Tooltip>
          </Popconfirm>
        ) : null}
      </Space>
    ),
  }

  // Keep body content of text columns left-aligned while centering every header.
  const withCenteredHeaders = (cols: TableColumnsType<MenuNode>): TableColumnsType<MenuNode> =>
    cols.map((column) => ({
      ...column,
      onHeaderCell: () => ({ style: { textAlign: 'center' } }),
    }))

  const editing = drawer?.mode === 'edit' ? drawer.node : null
  const parentOptions = buildParentOptions(tree ?? [], editing ? editing.mid : null)

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePanel>
        <div style={{ height: '100%', padding: 16, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <Input
                allowClear
                style={{ width: 220 }}
                placeholder="菜单名"
                aria-label="菜单名"
                value={titleDraft}
                onChange={(event) => setTitleDraft(event.target.value)}
                onPressEnter={() => setAppliedTitle(titleDraft)}
              />
              <Button icon={<SearchOutlined />} onClick={() => setAppliedTitle(titleDraft)}>
                查询
              </Button>
            </div>
            {canCreate ? (
              <Button type="primary" icon={<PlusCircleOutlined />} onClick={() => openCreate(null)}>
                添加
              </Button>
            ) : null}
          </div>

          {tree === null ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <Spin />
            </div>
          ) : (
            <Table<MenuNode>
              rowKey={(node) => node.mid}
              tableLayout="fixed"
              columns={withCenteredHeaders([...columns, actionColumn])}
              dataSource={filterTree(tree, appliedTitle)}
              expandedRowKeys={expandedKeys}
              onExpandedRowsChange={(keys) => setExpandedKeys([...keys])}
              pagination={false}
              size="middle"
            />
          )}
        </div>
      </ConsolePanel>

      <Drawer
        open={!!drawer}
        title={drawer?.mode === 'edit' ? '编辑菜单' : '添加菜单'}
        width={460}
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
          {drawer?.mode === 'create' && drawer.parent ? (
            <Form.Item
              name="upId"
              label="上级菜单"
              rules={[{ required: true, message: '请选择上级菜单' }]}
            >
              <TreeSelect
                treeData={parentOptions}
                treeDefaultExpandAll
                placeholder="选择上级菜单"
              />
            </Form.Item>
          ) : null}
          {drawer?.mode === 'edit' ? (
            <Form.Item
              name="upId"
              label="上级菜单"
              rules={[{ required: true, message: '请选择上级菜单' }]}
            >
              <TreeSelect
                treeData={parentOptions}
                treeDefaultExpandAll
                placeholder="选择上级菜单"
              />
            </Form.Item>
          ) : null}
          <Form.Item name="title" label="菜单标题" rules={[{ required: true, message: '菜单标题不能为空' }]}>
            <Input autoComplete="off" placeholder="请输入标题" />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Radio.Group>
              <Radio value="W">工作站</Radio>
              <Radio value="C">页面</Radio>
              <Radio value="M">目录</Radio>
              <Radio value="F">按钮</Radio>
            </Radio.Group>
          </Form.Item>
          {formType !== 'F' && formType !== 'W' ? (
            <Form.Item name="path" label="路径">
              <Input autoComplete="off" placeholder="如 /system/user" />
            </Form.Item>
          ) : null}
          {formType === 'C' ? (
            <Form.Item name="component" label="组件">
              <Input autoComplete="off" placeholder="如 system/user/index" />
            </Form.Item>
          ) : null}
          {formType !== 'F' && formType !== 'W' ? (
            <Form.Item name="icon" label="图标">
              <Input autoComplete="off" placeholder="图标名" />
            </Form.Item>
          ) : null}
          {formType !== 'W' ? (
          <Form.Item
            name="perms"
            label="权限标识"
            extra={
              <span style={{ fontSize: 12 }}>
                {formType === 'F'
                  ? '约定 模块:资源:动作（如 system:user:create）'
                  : '页面权限串（如 system:user:list），留空表示纯导航节点'}
              </span>
            }
          >
            <Input autoComplete="off" placeholder="如 system:user:create" />
          </Form.Item>
          ) : null}
          <Form.Item name="sort" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea
              autoSize={{ minRows: 2, maxRows: 4 }}
              placeholder="请输入备注"
            />
          </Form.Item>
          {formType !== 'F' ? (
            <div
              data-menu-visible-row
              style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 24 }}
            >
              <label htmlFor="visible">是否显示</label>
              <Form.Item name="visible" valuePropName="checked" noStyle>
                <Switch id="visible" />
              </Form.Item>
            </div>
          ) : null}
        </Form>
      </Drawer>
    </div>
  )
}

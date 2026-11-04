import {
  BankOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { Button, Popconfirm, Space, Spin, Tooltip, Tree, Typography, theme } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { OrgNode } from '@/api/types'

/**
 * Shared org tree (batch 6a, tasks 6.1/6.2; spec `specs/web-system-console`
 * § Org Tree Management + § Department Management "the page SHALL present the
 * org tree as the selection context").
 *
 * Structure source: `prototype/org-deps.html` left `.tree-panel` - the
 * `.tree-hd` title (「机构」), the `.tree` body (`.t-node` rows: building glyph
 * + name), the `.tree-hint` footnote and the dashed `.ghost` 「新建根机构」
 * button at the foot (the spec's multi-root affordance). The prototype reveals
 * `.t-add` on row hover only; here a row's glyph group (下级 / 编辑 / 删除) is
 * revealed while the row is HOVERED, SELECTED, or has a delete confirmation
 * open - the last two states keep the trigger mounted so the Popconfirm panel
 * stays reachable (a purely hover-revealed trigger unmounts under the pointer
 * the moment it moves toward the confirm button).
 *
 * Transport-agnostic by design: the tree receives `nodes` + an `onRefresh`
 * callback, so the PAGE owns the `orgs.orgTree()` request lifecycle and no URL
 * is ever assembled in a component (`specs/web-app-shell` § API Client Seam).
 */

/** Prototype `.t-node svg.i{width:17px}` - the building glyph before the name. */
const NODE_ICON_SIZE = 17

/** id -> node, so a selection/expand callback resolves from AntD's key alone. */
function indexNodes(nodes: OrgNode[], map: Map<number, OrgNode> = new Map()): Map<number, OrgNode> {
  for (const node of nodes) {
    map.set(node.id, node)
    if (node.children) indexNodes(node.children, map)
  }
  return map
}

/** Every id that currently has children - the auto-expand set. */
function collectParentIds(nodes: OrgNode[], ids: number[] = []): number[] {
  for (const node of nodes) {
    if (node.children && node.children.length > 0) {
      ids.push(node.id)
      collectParentIds(node.children, ids)
    }
  }
  return ids
}

/** Structure-only tree data: the visible row is composed by `titleRender`. */
function toTreeData(nodes: OrgNode[]): DataNode[] {
  return nodes.map((node) => ({
    key: node.id,
    title: node.name,
    children: node.children ? toTreeData(node.children) : undefined,
  }))
}

export interface OrgTreeActionHandlers {
  /** 「新建下级机构」 on the row (prototype `.t-add`). */
  onCreateChild: (parent: OrgNode) => void
  /** 「编辑」 on the row. */
  onEdit: (node: OrgNode) => void
  /** 「删除」 on the row - the Popconfirm lives here, the page performs the DELETE. */
  onDelete: (node: OrgNode) => void
}

export interface OrgTreeProps {
  nodes: OrgNode[]
  loading?: boolean
  /** Panel heading (prototype `.tree-hd`). */
  title?: string
  /** Footnote under the tree (prototype `.tree-hint`). */
  hint?: string
  /** Selected org id; the page owns the selection because it scopes its work area. */
  selectedOrgId: number | null
  onSelect: (node: OrgNode | null) => void
  /**
   * The dashed foot button (spec multi-root). The PAGE hands it over only when
   * the session's perms allow org mutation - omitting it hides the control,
   * which is the brief's 「操作按 perms 隐藏」 gating shape.
   */
  onCreateRoot?: () => void
  createRootLabel?: string
  /** Row action glyphs; omitted, the tree is a pure selection context (the 科室 page). */
  actions?: OrgTreeActionHandlers
  /** Re-fetch the tree (header refresh glyph). */
  onRefresh: () => void
}

export function OrgTree({
  nodes,
  loading,
  title = '机构',
  hint,
  selectedOrgId,
  onSelect,
  onCreateRoot,
  createRootLabel = '新建根机构',
  actions,
  onRefresh,
}: OrgTreeProps) {
  const { token } = theme.useToken()
  const byId = useMemo(() => indexNodes(nodes), [nodes])
  const treeData = useMemo(() => toTreeData(nodes), [nodes])

  const [hoverId, setHoverId] = useState<number | null>(null)
  const [confirmingId, setConfirmingId] = useState<number | null>(null)

  // Controlled expansion: user collapses are respected, while a node that only
  // gains children after a create is auto-expanded so the new child is visible
  // (the prototype shows the root open).
  const [expandedKeys, setExpandedKeys] = useState<number[]>(() => collectParentIds(nodes))
  const knownParents = useRef<Set<number>>(new Set(collectParentIds(nodes)))
  useEffect(() => {
    const fresh = collectParentIds(nodes).filter((id) => !knownParents.current.has(id))
    if (fresh.length === 0) return
    for (const id of fresh) knownParents.current.add(id)
    setExpandedKeys((prev) => [...prev, ...fresh])
  }, [nodes])

  // The OPS pattern always has a context node: pick the first root as soon as
  // the tree arrives (pages pass null until then).
  useEffect(() => {
    if (selectedOrgId === null && nodes.length > 0) onSelect(nodes[0])
  }, [nodes, selectedOrgId, onSelect])

  const handleSelect = useCallback(
    (keys: React.Key[]) => {
      const key = keys[0]
      if (key === undefined) {
        onSelect(null)
        return
      }
      onSelect(byId.get(Number(key)) ?? null)
    },
    [byId, onSelect],
  )

  const titleRender = (data: DataNode) => {
    const node = byId.get(Number(data.key))
    if (!node) return null
    const active = hoverId === node.id || selectedOrgId === node.id || confirmingId === node.id
    return (
      <span
        data-org-node={node.id}
        style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '0 2px 0 0', minHeight: 32 }}
        onMouseEnter={() => setHoverId(node.id)}
        onMouseLeave={() => setHoverId(null)}
      >
        <BankOutlined style={{ fontSize: NODE_ICON_SIZE, color: token.colorTextSecondary }} />
        <span>{node.name}</span>
        {actions && active ? (
          <Space size={2} style={{ marginLeft: 'auto' }}>
            <Tooltip title="新建下级机构">
              <Button
                type="text"
                size="small"
                aria-label={`新建下级机构 ${node.name}`}
                icon={<PlusOutlined />}
                onClick={() => actions?.onCreateChild(node)}
              />
            </Tooltip>
            <Tooltip title="编辑机构">
              <Button
                type="text"
                size="small"
                aria-label={`编辑机构 ${node.name}`}
                icon={<EditOutlined />}
                onClick={() => actions?.onEdit(node)}
              />
            </Tooltip>
            <Popconfirm
              title="删除机构"
              description={`确定删除「${node.name}」吗?`}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              open={confirmingId === node.id}
              onOpenChange={(open) => setConfirmingId(open ? node.id : null)}
              onConfirm={() => {
                setConfirmingId(null)
                actions?.onDelete(node)
              }}
              onCancel={() => setConfirmingId(null)}
            >
              <Tooltip title="删除机构">
                <Button
                  type="text"
                  size="small"
                  danger
                  aria-label={`删除机构 ${node.name}`}
                  icon={<DeleteOutlined />}
                />
              </Tooltip>
            </Popconfirm>
          </Space>
        ) : null}
      </span>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: `${token.paddingSM}px ${token.paddingMD}px 4px`,
        }}
      >
        <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM, letterSpacing: 1 }}>
          {title}
        </Typography.Text>
        <Tooltip title="刷新机构树">
          <Button
            type="text"
            size="small"
            aria-label="刷新机构树"
            icon={<ReloadOutlined />}
            onClick={onRefresh}
          />
        </Tooltip>
      </div>

      <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '6px 10px 12px' }}>
        <Spin spinning={!!loading}>
          <Tree
            blockNode
            showLine={{ showLeafIcon: false }}
            treeData={treeData}
            titleRender={titleRender}
            expandedKeys={expandedKeys}
            onExpand={(_keys, info) => {
              const node = byId.get(Number(info.node.key))
              if (!node) return
              setExpandedKeys((prev) =>
                info.expanded ? [...prev, node.id] : prev.filter((id) => id !== node.id),
              )
            }}
            selectedKeys={selectedOrgId === null ? [] : [selectedOrgId]}
            onSelect={handleSelect}
          />
        </Spin>
      </div>

      {hint ? (
        <p
          style={{
            margin: 0,
            padding: `0 ${token.paddingMD}px ${token.paddingSM}px`,
            fontSize: token.fontSizeSM,
            color: token.colorTextTertiary,
            lineHeight: 1.6,
          }}
        >
          {hint}
        </p>
      ) : null}

      {onCreateRoot ? (
        <Button
          type="dashed"
          icon={<PlusOutlined />}
          style={{ margin: `0 ${token.paddingMD}px ${token.paddingMD}px` }}
          onClick={onCreateRoot}
        >
          {createRootLabel}
        </Button>
      ) : null}
    </div>
  )
}

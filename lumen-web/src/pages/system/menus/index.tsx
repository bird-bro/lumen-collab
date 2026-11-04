import { Spin, Tag, Tree, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useEffect, useState } from 'react'
import { menuTree } from '@/api/menus'
import { ApiError } from '@/api/client'
import type { MenuNode } from '@/api/types'
import { ConsolePanel } from '../console-shell'

/**
 * 菜单管理 (batch 6b, task 6.5; spec `specs/web-system-console`
 * § Menu Tree Visibility): a READ-ONLY tree of the directory/menu/button
 * hierarchy where every node's `perms` string is visible. There is no
 * mutation surface - the menu model is owned by backend seeds/migrations in
 * v1 (no menu CRUD endpoints exist in the api contract, so none is rendered).
 */

const TYPE_LABELS: Record<string, string> = {
  M: '目录',
  C: '菜单',
  F: '按钮',
}

function toData(nodes: MenuNode[]): DataNode[] {
  return nodes.map((node) => ({
    key: node.mid,
    title: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
        <span>{node.title}</span>
        <Tag style={{ marginInlineEnd: 0 }}>{TYPE_LABELS[node.type] ?? node.type}</Tag>
        {node.perms ? (
          <Tag color="blue" style={{ marginInlineEnd: 0, fontFamily: 'monospace' }}>
            {node.perms}
          </Tag>
        ) : null}
      </span>
    ),
    children: node.children ? toData(node.children) : undefined,
  }))
}

export default function MenusPage() {
  const [tree, setTree] = useState<MenuNode[] | null>(null)
  const [messageApi, messageHolder] = message.useMessage()

  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const data = await menuTree()
        if (alive) setTree(data)
      } catch (error) {
        if (alive) messageApi.error(error instanceof ApiError ? error.msg : '菜单树加载失败')
      }
    })()
    return () => {
      alive = false
    }
  }, [messageApi])

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsolePanel>
        <div style={{ padding: 16, overflow: 'auto' }}>
          {tree === null ? (
            <div style={{ textAlign: 'center', padding: 48 }}>
              <Spin />
            </div>
          ) : (
            <Tree treeData={toData(tree)} defaultExpandAll blockNode selectable={false} />
          )}
        </div>
      </ConsolePanel>
    </div>
  )
}

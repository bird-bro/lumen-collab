/**
 * 工作站切换 chip (workstation-context-web task 3.1; W-node model): zone 2, RIGHT of
 * the product title (user instruction 2026-11-06). The dropdown lists the granted tree's
 * type-W nodes (title/icon from the node, check on the active); selecting stores the
 * W node's MID (auth store) and reroutes to the station's first leaf route.
 * Session-scoped: re-login re-derives the active station (the first granted W node).
 * Absent when the granted tree carries no W node (console-only users).
 */
import { Dropdown, theme } from 'antd'
import type { MenuProps } from 'antd'
import { CheckOutlined, CaretDownOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/auth'
import { stationNodes } from '@/config/workstations'
import { firstLeafRouteOf } from '@/routes/menu-paths'
import { MenuIcon } from './BasicLayout'

export function WorkstationChip() {
  const navigate = useNavigate()
  const { token } = theme.useToken()
  const menus = useAuthStore((state) => state.menus)
  const active = useAuthStore((state) => state.activeWorkstation)
  const setActive = useAuthStore((state) => state.setActiveWorkstation)

  const stations = stationNodes(menus)
  if (stations.length === 0) return null

  const items: MenuProps['items'] = stations.map((node) => ({
    key: String(node.mid),
    label: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10, minWidth: 200 }}>
        <span
          style={{
            width: 30, height: 30, borderRadius: 8, display: 'inline-flex',
            alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            background: node.mid === active ? token.colorPrimary : token.colorFillSecondary,
            color: node.mid === active ? token.colorTextLightSolid : token.colorPrimary,
          }}
        >
          <MenuIcon name={node.icon} size={16} />
        </span>
        <span style={{ fontWeight: 600, fontSize: 13 }}>{node.title}</span>
        {node.mid === active ? (
          <CheckOutlined style={{ marginLeft: 'auto', color: token.colorSuccess }} />
        ) : null}
      </span>
    ),
  }))

  const activeNode = stations.find((m) => m.mid === active) ?? stations[0]

  return (
    <Dropdown
      trigger={['click']}
      menu={{
        items,
        selectable: true,
        selectedKeys: active != null ? [String(active)] : [],
        onClick: ({ key }) => {
          const mid = Number(key)
          if (mid === active) return
          const node = stations.find((m) => m.mid === mid)
          setActive(mid)
          // a station without any leaf page (e.g. a freshly seeded W node) switches
          // the context WITHOUT navigating - navigating to '/' would blank the page
          if (node) {
            const landing = firstLeafRouteOf(node)
            if (landing) navigate(landing)
          }
        },
      }}
    >
      <button
        type="button"
        aria-label="工作站切换"
        style={{
          display: 'inline-flex', alignItems: 'center', gap: 8, padding: '5px 12px',
          border: `1px solid ${token.colorBorderSecondary}`, borderRadius: 999,
          background: 'transparent', cursor: 'pointer',
        }}
      >
        <span style={{ fontSize: 14 }}>
          <MenuIcon name={activeNode.icon} size={16} />
        </span>
        <span style={{ fontSize: 13, fontWeight: 600 }}>{activeNode.title}</span>
        <CaretDownOutlined style={{ fontSize: 10, color: token.colorTextSecondary }} />
      </button>
    </Dropdown>
  )
}

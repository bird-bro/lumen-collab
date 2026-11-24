/**
 * 工作站切换 chip (workstation-context-web task 3.1; W-node model): zone 2, RIGHT of
 * the product title (user instruction 2026-11-06). The dropdown lists the granted tree's
 * type-W nodes (title/icon from the node, check on the active); selecting stores the
 * W node's MID (auth store) and reroutes to the station's first leaf route.
 * Session-scoped: re-login re-derives the active station (the first granted W node).
 * Absent when the granted tree carries no W node (console-only users).
 * URL-ownership (user report 2026-11-24): the shell passes the URL-derived
 * effective station (`active` override) so chip display and the page being served
 * can never diverge; switching stores the MID and navigates to the station's
 * landing - its first leaf page, or for a childless station the first permitted
 * resident page (station HOME placeholder as the last resort). A context-only
 * switch would resurface invisibly on console pages.
 */
import { Dropdown, theme } from 'antd'
import type { MenuProps } from 'antd'
import { CheckOutlined, CaretDownOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/auth'
import { stationNodes } from '@/config/workstations'
import { firstLeafRouteOf, firstResidentRoute, stationHomeRoute } from '@/routes/menu-paths'
import { MenuIcon } from './BasicLayout'

export function WorkstationChip({ active: activeOverride }: { active?: number | null } = {}) {
  const navigate = useNavigate()
  const { token } = theme.useToken()
  const menus = useAuthStore((state) => state.menus)
  const storeActive = useAuthStore((state) => state.activeWorkstation)
  const setActive = useAuthStore((state) => state.setActiveWorkstation)
  // URL-ownership (user report 2026-11-24): the shell passes the URL-derived
  // effective station so the chip can never disagree with the page being served;
  // a `null` override means "console view, no station" and wins over the store.
  const active = activeOverride !== undefined ? activeOverride : storeActive

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
          // The switch must always be VISIBLE (user report 2026-11-24): a station
          // with a leaf page navigates to it; a CHILDLESS station lands on the
          // first permitted RESIDENT page (系统管理/设置 stay usable - user ruling
          // 2026-11-24), falling back to the station HOME placeholder only when
          // the tree carries no resident page. It must never leave the previous
          // station's page on screen - a context-only switch would resurface
          // later as an "automatic" chip change on console pages.
          if (node) {
            const landing =
              firstLeafRouteOf(node) ?? firstResidentRoute(menus) ?? stationHomeRoute(node)
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

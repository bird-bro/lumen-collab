/**
 * Workstation helpers (workstation-context-web task 1.1; W-node model, user ruling
 * 2026-11-06): stations ARE menu nodes — `menu_info.type = 'W'` at L1 level, granted
 * through the role_menu flow (roles own menu permissions; users bind roles). There is
 * NO closed frontend enum and NO per-user grant store: the station list derives from
 * the granted tree, the APPEND/PROMOTION filter shapes the sidebar.
 *
 * Display rule (user spec 2026-11-06, verbatim semantics):
 *   if (W): the W node appears ONLY in the chip dropdown (default = the first W); the
 *           ACTIVE W node's CHILDREN are promoted one level into the menu bar beside
 *           the resident M-type L1s (登记 beside 系统管理).
 *   if (M): rendered exactly as today (current hierarchy, zero change).
 * Clicking a promoted entry navigates normally WITHOUT changing the chip's active
 * station.
 */
import type { MenuNode } from '@/api/types';

/** The granted workstation nodes: the tree's L1 entries with `type = 'W'`. */
export function stationNodes(menus: MenuNode[]): MenuNode[] {
  return menus.filter((m) => m.type === 'W');
}

/**
 * The sidebar menu list for the active workstation - the PROMOTION semantics:
 * M-type L1s are resident; the ACTIVE W node's CHILDREN promote one level into the
 * bar (the W node itself never enters it); null = console view (M-type L1s alone,
 * byte-identical to pre-workstation).
 */
export function sidebarMenus(menus: MenuNode[], activeWsMid: number | null): MenuNode[] {
  const resident = menus.filter((m) => m.type !== 'W');
  if (activeWsMid == null) return resident;
  const station = menus.find((m) => m.mid === activeWsMid && m.type === 'W');
  return station?.children ? [...resident, ...station.children] : resident;
}

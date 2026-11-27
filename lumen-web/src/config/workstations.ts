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
import { activeTopLevelNode, stationHomeRoute } from '@/routes/menu-paths';

/** The granted workstation nodes: the tree's L1 entries with `type = 'W'`. */
export function stationNodes(menus: MenuNode[]): MenuNode[] {
  return menus.filter((m) => m.type === 'W');
}

/**
 * The sidebar menu list for the active workstation - the PROMOTION semantics:
 * the ACTIVE W node's CHILDREN promote one level into the bar and LEAD it
 * (user ruling 2026-11-20: the station's own page — sort 1 — ranks ABOVE the
 * resident M-type L1s); the W node itself never enters the bar (it lives only
 * in the chip dropdown); null = console view (M-type L1s alone, byte-identical
 * to pre-workstation).
 */
export function sidebarMenus(menus: MenuNode[], activeWsMid: number | null): MenuNode[] {
  const resident = menus.filter((m) => m.type !== 'W');
  if (activeWsMid == null) return resident;
  const station = menus.find((m) => m.mid === activeWsMid && m.type === 'W');
  // promoted children LEAD (ruling 2026-11-20): the station's pages first, the
  // resident M L1s (系统管理/设置) after
  return station?.children ? [...station.children, ...resident] : resident;
}

/**
 * The type-W station node that OWNS the given pathname (its subtree resolves to
 * it), or null when the route belongs to a resident console menu (or nothing).
 * URL-ownership rule (user report 2026-11-24): on a station page the displayed
 * station context ALWAYS follows the route - the chip and the promoted bar may
 * never disagree with the page being served, however the store value got there
 * (a refresh resume or a login landing at a non-first station alike). The store
 * value only governs console views, where no station owns the route.
 */
export function activeStationForPath(menus: MenuNode[], pathname: string): MenuNode | null {
  const owner = activeTopLevelNode(menus, pathname);
  if (owner?.type === 'W') return owner;
  // The station HOME placeholder (design D8 - e.g. childless 放射 lands on
  // /radiology): the V22 seed's W paths are RELATIVE, so activeTopLevelNode's
  // segment-safe prefix rule (absolute paths only) can never own them - match
  // the mapped placeholder route explicitly, else the chip desyncs from the
  // placeholder page the moment the store holds another station (user report
  // 2026-11-24, the 放射-placeholder shape).
  return menus.find((m) => m.type === 'W' && stationHomeRoute(m) === pathname) ?? null;
}

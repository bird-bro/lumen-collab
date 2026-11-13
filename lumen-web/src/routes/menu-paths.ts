/**
 * Menu-tree -> route resolution (batch 5b of web-foundation-auth-console,
 * tasks 5.2/5.3; design D6 "the `/` redirect and the login landing resolve to
 * the first menu route the user's tree grants"). Pure functions over the
 * `MenuNode` tree stored in the auth session (`src/stores/auth.ts`); the
 * router (`/` index redirect), the login page (post-login landing) and the
 * shell (`BasicLayout` rail activation + L1 click) all consume this module.
 *
 * Route semantics (design D6): console routes are the plural
 * `/system/orgs|deps|users|roles|menus|dicts|logs` plus the sign-log sibling
 * `/system/logs/sign` (sign-log-query b4), while the menu fixture
 * keeps the backend seed's singular relative `path` column (`org`, `user`, ...)
 * with the `component` column (`system/org/index`, ...). The component column
 * is therefore the stable key that maps a console leaf onto its registered
 * route; department leaves carry absolute paths (`/radiology`) and resolve
 * directly. Directories (`type 'M'`) and buttons (`type 'F'`) never navigate.
 */
import type { MenuNode } from '@/api/types';

/**
 * Console `component` column -> the registered route in `src/router.tsx`
 * (design D6). Keys mirror the menu fixture's component column verbatim.
 */
const CONSOLE_ROUTE_BY_COMPONENT: Record<string, string> = {
  'system/device/index': '/system/devices',
  'system/room/index': '/system/rooms',
  'system/org/index': '/system/orgs',
  'system/dep/index': '/system/deps',
  'system/user/index': '/system/users',
  'system/role/index': '/system/roles',
  'system/menu/index': '/system/menus',
  'system/dict/index': '/system/dicts',
  'system/log/index': '/system/logs',
  // 检查设置 (exam-setting-web task 1.2 - component keys verbatim from the V28 seed).
  'setting/exam-item/index': '/setting/exam-items',
  'setting/exam-body/index': '/setting/exam-bodies',
  'setting/exam-method/index': '/setting/exam-methods',
  'setting/rep-template/index': '/setting/rep-templates',
};

/**
 * The route a single node navigates to, or null when the node is not a
 * navigable leaf (directories and buttons are shell structure, never links;
 * console pages resolve through the component map above, department pages
 * through their absolute path).
 */
export function resolveMenuRoute(node: MenuNode): string | null {
  if (node.type !== 'C' || node.path == null) return null;
  if (node.path.startsWith('/')) return node.path;
  return CONSOLE_ROUTE_BY_COMPONENT[node.component ?? ''] ?? null;
}

/**
 * Depth-first (sort order is carried by the tree itself) first navigable
 * route of a menu forest - the login landing and the `/` index redirect.
 */
export function firstGrantedRoute(menus: MenuNode[]): string | null {
  for (const node of menus) {
    const direct = resolveMenuRoute(node);
    if (direct) return direct;
    const childRoute = firstGrantedRoute(node.children ?? []);
    if (childRoute) return childRoute;
  }
  return null;
}

/**
 * The route an L1 shell entry leads to: the node itself when it is a leaf
 * route (departments), otherwise its first leaf (the 系统管理 directory).
 */
export function firstLeafRouteOf(node: MenuNode): string | null {
  return resolveMenuRoute(node) ?? firstGrantedRoute(node.children ?? []);
}

/**
 * The top-level node whose subtree contains the given route - the shell's
 * active L1 (rail selection + which children the secondary sidebar renders).
 */
export function activeTopLevelNode(menus: MenuNode[], pathname: string): MenuNode | null {
  // segment-safe prefix: a directory L1 (type M, e.g. /radiology) also owns its
  // prefix subtree ("/radiology" owns "/radiology/register" but NOT "/users")
  const pathOwns = (node: MenuNode): boolean => {
    const p = node.path;
    if (p != null && p.startsWith('/')) {
      return pathname === p || pathname.startsWith(p + '/');
    }
    return false;
  };
  const subtreeHas = (node: MenuNode): boolean =>
    resolveMenuRoute(node) === pathname ||
    pathOwns(node) ||
    (node.children ?? []).some((child) => subtreeHas(child));
  return menus.find((node) => subtreeHas(node)) ?? null;
}

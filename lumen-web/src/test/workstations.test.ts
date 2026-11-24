import { beforeEach, describe, expect, it, vi } from 'vitest'
import { activeStationForPath, sidebarMenus } from '@/config/workstations'
import { useAuthStore } from '@/stores/auth'
import type { MenuNode } from '@/api/types'

/**
 * 工作站上下文 (workstation-context-web tasks 1.1/1.2; W-node model, user ruling
 * 2026-11-06): stations derive from the granted tree (`type === 'W'` L1 nodes); the
 * sidebar APPEND/PROMOTION filter — the active W node's CHILDREN promote one level
 * into the menu bar beside the resident M-type L1s (系统管理), while the W node
 * itself NEVER enters the menu bar (it lives only in the chip dropdown). The auth
 * store's activeWorkstation is the W node's MID (number | null): defaults to the
 * first granted W node at session landing, session-scoped switching, re-login reset.
 */

function menu(partial: Partial<MenuNode> & { mid: number; title: string; upId: number }): MenuNode {
  return {
    icon: null, path: null, query: null, component: null, type: 'M', sort: 1,
    visible: true, redirect: null, app: null, breadcrumb: null, action: null,
    isCache: true, isFrame: true, perms: null, remark: null, children: null,
    ...partial,
  }
}

const TREE: MenuNode[] = [
  menu({
    mid: 1, title: '系统管理', upId: 0, type: 'M', path: '/system',
    children: [menu({ mid: 2, title: '用户管理', upId: 1, type: 'C', path: '/system/users' })],
  }),
  menu({
    mid: 30, title: '放射工作站', upId: 0, type: 'W', path: '/radiology',
    children: [menu({ mid: 31, title: '登记预约', upId: 30, type: 'C', path: '/radiology/register' })],
  }),
  menu({ mid: 40, title: '超声工作站', upId: 0, type: 'W', path: '/us' }),
]

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().clear()
})

describe('workstation derivation + promotion filter (task 1.1)', () => {
  it('stations derive from the tree: only type-W L1 nodes', () => {
    const stations = useAuthStore.getState().menus // not used here; the helper is tested via sidebarMenus
    void stations
    // the promoted bar for the radiology station contains its children as L1 entries
    const bar = sidebarMenus(TREE, 30)
    const ids = bar.map((m) => m.mid)
    // ORDER (user ruling 2026-11-20): the promoted station pages LEAD the bar -
    // the station's own page (sort 1, 登记) ranks ABOVE the resident M L1s
    expect(ids).toEqual([31, 1])
    expect(ids).not.toContain(30) // the W node itself NEVER enters the bar
    expect(ids).not.toContain(40) // the other station stays out
  })

  it('控制台 (no active station) renders only the resident M-type L1s', () => {
    expect(sidebarMenus(TREE, null).map((m) => m.mid)).toEqual([1])
  })

  it('a W node with no children still switches (its bar contribution is empty)', () => {
    const bar = sidebarMenus(TREE, 40)
    expect(bar.map((m) => m.mid)).toEqual([1]) // 系统管理 stays, 超声 contributes nothing
  })
})

describe('activeStationForPath - the URL owns the station context (user report 2026-11-24)', () => {
  // The REAL current shape: 放射 childless (no pages yet), 超声 carries the
  // promoted 登记 page - the refresh/landing desync scenario tree.
  const US_TREE: MenuNode[] = [
    menu({
      mid: 1, title: '系统管理', upId: 0, type: 'M', path: '/system',
      children: [menu({ mid: 2, title: '用户管理', upId: 1, type: 'C', path: '/system/users' })],
    }),
    menu({ mid: 30, title: '放射工作站', upId: 0, type: 'W', path: '/radiology' }),
    menu({
      mid: 40, title: '超声工作站', upId: 0, type: 'W', path: '/us',
      children: [menu({ mid: 41, title: '登记', upId: 40, type: 'C', path: '/us/registration' })],
    }),
  ]

  it('the station whose subtree resolves the route wins: /us/registration -> 超声 W node', () => {
    expect(activeStationForPath(US_TREE, '/us/registration')?.mid).toBe(40)
  })

  it('console routes own no station (store context stands)', () => {
    expect(activeStationForPath(US_TREE, '/system/users')).toBeNull()
  })

  it('segment-safe: /users is not swallowed by the /us station prefix', () => {
    expect(activeStationForPath(US_TREE, '/users')).toBeNull()
  })

  it('a childless station still owns its own prefix route (placeholder page context)', () => {
    expect(activeStationForPath(US_TREE, '/radiology')?.mid).toBe(30)
  })
})

describe('auth store activeWorkstation lifecycle (task 1.2; resume contract 2026-11-24)', () => {
  it('defaults to the FIRST granted W node at session landing', () => {
    useAuthStore.getState().setSession({ token: 't', menus: TREE, perms: [] })
    expect(useAuthStore.getState().activeWorkstation).toBe(30) // first W node's mid
  })

  it('lands null (console) when no W node is granted', () => {
    useAuthStore.getState().setSession({ token: 't', menus: [TREE[0]], perms: [] })
    expect(useAuthStore.getState().activeWorkstation).toBeNull()
  })

  it('a /me resume keeps a still-granted station (refresh ≠ landing, user report 2026-11-24)', () => {
    useAuthStore.getState().setSession({ token: 't', menus: TREE, perms: [] })
    useAuthStore.getState().setActiveWorkstation(40)
    // the boot-refresh path (SessionBoot): a partial setSession with no rederive
    // opt-in must NOT clobber the station the user was actually working in
    useAuthStore.getState().setSession({ token: 't', menus: TREE, perms: [] })
    expect(useAuthStore.getState().activeWorkstation).toBe(40)
  })

  it('re-login re-derives the first W via the { rederiveStation: true } opt-in (landing rule)', () => {
    useAuthStore.getState().setSession({ token: 't', menus: TREE, perms: [] })
    useAuthStore.getState().setActiveWorkstation(40)
    useAuthStore.getState().setSession({ token: 't2', menus: TREE, perms: [] }, { rederiveStation: true })
    expect(useAuthStore.getState().activeWorkstation).toBe(30) // first granted W
  })

  it('a no-longer-granted station falls back on resume (first W, null when none)', () => {
    useAuthStore.getState().setSession({ token: 't', menus: TREE, perms: [] })
    useAuthStore.getState().setActiveWorkstation(40)
    useAuthStore.getState().setSession({ token: 't', menus: [TREE[0]], perms: [] }) // 超声 revoked
    expect(useAuthStore.getState().activeWorkstation).toBeNull() // tree carries no W anymore
  })

  it('a session with no W nodes keeps activeWorkstation null across switches', () => {
    useAuthStore.getState().setSession({ token: 't', menus: [TREE[0]], perms: [] })
    useAuthStore.getState().setActiveWorkstation(30)
    expect(useAuthStore.getState().activeWorkstation).toBeNull() // nothing to switch to
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { sidebarMenus } from '@/config/workstations'
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
    expect(ids).toContain(1) // 系统管理 resident
    expect(ids).toContain(31) // 登记预约 promoted (was L2 under the W node)
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

describe('auth store activeWorkstation lifecycle (task 1.2)', () => {
  it('defaults to the FIRST granted W node at session landing', () => {
    useAuthStore.getState().setSession({ token: 't', menus: TREE, perms: [] })
    expect(useAuthStore.getState().activeWorkstation).toBe(30) // first W node's mid
  })

  it('lands null (console) when no W node is granted', () => {
    useAuthStore.getState().setSession({ token: 't', menus: [TREE[0]], perms: [] })
    expect(useAuthStore.getState().activeWorkstation).toBeNull()
  })

  it('switches via setActiveWorkstation and re-login resets to the first W', () => {
    useAuthStore.getState().setSession({ token: 't', menus: TREE, perms: [] })
    useAuthStore.getState().setActiveWorkstation(40)
    expect(useAuthStore.getState().activeWorkstation).toBe(40)
    useAuthStore.getState().setSession({ token: 't2', menus: TREE, perms: [] })
    expect(useAuthStore.getState().activeWorkstation).toBe(30) // re-login → first W
  })

  it('a session with no W nodes keeps activeWorkstation null across switches', () => {
    useAuthStore.getState().setSession({ token: 't', menus: [TREE[0]], perms: [] })
    useAuthStore.getState().setActiveWorkstation(30)
    expect(useAuthStore.getState().activeWorkstation).toBeNull() // nothing to switch to
  })
})

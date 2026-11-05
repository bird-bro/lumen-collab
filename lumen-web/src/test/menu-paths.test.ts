import { describe, expect, it } from 'vitest'
import { activeTopLevelNode, firstGrantedRoute, firstLeafRouteOf, resolveMenuRoute } from '@/routes/menu-paths'
import type { MenuNode } from '@/api/types'

/**
 * Menu-tree -> route resolution (batch 5b of web-foundation-auth-console,
 * tasks 5.2/5.3): the login page and the `/` index route land on the FIRST
 * route the user's menu tree grants, and the shell rail navigates an L1 to
 * its first leaf. The fixture below mirrors the backend seed menu tree
 * (V2__seed.sql) after the batch-5b restructure (three group directories 13/14/15 under
 * 系统管理, seven console pages hung off the groups) in the copied MenuTree
 * JSON shape (`mid` id column, leaf `children` null).
 */

/** Minimal MenuNode builder - only the fields the resolver reads. */
function node(partial: {
  mid: number
  title: string
  upId: number
  type: 'M' | 'C' | 'F'
  path?: string | null
  component?: string | null
  children?: MenuNode[] | null
}): MenuNode {
  return {
    mid: partial.mid,
    title: partial.title,
    upId: partial.upId,
    type: partial.type,
    path: partial.path ?? null,
    component: partial.component ?? null,
    children: partial.children ?? null,
    query: null,
    sort: 1,
    visible: true,
    redirect: null,
    app: null,
    breadcrumb: null,
    action: null,
    isCache: null,
    isFrame: null,
    perms: null,
    remark: null,
    icon: null,
  }
}

/** The seeded admin tree (shape of the batch-5b mock fixture, depth-first sorted). */
function adminTree(): MenuNode[] {
  return [
    node({
      mid: 1,
      title: '系统管理',
      upId: 0,
      type: 'M',
      path: '/system',
      children: [
        node({
          mid: 13,
          title: '组织架构',
          upId: 1,
          type: 'M',
          children: [
            node({ mid: 7, title: '机构管理', upId: 13, type: 'C', path: 'org', component: 'system/org/index' }),
            node({ mid: 8, title: '科室管理', upId: 13, type: 'C', path: 'dep', component: 'system/dep/index' }),
          ],
        }),
        node({
          mid: 14,
          title: '用户权限',
          upId: 1,
          type: 'M',
          children: [
            node({ mid: 2, title: '用户管理', upId: 14, type: 'C', path: 'user', component: 'system/user/index' }),
            node({ mid: 9, title: '角色管理', upId: 14, type: 'C', path: 'role', component: 'system/role/index' }),
            node({ mid: 10, title: '菜单管理', upId: 14, type: 'C', path: 'menu', component: 'system/menu/index' }),
          ],
        }),
        node({
          mid: 15,
          title: '系统配置',
          upId: 1,
          type: 'M',
          children: [
            node({ mid: 11, title: '字典管理', upId: 15, type: 'C', path: 'dict', component: 'system/dict/index' }),
            node({ mid: 12, title: '日志查询', upId: 15, type: 'C', path: 'log', component: 'system/log/index' }),
          ],
        }),
      ],
    }),
    node({ mid: 3, title: '放射科', upId: 0, type: 'C', path: '/radiology', children: [node({ mid: 6, title: '报告审核', upId: 3, type: 'F' })] }),
    node({ mid: 4, title: '超声科', upId: 0, type: 'C', path: '/ultrasound' }),
    node({ mid: 5, title: '内镜科', upId: 0, type: 'C', path: '/endoscopy' }),
  ]
}

describe('resolveMenuRoute (web-foundation-auth-console 5.3)', () => {
  it('maps a console page to its registered /system route via the component column', () => {
    expect(resolveMenuRoute(node({ mid: 7, title: '机构管理', upId: 13, type: 'C', path: 'org', component: 'system/org/index' }))).toBe('/system/orgs')
    expect(resolveMenuRoute(node({ mid: 10, title: '菜单管理', upId: 14, type: 'C', path: 'menu', component: 'system/menu/index' }))).toBe('/system/menus')
    expect(resolveMenuRoute(node({ mid: 12, title: '日志查询', upId: 15, type: 'C', path: 'log', component: 'system/log/index' }))).toBe('/system/logs')
  })

  it('keeps an absolute department path as-is', () => {
    expect(resolveMenuRoute(node({ mid: 3, title: '放射科', upId: 0, type: 'C', path: '/radiology' }))).toBe('/radiology')
  })

  it('refuses directories and buttons (nothing navigable)', () => {
    expect(resolveMenuRoute(node({ mid: 1, title: '系统管理', upId: 0, type: 'M', path: '/system' }))).toBeNull()
    expect(resolveMenuRoute(node({ mid: 13, title: '组织架构', upId: 1, type: 'M' }))).toBeNull()
    expect(resolveMenuRoute(node({ mid: 6, title: '报告审核', upId: 3, type: 'F' }))).toBeNull()
  })
})

describe('firstGrantedRoute (web-foundation-auth-console 5.2/5.3)', () => {
  it('lands on the first console page of the first group (admin fixture: /system/orgs)', () => {
    expect(firstGrantedRoute(adminTree())).toBe('/system/orgs')
  })

  it('falls through to the first department when system pages are not granted', () => {
    const tree = adminTree().slice(1)
    expect(firstGrantedRoute(tree)).toBe('/radiology')
  })

  it('returns null for an empty grant (the caller shows the no-menu state)', () => {
    expect(firstGrantedRoute([])).toBeNull()
  })
})

describe('firstLeafRouteOf (web-foundation-auth-console 5.4 rail click)', () => {
  it('navigates an L1 directory to its first leaf (/system/orgs for 系统管理)', () => {
    expect(firstLeafRouteOf(adminTree()[0])).toBe('/system/orgs')
  })

  it('navigates a department L1 to itself (it is a leaf route)', () => {
    expect(firstLeafRouteOf(adminTree()[1])).toBe('/radiology')
  })
})

describe('activeTopLevelNode (web-foundation-auth-console 5.4 rail activation)', () => {
  it('finds the L1 whose subtree owns the current route', () => {
    const tree = adminTree()
    expect(activeTopLevelNode(tree, '/system/roles')?.mid).toBe(1)
    expect(activeTopLevelNode(tree, '/system/logs')?.mid).toBe(1)
    expect(activeTopLevelNode(tree, '/radiology')?.mid).toBe(3)
    expect(activeTopLevelNode(tree, '/endoscopy')?.mid).toBe(5)
  })

  it('matches nothing for an ungranted route (rail renders without an active item)', () => {
    expect(activeTopLevelNode(adminTree(), '/nowhere')).toBeNull()
  })
})

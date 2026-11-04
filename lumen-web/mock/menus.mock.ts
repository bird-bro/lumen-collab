import { defineMock } from 'vite-plugin-mock-dev-server'

import { allMenus, buildMenuTree, guard, ok } from './_fixtures'

/**
 * GET /api/v1/system/menus/tree (MenuController) - the unscoped whole menu tree
 * (directories / menus / buttons with perms strings), token-guarded like the backend
 * security chain.
 */
export default defineMock([
  {
    url: '/api/v1/system/menus/tree',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      return ok(buildMenuTree(allMenus().map(m => m.id)))
    },
  },
])

import { defineMock } from 'vite-plugin-mock-dev-server'

import {
  allMenus,
  fail,
  guard,
  rowStamp,
  ok,
  pageOf,
  roleIds,
  seedRoleMenus,
  seedRoles,
  seedUserRoles,
  state,
  type MockRole,
} from './_fixtures'

/** Shared dev-session state (globalThis bucket - see _fixtures.ts for the plugin-convention note). */
const roles = () => state<Record<string, MockRole[]>>('roles', () => ({ list: seedRoles() })).list
const roleMenus = () => state<Record<string, Record<number, number[]>>>('roleMenus', () => ({ map: seedRoleMenus() })).map
const userRoles = () =>
  state<Record<string, { userId: number; roleId: number }[]>>('userRoles', () => ({ list: seedUserRoles() })).list

/**
 * Role endpoints (RoleController) plus the paged list - the list is the api-delta GAP
 * endpoint (not implemented backend-side yet); the mock serves the delta scenario with
 * the copied IPage shape and the `pageNum`/`pageSize` convention of the copied
 * PageSizeConfig. Each record carries `menuIds` (mock-only) so the console's
 * full-replace dialog can reopen with the role's current grant.
 */
export default defineMock([
  {
    url: '/api/v1/system/roles/list',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const pageNum = Number(request.query?.pageNum ?? 1)
      const pageSize = Number(request.query?.pageSize ?? 20)
      const grants = roleMenus()
      const rows = roles()
        .slice()
        .sort((a, b) => a.id - b.id)
        .map(role => ({ ...role, menuIds: grants[role.id] ?? [] }))
      return ok(pageOf(rows.slice((pageNum - 1) * pageSize, pageNum * pageSize), pageNum, pageSize))
    },
  },
  {
    url: '/api/v1/system/roles',
    method: 'POST',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const body = (request.body ?? {}) as { name?: string; type?: number; remark?: string }
      if (!body.name) return fail('A0400', 'name must not be blank')
      if (roles().some(r => r.name === body.name)) {
        return fail('A0714', `数据重复：role name already exists: ${body.name}`)
      }
      const id = Math.max(...roles().map(r => r.id), 0) + 1
      roles().push({
        id,
        name: body.name,
        // A null type falls to the V1 column default 11.
        type: body.type ?? 11,
        remark: body.remark ?? null,
        createBy: null,
        createTime: rowStamp(),
        updateBy: null,
        updateTime: rowStamp(),
      })
      roleIds().push(id)
      return ok(id)
    },
  },
  {
    url: '/api/v1/system/roles/:id',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      const role = roles().find(r => r.id === id)
      if (!role) return fail('B0031', `role id ${id} does not exist`)
      const body = (request.body ?? {}) as { name?: string; type?: number; remark?: string }
      if (body.name != null) {
        if (roles().some(r => r.name === body.name && r.id !== id)) {
          return fail('A0714', `数据重复：role name already exists: ${body.name}`)
        }
        role.name = body.name
      }
      if (body.type != null) role.type = body.type
      if (body.remark != null) role.remark = body.remark
      role.updateTime = rowStamp()
      return ok(true)
    },
  },
  {
    url: '/api/v1/system/roles/:id',
    method: 'DELETE',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // Cascade (recorded decision): dead role, dead user_role bindings, dead role_menu
      // grants - mirror RoleServiceImpl.delete.
      const id = Number(request.params?.id)
      const index = roles().findIndex(r => r.id === id)
      if (index >= 0) roles().splice(index, 1)
      const idIndex = roleIds().indexOf(id)
      if (idIndex >= 0) roleIds().splice(idIndex, 1)
      const remainingUserRoles = userRoles().filter(b => b.roleId !== id)
      userRoles().splice(0, userRoles().length, ...remainingUserRoles)
      delete roleMenus()[id]
      return ok(true)
    },
  },
  {
    url: '/api/v1/system/roles/:id/menus',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      if (!roles().some(r => r.id === id)) {
        return fail('A0400', `role does not exist: role id ${id}`)
      }
      const body = (request.body ?? {}) as { menuIds?: number[] }
      if (body.menuIds == null) {
        return fail('A0400', 'menuIds must not be null (an empty array clears all grants)')
      }
      // Dedupe keeping order; unknown menu ids reject with A0400 (selectBatchIds count).
      const menuIds = [...new Set(body.menuIds)]
      const known = new Set(allMenus().map(m => m.id))
      const missing = menuIds.filter(mid => !known.has(mid))
      if (missing.length > 0) {
        return fail('A0400', `some menu ids do not exist: [${missing.join(', ')}]`)
      }
      // FULL REPLACE: the posted set becomes the role's complete grant - /me and the
      // roles list reflect it immediately (shared globalThis state).
      roleMenus()[id] = menuIds
      return ok(true)
    },
  },
])

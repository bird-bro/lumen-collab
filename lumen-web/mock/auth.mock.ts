import { defineMock } from 'vite-plugin-mock-dev-server'

import {
  buildMenuTree,
  fail,
  guard,
  mockJwt,
  ok,
  permsOf,
  seedRoleMenus,
  seedRoles,
  seedUserRoles,
  seedUsers,
  state,
  type MockRole,
  type MockUser,
} from './_fixtures'

/** Shared dev-session state (globalThis bucket - see _fixtures.ts for the plugin-convention note). */
const users = () => state<Record<string, MockUser[]>>('users', () => ({ list: seedUsers() })).list
const roles = () => state<Record<string, MockRole[]>>('roles', () => ({ list: seedRoles() })).list
const userRoles = () =>
  state<Record<string, { userId: number; roleId: number }[]>>('userRoles', () => ({ list: seedUserRoles() })).list
const roleMenus = () => state<Record<string, Record<number, number[]>>>('roleMenus', () => ({ map: seedRoleMenus() })).map
/** mock-only issued-token registry (login -> uid) so /me resolves the calling user. */
const tokens = () => state<Record<string, Record<string, number>>>('tokens', () => ({ map: {} })).map

function roleIdsOf(uid: number): number[] {
  return userRoles().filter(b => b.userId === uid).map(b => b.roleId)
}

function roleNamesOf(uid: number): string[] {
  const ids = roleIdsOf(uid)
  return roles().filter(r => ids.includes(r.id)).map(r => r.name)
}

/** The union of the user's roles' granted menu ids (AuthServiceImpl permissionSet). */
function grantsOf(uid: number): number[] {
  const ids = roleIdsOf(uid)
  const grants = roleMenus()
  return [...new Set(ids.flatMap(roleId => grants[roleId] ?? []))]
}

function profileOf(user: MockUser) {
  return {
    uid: user.uid,
    username: user.username,
    name: user.name,
    enable: user.enable,
    mustChangePassword: user.mustChangePassword,
    roles: roleNamesOf(user.uid),
  }
}

/**
 * Auth endpoints (AuthController): POST /login (anonymous - the batch-3 permit-all rule)
 * and GET /me (token-guarded; missing/non-Bearer Authorization -> the "203" envelope,
 * mirroring AuthenticationEntryPointImpl).
 */
export default defineMock([
  {
    url: '/api/v1/system/login',
    method: 'POST',
    body: (request) => {
      const body = (request.body ?? {}) as { username?: string; password?: string }
      // Branch order mirrors AuthServiceImpl.login: unknown -> A0201, disabled -> A0202,
      // credential mismatch -> A0210 (A0210 semantics per the brief).
      const user = users().find(u => u.username === body.username)
      if (!user) return fail('A0201', '用户账户不存在')
      if (user.enable !== 1) return fail('A0202', '用户账户被冻结')
      if (user.password !== body.password) return fail('A0210', '用户密码错误')

      const token = mockJwt()
      tokens()[token] = user.uid
      return ok({
        token,
        profile: profileOf(user),
        menus: buildMenuTree(grantsOf(user.uid)),
        perms: permsOf(grantsOf(user.uid)),
      })
    },
  },
  {
    url: '/api/v1/system/me',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // Resolve the Bearer value against the mock-only token registry; an unregistered
      // token falls back to the seeded admin session (mock-only convenience).
      const auth = request.headers?.authorization ?? ''
      const uid = tokens()[auth.slice('Bearer '.length)] ?? 1
      const user = users().find(u => u.uid === uid) ?? users()[0]
      return ok({
        profile: profileOf(user),
        menus: buildMenuTree(grantsOf(user.uid)),
        perms: permsOf(grantsOf(user.uid)),
      })
    },
  },
])

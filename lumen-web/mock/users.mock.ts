import { defineMock } from 'vite-plugin-mock-dev-server'

import {
  fail,
  guard,
  rowStamp,
  ok,
  roleIds,
  seedUserRoles,
  seedUsers,
  state,
  type MockUser,
} from './_fixtures'

/** Shared dev-session state (globalThis bucket - see _fixtures.ts for the plugin-convention note). */
const users = () => state<Record<string, MockUser[]>>('users', () => ({ list: seedUsers() })).list
const userRoles = () =>
  state<Record<string, { userId: number; roleId: number }[]>>('userRoles', () => ({ list: seedUserRoles() })).list

/**
 * User endpoints (UserInfoController). The implemented surface returns the list as a
 * PLAIN array of UserItemDto items (no page wrapper) - mirrored 1:1. POST returns the
 * new uid, PUT /password and PUT /enable return Boolean.TRUE. Duplicate live username
 * rejects A0111; an unknown target rejects B0031 (requireLiveUser).
 */
export default defineMock([
  {
    url: '/api/v1/system/users/list',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // UserItemDto JSON: the BCrypt hash never serializes (no password field exists).
      const rows = users()
        .slice()
        .sort((a, b) => a.uid - b.uid)
        .map(u => ({
          uid: u.uid,
          username: u.username,
          name: u.name,
          enable: u.enable,
          mustChangePassword: u.mustChangePassword,
          createTime: u.createTime,
        }))
      return ok(rows)
    },
  },
  {
    url: '/api/v1/system/users',
    method: 'POST',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const body = (request.body ?? {}) as {
        username?: string
        password?: string
        name?: string
        roleIds?: number[]
      }
      if (!body.username) return fail('A0400', 'username must not be blank')
      if (!body.password) return fail('A0400', 'password must not be blank')
      if (!body.name) return fail('A0400', 'name must not be blank')
      if (users().some(u => u.username === body.username)) {
        return fail('A0111', '用户名已存在')
      }
      // Dedupe keeping order (the LinkedHashSet semantics of createUser), then validate
      // against live roles: "some role ids do not exist: [ids]".
      const requestedRoleIds = [...new Set(body.roleIds ?? [])]
      const missing = requestedRoleIds.filter(id => !roleIds().includes(id))
      if (missing.length > 0) {
        return fail('A0400', `some role ids do not exist: [${missing.join(', ')}]`)
      }
      // must_change_password defaults 0 and enable defaults 1 (V1 column defaults,
      // per UserInfoServiceImpl.createUser).
      const uid = Math.max(...users().map(u => u.uid), 0) + 1
      users().push({
        uid,
        username: body.username,
        password: body.password, // mock-only: raw in session state, never serialized out
        name: body.name,
        enable: 1,
        mustChangePassword: 0,
        createTime: rowStamp(),
      })
      for (const roleId of requestedRoleIds) userRoles().push({ userId: uid, roleId })
      return ok(uid)
    },
  },
  {
    url: '/api/v1/system/users/:id/password',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      const user = users().find(u => u.uid === id)
      if (!user) return fail('B0031', `user id ${id} does not exist`)
      const body = (request.body ?? {}) as { newPassword?: string }
      if (!body.newPassword) return fail('A0400', 'newPassword must not be blank')
      user.password = body.newPassword // mock-only raw compare value
      // The reset forces a password change at the next login (resetPassword contract).
      user.mustChangePassword = 1
      user.updateTime = rowStamp()
      return ok(true)
    },
  },
  {
    url: '/api/v1/system/users/:id/enable',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      const user = users().find(u => u.uid === id)
      if (!user) return fail('B0031', `user id ${id} does not exist`)
      const body = (request.body ?? {}) as { enable?: number }
      // Bean-validated 0/1 range: an out-of-range value renders as the A0400 envelope.
      if (body.enable !== 0 && body.enable !== 1) {
        return fail('A0400', 'enable must be 0 or 1')
      }
      user.enable = body.enable
      user.updateTime = rowStamp()
      return ok(true)
    },
  },
])

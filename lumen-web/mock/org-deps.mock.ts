import { defineMock } from 'vite-plugin-mock-dev-server'

import {
  fail,
  guard,
  rowStamp,
  ok,
  orgIds,
  seedDeps,
  state,
  type MockDep,
} from './_fixtures'

/** Shared dev-session state (globalThis bucket - see _fixtures.ts for the plugin-convention note). */
const deps = () => state<Record<string, MockDep[]>>('orgDeps', () => ({ list: seedDeps() })).list

function liveOrg(id: number): boolean {
  return orgIds().includes(id)
}

/**
 * OrgDep endpoints (OrgDepController): the org-scoped list + CRUD. POST returns the new
 * id, PUT/DELETE return Boolean.TRUE. Duplicate live code rejects A0712, a missing
 * referenced org rejects A0400, an unknown update target rejects B0031.
 */
export default defineMock([
  {
    url: '/api/v1/system/org-deps/list',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // orgId optional filter; ordered by id asc.
      const orgId = request.query?.orgId != null ? Number(request.query.orgId) : null
      const rows = deps()
        .filter(d => orgId == null || d.orgId === orgId)
        .sort((a, b) => a.id - b.id)
      return ok(rows)
    },
  },
  {
    url: '/api/v1/system/org-deps',
    method: 'POST',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const body = (request.body ?? {}) as { orgId?: number; code?: string; name?: string }
      if (body.orgId == null) return fail('A0400', 'orgId must not be null')
      if (!body.code) return fail('A0400', 'code must not be blank')
      if (!body.name) return fail('A0400', 'name must not be blank')
      if (!liveOrg(body.orgId)) return fail('A0400', `org does not exist: org id ${body.orgId}`)
      if (deps().some(d => d.code === body.code)) {
        return fail('A0712', `department code already exists: ${body.code}`)
      }
      const id = Math.max(...deps().map(d => d.id), 0) + 1
      deps().push({
        id,
        orgId: body.orgId,
        code: body.code,
        name: body.name,
        createBy: null,
        createTime: rowStamp(),
        updateBy: null,
        updateTime: rowStamp(),
      })
      return ok(id)
    },
  },
  {
    url: '/api/v1/system/org-deps/:id',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      const dep = deps().find(d => d.id === id)
      if (!dep) return fail('B0031', `department id ${id} does not exist`)
      const body = (request.body ?? {}) as { orgId?: number; code?: string; name?: string }
      if (body.orgId != null) {
        if (!liveOrg(body.orgId)) return fail('A0400', `org does not exist: org id ${body.orgId}`)
        dep.orgId = body.orgId
      }
      if (body.code != null) {
        if (deps().some(d => d.code === body.code && d.id !== id)) {
          return fail('A0712', `department code already exists: ${body.code}`)
        }
        dep.code = body.code
      }
      if (body.name != null) dep.name = body.name
      dep.updateTime = rowStamp()
      return ok(true)
    },
  },
  {
    url: '/api/v1/system/org-deps/:id',
    method: 'DELETE',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // Bare deleteById semantics: the backend always returns TRUE.
      const id = Number(request.params?.id)
      const index = deps().findIndex(d => d.id === id)
      if (index >= 0) deps().splice(index, 1)
      return ok(true)
    },
  },
])

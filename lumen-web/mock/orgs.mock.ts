import { defineMock } from 'vite-plugin-mock-dev-server'

import {
  fail,
  guard,
  rowStamp,
  ok,
  orgIds,
  seedOrgs,
  state,
  type MockOrg,
} from './_fixtures'

/** Shared dev-session state (globalThis bucket - see _fixtures.ts for the plugin-convention note). */
const orgs = () => state<Record<string, MockOrg[]>>('orgs', () => ({ list: seedOrgs() })).list

function liveOrg(id: number): MockOrg | undefined {
  return orgs().find(o => o.id === id)
}

function requireLiveOrg(id: number, detail: string): Record<string, unknown> | null {
  if (!liveOrg(id)) return fail('A0400', `${detail}: org id ${id}`)
  return null
}

/** upId-assembly identical to OrgTreeService/TreeUtils: roots are upId = 0, leaf children stay null. */
function tree(): (MockOrg & { children: MockOrg[] | null })[] {
  const nodes = new Map<number, MockOrg & { children: MockOrg[] | null }>()
  for (const org of orgs()) nodes.set(org.id, { ...org, children: null })
  const roots: (MockOrg & { children: MockOrg[] | null })[] = []
  for (const node of nodes.values()) {
    if (node.upId === 0) {
      roots.push(node)
    } else {
      const parent = nodes.get(node.upId)
      if (parent) {
        parent.children = parent.children ?? []
        parent.children.push(node)
      } // else orphan - dropped (TreeUtils semantics)
    }
  }
  return roots
}

/**
 * Org endpoints (OrgController): the upId tree (multi-root capable) + CRUD. POST returns
 * the new id, PUT/DELETE return Boolean.TRUE (the GlobalResponseAdvice "200" contract).
 */
export default defineMock([
  {
    url: '/api/v1/system/orgs/tree',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      return ok(tree())
    },
  },
  {
    url: '/api/v1/system/orgs',
    method: 'POST',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const body = (request.body ?? {}) as { upId?: number; level?: number; type?: number; name?: string }
      if (body.upId == null) return fail('A0400', 'upId must not be null (0 = root)')
      if (!body.name) return fail('A0400', 'name must not be blank')
      // level is derived from the parent when omitted (roots default 1).
      const level = body.upId === 0 ? 1 : liveOrg(body.upId)?.level
      if (body.upId !== 0) {
        const missing = requireLiveOrg(body.upId, 'new parent org does not exist')
        if (missing) return missing
      }
      const id = Math.max(...orgs().map(o => o.id), 0) + 1
      const org: MockOrg = {
        id,
        upId: body.upId,
        level: body.level ?? level ?? 1,
        type: body.type ?? null,
        name: body.name,
        createBy: null,
        createTime: rowStamp(),
        updateBy: null,
        updateTime: rowStamp(),
      }
      orgs().push(org)
      orgIds().push(id)
      return ok(id)
    },
  },
  {
    url: '/api/v1/system/orgs/:id',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      const org = liveOrg(id)
      if (!org) return fail('A0400', `org does not exist: org id ${id}`)
      const body = (request.body ?? {}) as { upId?: number; level?: number; type?: number; name?: string }
      if (body.upId != null) {
        if (body.upId === id) return fail('A0400', 'a org cannot be re-parented under itself')
        if (body.upId !== 0) {
          const missing = requireLiveOrg(body.upId, 'new parent org does not exist')
          if (missing) return missing
        }
        org.upId = body.upId
      }
      // Partial update: null fields are skipped (the MyBatis-Plus NOT-NULL update strategy).
      if (body.level != null) org.level = body.level
      if (body.type != null) org.type = body.type
      if (body.name != null) org.name = body.name
      org.updateTime = rowStamp()
      return ok(true)
    },
  },
  {
    url: '/api/v1/system/orgs/:id',
    method: 'DELETE',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // Bare deleteById semantics: the backend always returns TRUE.
      const id = Number(request.params?.id)
      const index = orgs().findIndex(o => o.id === id)
      if (index >= 0) orgs().splice(index, 1)
      const idIndex = orgIds().indexOf(id)
      if (idIndex >= 0) orgIds().splice(idIndex, 1)
      return ok(true)
    },
  },
])

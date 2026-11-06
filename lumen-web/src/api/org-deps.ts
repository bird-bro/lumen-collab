/**
 * Department typed clients. Paths verbatim from the api spec's department
 * requirements (org-management-redesign-api): `/list` stays for existing
 * callers, `/page` is the department table's server-side query. Duplicate
 * live codes reject A0712; a hospital-root/disabled-campus owner rejects
 * A0400; the code is immutable after creation (clients cannot send it on PUT).
 */
import { apiRequest } from './client';
import type { CreateDepBody, DepPageQuery, OrgDep, Page, UpdateDepBody } from './types';

/**
 * GET /system/org-deps/list?orgId= - that org's departments; an absent
 * `orgId` returns all live departments (id-asc order).
 */
export function listDeps(orgId?: number): Promise<OrgDep[]> {
  return apiRequest<OrgDep[]>('/system/org-deps/list', { method: 'GET', query: { orgId } });
}

/**
 * GET /system/org-deps/page - the department table's server-side query.
 * Absent `orgId` spans all campuses; a present `orgId` must be a campus.
 * Falls back to the paging defaults (pageNum=1, pageSize=20) so the URL is
 * always deterministic, mirroring the roles client.
 */
export function pageDep(query: DepPageQuery = {}): Promise<Page<OrgDep>> {
  return apiRequest<Page<OrgDep>>('/system/org-deps/page', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      orgId: query.orgId,
      keyword: query.keyword,
      status: query.status,
    },
  });
}

/** POST /system/org-deps - returns the new department id. */
export function createDep(body: CreateDepBody): Promise<number> {
  return apiRequest<number>('/system/org-deps', { method: 'POST', body });
}

/** PUT /system/org-deps/{id} - partial update. */
export function updateDep(id: number, body: UpdateDepBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/org-deps/${id}`, { method: 'PUT', body });
}

/** DELETE /system/org-deps/{id}. */
export function deleteDep(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/org-deps/${id}`, { method: 'DELETE' });
}

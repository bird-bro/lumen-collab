/**
 * Department typed clients (task 4.3). Paths verbatim from the api delta
 * section "Department Endpoints" - the list filters by org; duplicate live
 * codes reject with A0712.
 */
import { apiRequest } from './client';
import type { CreateDepBody, OrgDep, UpdateDepBody } from './types';

/**
 * GET /system/org-deps/list?orgId= - that org's departments (orgId filter
 * optional, per the implemented backend surface; id-asc order).
 */
export function listDeps(orgId?: number): Promise<OrgDep[]> {
  return apiRequest<OrgDep[]>('/system/org-deps/list', { method: 'GET', query: { orgId } });
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

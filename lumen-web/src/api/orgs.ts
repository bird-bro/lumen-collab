/**
 * Org-hierarchy typed clients (task 4.3). Paths verbatim from the api delta
 * section "Org Tree Endpoints" - the upId-assembled tree is multi-root
 * capable and includes the seeded hospital root.
 */
import { apiRequest } from './client';
import type { CreateOrgBody, OrgNode, UpdateOrgBody } from './types';

/** GET /system/orgs/tree - the nested org tree (leaf children null). */
export function orgTree(): Promise<OrgNode[]> {
  return apiRequest<OrgNode[]>('/system/orgs/tree', { method: 'GET' });
}

/** POST /system/orgs - returns the new org id (upId 0 creates a root). */
export function createOrg(body: CreateOrgBody): Promise<number> {
  return apiRequest<number>('/system/orgs', { method: 'POST', body });
}

/** PUT /system/orgs/{id} - partial update (null fields skipped server-side). */
export function updateOrg(id: number, body: UpdateOrgBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/orgs/${id}`, { method: 'PUT', body });
}

/** DELETE /system/orgs/{id}. */
export function deleteOrg(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/orgs/${id}`, { method: 'DELETE' });
}

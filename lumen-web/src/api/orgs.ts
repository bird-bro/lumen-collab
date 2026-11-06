/**
 * Org typed clients. Paths verbatim from the api spec's Controlled
 * Organization Management Endpoints (org-management-redesign-api): the tree
 * carries the hospital/campus profile fields, POST creates only the bootstrap
 * hospital root or a campus under it (a second live root is rejected A0400),
 * PUT updates profile/status only (reparenting and type changes are
 * rejected), DELETE is guarded by live dependents.
 */
import { apiRequest } from './client';
import type { CreateOrgBody, OrgNode, UpdateOrgBody } from './types';

/** GET /system/orgs/tree - the controlled hospital/campus tree with profile fields. */
export function orgTree(params?: { name?: string }): Promise<OrgNode[]> {
  const name = params?.name?.trim();
  return apiRequest<OrgNode[]>('/system/orgs/tree', {
    method: 'GET',
    query: name ? { name } : undefined,
  });
}

/**
 * POST /system/orgs - returns the new org id. `upId: 0` creates the bootstrap
 * hospital root (rejected while a live root exists); the hospital id creates
 * a campus under it.
 */
export function createOrg(body: CreateOrgBody): Promise<number> {
  return apiRequest<number>('/system/orgs', { method: 'POST', body });
}

/** PUT /system/orgs/{id}/status - the explicit lifecycle toggle (0 = disabled, 1 = enabled). */
export function setOrgStatus(id: number, status: 0 | 1): Promise<boolean> {
  return apiRequest<boolean>(`/system/orgs/${id}/status`, { method: 'PUT', body: { status } });
}

/** PUT /system/orgs/{id} - partial profile/status update (null fields skipped server-side). */
export function updateOrg(id: number, body: UpdateOrgBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/orgs/${id}`, { method: 'PUT', body });
}

/** DELETE /system/orgs/{id} - guarded by live child orgs and live departments. */
export function deleteOrg(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/orgs/${id}`, { method: 'DELETE' });
}

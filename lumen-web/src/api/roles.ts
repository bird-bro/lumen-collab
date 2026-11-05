/**
 * Role-management typed clients (task 4.3). Paths verbatim from the api
 * delta section "Role Management Endpoints" - the list is the declared GAP
 * endpoint (unimplemented in the backend until the follow-up change), and
 * the menu assignment is a FULL REPLACE of the role's grant.
 */
import { apiRequest } from './client';
import type { CreateRoleBody, Page, RoleItem, UpdateRoleBody } from './types';

/**
 * GET /system/roles/list - the IPage-wrapped role list (pageNum/pageSize per
 * the copied PageSizeConfig convention). Records may carry the `menuIds`
 * grant echo (see RoleItem) pending the backend; the list endpoint is not
 * implemented yet (api delta gap, 2026-11-03).
 */
export function listRoles(pageNum: number = 1, pageSize: number = 20): Promise<Page<RoleItem>> {
  return apiRequest<Page<RoleItem>>('/system/roles/list', {
    method: 'GET',
    query: { pageNum, pageSize },
  });
}

/** POST /system/roles - returns the new role id (duplicate name rejects with an A-class code). */
export function createRole(body: CreateRoleBody): Promise<number> {
  return apiRequest<number>('/system/roles', { method: 'POST', body });
}

/** PUT /system/roles/{id} - partial update. */
export function updateRole(id: number, body: UpdateRoleBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/roles/${id}`, { method: 'PUT', body });
}

/** DELETE /system/roles/{id} - cascades unbind user_role and role_menu rows. */
export function deleteRole(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/roles/${id}`, { method: 'DELETE' });
}

/**
 * PUT /system/roles/{id}/menus - the FULL-REPLACE grant: the posted menu-id
 * set becomes the role's complete grant (an empty array clears all grants).
 */
export function assignRoleMenus(id: number, menuIds: number[]): Promise<boolean> {
  return apiRequest<boolean>(`/system/roles/${id}/menus`, { method: 'PUT', body: { menuIds } });
}

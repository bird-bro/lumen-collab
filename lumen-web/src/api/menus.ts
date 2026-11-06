/**
 * Menu typed client (task 4.3; management family added by menu-management-web).
 * The tree path is verbatim from the api delta section "Menu Tree Endpoint" -
 * the unscoped directory/menu/button tree. The lifecycle family (create /
 * full-field update / guarded delete) is verbatim from the delta section
 * "Menu Management Endpoints" (backend change menu-management-api, archived
 * 2026-11-06): creation auto-grants the admin role server-side, delete rejects
 * with an A-class message while live children exist.
 */
import { apiRequest } from './client';
import type { CreateMenuBody, MenuNode, UpdateMenuBody } from './types';

/** GET /system/menus/tree - the whole menu tree keyed by `mid`, with perms strings. */
export function menuTree(): Promise<MenuNode[]> {
  return apiRequest<MenuNode[]>('/system/menus/tree', { method: 'GET' });
}

/** POST /system/menus - create a node (auto-granted to the admin role server-side). */
export function createMenu(body: CreateMenuBody): Promise<number> {
  return apiRequest<number>('/system/menus', { method: 'POST', body });
}

/** PUT /system/menus/{id} - full-field update including reparenting via `upId`. */
export function updateMenu(id: number, body: UpdateMenuBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/menus/${id}`, { method: 'PUT', body });
}

/** DELETE /system/menus/{id} - guarded soft delete (live children reject with A0400). */
export function removeMenu(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/menus/${id}`, { method: 'DELETE' });
}

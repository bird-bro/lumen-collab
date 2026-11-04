/**
 * Menu typed client (task 4.3). Path verbatim from the api delta section
 * "Menu Tree Endpoint" - the unscoped directory/menu/button tree.
 */
import { apiRequest } from './client';
import type { MenuNode } from './types';

/** GET /system/menus/tree - the whole menu tree keyed by `mid`, with perms strings. */
export function menuTree(): Promise<MenuNode[]> {
  return apiRequest<MenuNode[]>('/system/menus/tree', { method: 'GET' });
}

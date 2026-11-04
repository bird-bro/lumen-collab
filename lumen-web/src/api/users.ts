/**
 * User-management typed clients (task 4.3). Paths verbatim from the api
 * delta section "User Management Endpoints" - the list is a PLAIN array (no
 * page wrapper, per the implemented surface).
 */
import { apiRequest } from './client';
import type { CreateUserBody, UserItem } from './types';

/** GET /system/users/list - plain UserItemDto array (BCrypt hash never serializes). */
export function listUsers(): Promise<UserItem[]> {
  return apiRequest<UserItem[]>('/system/users/list', { method: 'GET' });
}

/** POST /system/users - returns the new user id (duplicate username rejects with an A-class code). */
export function createUser(body: CreateUserBody): Promise<number> {
  return apiRequest<number>('/system/users', { method: 'POST', body });
}

/** PUT /system/users/{id}/password - resets the password (forces a change at next login). */
export function resetPassword(id: number, password: string): Promise<boolean> {
  return apiRequest<boolean>(`/system/users/${id}/password`, {
    method: 'PUT',
    body: { newPassword: password },
  });
}

/** PUT /system/users/{id}/enable - toggles the enable flag (0 | 1). */
export function enableUser(id: number, enable: 0 | 1): Promise<boolean> {
  return apiRequest<boolean>(`/system/users/${id}/enable`, { method: 'PUT', body: { enable } });
}

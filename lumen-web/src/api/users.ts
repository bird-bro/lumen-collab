/**
 * User-management typed clients (task 4.3). Paths verbatim from the api
 * delta section "User Management Endpoints" - the list is a PLAIN array (no
 * page wrapper, per the implemented surface).
 *
 * Password transport encryption (password-transport-encryption-web task 2.4,
 * coordination D4/D6): the create `password` and reset `newPassword` are SM2-encrypted
 * INSIDE this client boundary (design D2) - raw-password signatures stay human-readable,
 * and every current or future caller is safe by construction: the typed value never
 * reaches the wire as plaintext.
 */
import { apiRequest, ApiError } from './client';
import { encryptPassword, invalidateKeyCache } from '@/utils/password-cipher';
import type { CreateUserBody, UpdateUserBody, UserDetail, UserItem } from './types';

/** GET /system/users/list - plain UserItemDto array (BCrypt hash never serializes). */
export function listUsers(): Promise<UserItem[]> {
  return apiRequest<UserItem[]>('/system/users/list', { method: 'GET' });
}

/**
 * GET /system/users/list/profile - the richer read (user-edit-delete-profile
 * backend, api delta): rows carry the V26 profile columns plus the
 * batch-resolved roleNames/depName. The console page's data source.
 */
export function listUsersWithProfile(): Promise<UserItem[]> {
  return apiRequest<UserItem[]>('/system/users/list/profile', { method: 'GET' });
}

/** GET /system/users/{id} - the full profile detail (the edit dialog's echo). */
export function getUserDetail(id: number): Promise<UserDetail> {
  return apiRequest<UserDetail>(`/system/users/${id}`, { method: 'GET' });
}

/**
 * POST /system/users - returns the new user id (duplicate username rejects with an
 * A-class code). The initial `password` field is encrypted with the session-cached
 * public key before the body is built.
 */
export async function createUser(body: CreateUserBody): Promise<number> {
  try {
    return await apiRequest<number>('/system/users', {
      method: 'POST',
      body: { ...body, password: await encryptPassword(body.password) },
    });
  } catch (err) {
    // A0400 on a password endpoint = stale key after a server-side rotation: drop the
    // cached key so the NEXT operation refetches (design D3, single self-heal step).
    if (err instanceof ApiError && err.code === 'A0400') {
      invalidateKeyCache();
    }
    throw err;
  }
}

/**
 * PUT /system/users/{id}/password - resets the password (forces a change at next
 * login). The `newPassword` field is encrypted before the body is built; the raw
 * parameter signature is unchanged.
 */
export async function resetPassword(id: number, password: string): Promise<boolean> {
  try {
    return await apiRequest<boolean>(`/system/users/${id}/password`, {
      method: 'PUT',
      body: { newPassword: await encryptPassword(password) },
    });
  } catch (err) {
    if (err instanceof ApiError && err.code === 'A0400') {
      invalidateKeyCache();
    }
    throw err;
  }
}

/** PUT /system/users/{id}/enable - toggles the enable flag (0 | 1). */
export function enableUser(id: number, enable: 0 | 1): Promise<boolean> {
  return apiRequest<boolean>(`/system/users/${id}/enable`, { method: 'PUT', body: { enable } });
}

/**
 * PUT /system/users/{id} - the partial profile update (user-edit-delete-profile
 * api delta). No password/username fields exist on the body type - the backend
 * ignores a posted username by construction and the reset stays its own SM2
 * endpoint. A present `roleIds` full-replaces the bindings.
 */
export function updateUser(id: number, body: UpdateUserBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/users/${id}`, { method: 'PUT', body });
}

/** DELETE /system/users/{id} - the protected logical delete (self + seed-admin reject). */
export function deleteUser(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/users/${id}`, { method: 'DELETE' });
}

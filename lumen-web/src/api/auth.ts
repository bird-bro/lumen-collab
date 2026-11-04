/**
 * Auth-surface typed clients (batch 4 of web-foundation-auth-console, task
 * 4.3). Paths verbatim from `openspec/specs/api/spec.md` section
 * "Authentication Endpoints" - POST /api/v1/system/login (anonymous) and
 * GET /api/v1/system/me (token-guarded, profile + full permission set).
 */
import { apiRequest } from './client';
import type { LoginBody, LoginData, MeData } from './types';

/** POST /system/login - credentials, returns the JWT plus profile/menus/perms. */
export function login(body: LoginBody): Promise<LoginData> {
  return apiRequest<LoginData>('/system/login', { method: 'POST', body });
}

/** GET /system/me - profile + menus + perms of the authenticated user (no re-issued token). */
export function me(): Promise<MeData> {
  return apiRequest<MeData>('/system/me', { method: 'GET' });
}

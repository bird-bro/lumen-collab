/**
 * Device registry typed clients (org-device-room-web task 2.1; paths verbatim from
 * the api delta "Device Registry Endpoints"). The page query's `orgId` is OPTIONAL -
 * the tree's hospital-root selection means ALL campuses (second-round ruling, the
 * department page semantics); the room form's picker list narrows by `depId` to the
 * room's own 负责科室. Duplicate codes / dead or missing 使用科室 reject A0400
 * server-side and surface through `ApiError`.
 */
import { apiRequest } from './client';
import type {
  CreateDeviceBody,
  DevicePageQuery,
  OrgDevice,
  Page,
  UpdateDeviceBody,
} from './types';

/**
 * GET /system/org-devices/page - the device table's server-side query. Absent
 * `orgId` spans every campus (root = all); falls back to the paging defaults
 * (pageNum=1, pageSize=20) so the URL is always deterministic.
 */
export function pageDevice(query: DevicePageQuery = {}): Promise<Page<OrgDevice>> {
  return apiRequest<Page<OrgDevice>>('/system/org-devices/page', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      orgId: query.orgId,
      type: query.type,
      status: query.status,
      keyword: query.keyword,
    },
  });
}

/**
 * GET /system/org-devices/list?orgId=&depId= - the room form's picker feed,
 * name-ordered; department-scoped when `depId` is present.
 */
export function listDevices(orgId: number, depId?: number): Promise<OrgDevice[]> {
  return apiRequest<OrgDevice[]>('/system/org-devices/list', {
    method: 'GET',
    query: { orgId, depId },
  });
}

/** GET /system/org-devices/{id} - the edit drawer's echo. */
export function getDevice(id: number): Promise<OrgDevice> {
  return apiRequest<OrgDevice>(`/system/org-devices/${id}`, { method: 'GET' });
}

/** POST /system/org-devices - returns the new device id. */
export function createDevice(body: CreateDeviceBody): Promise<number> {
  return apiRequest<number>('/system/org-devices', { method: 'POST', body });
}

/** PUT /system/org-devices/{id} - full-replace (D9 null-aware server side). */
export function updateDevice(id: number, body: UpdateDeviceBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/org-devices/${id}`, { method: 'PUT', body });
}

/** DELETE /system/org-devices/{id} - guarded by live room references server side. */
export function deleteDevice(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/org-devices/${id}`, { method: 'DELETE' });
}

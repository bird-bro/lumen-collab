/**
 * Exam-room registry typed clients (org-device-room-web task 2.1; paths verbatim
 * from the api delta "Exam Room Registry Endpoints"). Page/detail rows carry the
 * JOINED device summary (devId/devName/devAe/devIp/devPort - the room row itself
 * stays device-free, the split model's read shape). The update's `devId` rebinds or
 * clears (null/absent = unbind, D9); the status command touches only `enable` (D6).
 */
import { apiRequest } from './client';
import type {
  CreateRoomBody,
  OrgRoom,
  Page,
  RoomPageQuery,
  RoomRow,
  UpdateRoomBody,
} from './types';

/**
 * GET /system/org-rooms/page - the room table's server-side query. Absent `orgId`
 * spans every campus (root = all); falls back to the paging defaults.
 */
export function pageRoom(query: RoomPageQuery = {}): Promise<Page<RoomRow>> {
  return apiRequest<Page<RoomRow>>('/system/org-rooms/page', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      orgId: query.orgId,
      depId: query.depId,
      name: query.name,
    },
  });
}

/** GET /system/org-rooms/list?orgId=&depId= - live rooms, join-free. */
export function listRooms(orgId: number, depId?: number): Promise<OrgRoom[]> {
  return apiRequest<OrgRoom[]>('/system/org-rooms/list', {
    method: 'GET',
    query: { orgId, depId },
  });
}

/** GET /system/org-rooms/{id} - the edit drawer's echo (with the device summary). */
export function getRoom(id: number): Promise<RoomRow> {
  return apiRequest<RoomRow>(`/system/org-rooms/${id}`, { method: 'GET' });
}

/** POST /system/org-rooms - returns the new room id. */
export function createRoom(body: CreateRoomBody): Promise<number> {
  return apiRequest<number>('/system/org-rooms', { method: 'POST', body });
}

/** PUT /system/org-rooms/{id} - full-replace incl. rebind/clear of `devId` (D9). */
export function updateRoom(id: number, body: UpdateRoomBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/org-rooms/${id}`, { method: 'PUT', body });
}

/** PUT /system/org-rooms/{id}/status - only `enable` changes (D6). */
export function setRoomStatus(id: number, enable: 0 | 1): Promise<boolean> {
  return apiRequest<boolean>(`/system/org-rooms/${id}/status`, {
    method: 'PUT',
    body: { enable },
  });
}

/** DELETE /system/org-rooms/{id} - logical delete. */
export function deleteRoom(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/org-rooms/${id}`, { method: 'DELETE' });
}

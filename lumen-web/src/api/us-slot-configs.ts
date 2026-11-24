/**
 * The slot-config administration client (us-registration-redesign-api tasks 4.1-4.3,
 * 拍板 06-A — 预约设置): the per-modality pre-registration policy CRUD over
 * `/api/v1/us/slot-configs`, gated by `setting:slot:*` server-side. The modality
 * token is the dict-1024 VALUE (immutable after create); the windows ride as the
 * structured {start,end} pairs.
 */
import { apiRequest } from './client';

export interface SlotWindow {
  start: string;
  end: string;
}

export interface SlotConfigView {
  id: number;
  modality: string;
  granularityMinutes: number;
  slotCapacity: number;
  openWindows: SlotWindow[] | null;
}

export interface SlotConfigBody {
  modality: string;
  granularityMinutes: number;
  slotCapacity: number;
  openWindows: SlotWindow[];
}

export function listSlotConfigs(): Promise<SlotConfigView[]> {
  return apiRequest<SlotConfigView[]>('/us/slot-configs');
}

export function createSlotConfig(body: SlotConfigBody): Promise<SlotConfigView> {
  return apiRequest<SlotConfigView>('/us/slot-configs', { method: 'POST', body });
}

export function updateSlotConfig(id: number, body: SlotConfigBody): Promise<SlotConfigView> {
  return apiRequest<SlotConfigView>(`/us/slot-configs/${id}`, { method: 'PUT', body });
}

export function deleteSlotConfig(id: number): Promise<void> {
  return apiRequest<void>(`/us/slot-configs/${id}`, { method: 'DELETE' });
}

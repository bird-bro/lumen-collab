/**
 * Report-template typed clients (exam-setting-web task 1.1). The scope rides `type`
 * (1 公共 / 2 个人 - the console's tab state) threaded into every call; PRIVATE rows
 * bind the session owner server-side and refuse non-owner writes content-free.
 */
import { apiRequest } from './client';
import type {
  CreateRepTempBody,
  Page,
  RepTemp,
  RepTempNode,
  RepTempPageQuery,
  UpdateRepTempBody,
} from './types';

/**
 * GET /setting/rep-temps/tree?modality=&type= - the scope's full folder/template tree
 * (private requests resolve to the CALLER's own rows only).
 */
export function repTempTree(modality: string, type: number): Promise<RepTempNode[]> {
  return apiRequest<RepTempNode[]>('/setting/rep-temps/tree', {
    method: 'GET',
    query: { modality, type },
  });
}

/**
 * GET /setting/rep-temps/page - the templates (flag=1) under ONE parent node of one
 * scope, name-filtered and paged (the right-pane table feed).
 */
export function pageRepTemps(query: RepTempPageQuery): Promise<Page<RepTemp>> {
  return apiRequest<Page<RepTemp>>('/setting/rep-temps/page', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      type: query.type,
      modality: query.modality,
      upId: query.upId,
      name: query.name,
    },
  });
}

/** POST /setting/rep-temps - returns the new node id (private rows bind the session owner). */
export function createRepTemp(body: CreateRepTempBody): Promise<number> {
  return apiRequest<number>('/setting/rep-temps', { method: 'POST', body });
}

/**
 * PUT /setting/rep-temps/{id} - full-replace of the EDITABLE surface; scope/ownership
 * stay as stored server-side.
 */
export function updateRepTemp(id: number, body: UpdateRepTempBody): Promise<boolean> {
  return apiRequest<boolean>(`/setting/rep-temps/${id}`, { method: 'PUT', body });
}

/** DELETE /setting/rep-temps/{id} - logical delete (private rows refuse non-owners). */
export function deleteRepTemp(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/setting/rep-temps/${id}`, { method: 'DELETE' });
}

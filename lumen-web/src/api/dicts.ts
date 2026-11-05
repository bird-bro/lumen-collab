/**
 * Dictionary typed clients (task 4.3). Paths verbatim from the
 * web-system-console spec delta section "Dictionary Management" -
 * `/system/dicts/catal/**` catalogs (IPage list) and `/system/dicts/detail/**`
 * per-catalog details (plain array ordered by `sort` asc).
 */
import { apiRequest } from './client';
import type {
  CreateDictCatalBody,
  CreateDictDetailBody,
  DictCatal,
  DictDetail,
  Page,
  UpdateDictCatalBody,
  UpdateDictDetailBody,
} from './types';

/**
 * GET /system/dicts/catal/list - the IPage-wrapped catalog list
 * (pageNum/pageSize per the copied PageSizeConfig convention, id desc).
 */
export function listCatal(pageNum: number = 1, pageSize: number = 20): Promise<Page<DictCatal>> {
  return apiRequest<Page<DictCatal>>('/system/dicts/catal/list', {
    method: 'GET',
    query: { pageNum, pageSize },
  });
}

/** POST /system/dicts/catal - returns the new catalog id (duplicate name rejects with an A-class code). */
export function createCatal(body: CreateDictCatalBody): Promise<number> {
  return apiRequest<number>('/system/dicts/catal', { method: 'POST', body });
}

/** PUT /system/dicts/catal/{id} - partial update. */
export function updateCatal(id: number, body: UpdateDictCatalBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/dicts/catal/${id}`, { method: 'PUT', body });
}

/** DELETE /system/dicts/catal/{id}. */
export function deleteCatal(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/dicts/catal/${id}`, { method: 'DELETE' });
}

/**
 * GET /system/dicts/detail/list?caId= - that catalog's details as a plain
 * array ordered by `sort` ascending (V12: the query anchors by the catalog id -
 * the former `caCode=` parameter died with the dict_catal.code column).
 */
export function listDetails(caId: number): Promise<DictDetail[]> {
  return apiRequest<DictDetail[]>('/system/dicts/detail/list', {
    method: 'GET',
    query: { caId },
  });
}

/** POST /system/dicts/detail - returns the new detail id (duplicate value under a catalog rejects). */
export function createDetail(body: CreateDictDetailBody): Promise<number> {
  return apiRequest<number>('/system/dicts/detail', { method: 'POST', body });
}

/** PUT /system/dicts/detail/{id} - partial update. */
export function updateDetail(id: number, body: UpdateDictDetailBody): Promise<boolean> {
  return apiRequest<boolean>(`/system/dicts/detail/${id}`, { method: 'PUT', body });
}

/** DELETE /system/dicts/detail/{id}. */
export function deleteDetail(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/system/dicts/detail/${id}`, { method: 'DELETE' });
}

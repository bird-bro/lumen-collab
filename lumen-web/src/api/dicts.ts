/**
 * Dictionary typed clients (task 4.3). Paths verbatim from the
 * web-system-console spec delta section "Dictionary Management" -
 * `/system/dicts/catal/**` catalogs (IPage list) and `/system/dicts/detail/**`
 * per-catalog details (plain array ordered by `sort` asc).
 */
import { apiRequest } from './client';
import type {
  CityDict,
  DictItem,
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

/**
 * GET /system/dicts/city/list - administrative-divisions reference rows
 * (spec `system-data` § City Reference Query; READ-ONLY dict, no write path).
 * Both filters are optional server-side; callers of the region cascade pass
 * exactly one per request (`level: 1` for provinces, `upId` for one node's
 * children) and NEVER query bare - an unfiltered call returns all 3751 rows.
 */
const CITY_DICT_CACHE = new Map<string, Promise<CityDict[]>>()

/**
 * `city_dict` is read-only reference data, so identical queries are coalesced
 * for the session. This keeps stacked region cells (hospital card + campus
 * rows) from firing the same province/city/district lookup repeatedly. A failed
 * request is not retained: the next caller retries.
 */
function cityDictCacheKey(params: { level?: number; upId?: number }): string {
  return JSON.stringify({ level: params.level ?? null, upId: params.upId ?? null })
}

export function listCityDicts(params: { level?: number; upId?: number }): Promise<CityDict[]> {
  const key = cityDictCacheKey(params)
  const cached = CITY_DICT_CACHE.get(key)
  if (cached) return cached

  const request = apiRequest<CityDict[]>('/system/dicts/city/list', {
    method: 'GET',
    query: params,
  }).catch((error: unknown) => {
    CITY_DICT_CACHE.delete(key)
    throw error
  })
  CITY_DICT_CACHE.set(key, request)
  return request
}

/**
 * GET /system/dicts/detail/items/{catalogName} - the label-lookup endpoint
 * (role-type-dict-web): the enabled catalog's live details as the slim
 * `{code, value}[]` projection, sort ascending. The catalog name rides the
 * path RAW (the recorded cross-stack contract - pre-encoded %XX segments land
 * on the security entry point; the fetch layer encodes automatically). An
 * unknown or disabled catalog resolves to an empty array, never an error.
 * Cached per name - use `dictItemsByName` from `@/utils/dict-items` (session
 * cache) for page consumers; this raw client is the uncached transport.
 */
export function fetchDictItemsByName(catalogName: string): Promise<DictItem[]> {
  return apiRequest<DictItem[]>(`/system/dicts/detail/items/${catalogName}`, {
    method: 'GET',
  });
}

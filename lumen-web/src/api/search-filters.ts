/**
 * The generic saved-search REST client (us-registration-redesign-api task 5.2,
 * 拍板 07-A — 我的查询): uid-owned rows over `/api/v1/system/search-filters`,
 * authenticated-only (no perm family). The query jsonb passes through verbatim —
 * its shape belongs to each workbench page. Transport mirrors the house clients.
 */
import { apiRequest } from './client';

export interface SearchFilterView {
  id: number;
  pageKey: string;
  name: string;
  query: Record<string, unknown>;
  isDefault: boolean;
  updateTime: string;
}

export interface SearchFilterSaveBody {
  pageKey: string;
  name: string;
  query?: Record<string, unknown>;
  isDefault?: boolean;
}

export function listFilters(pageKey?: string): Promise<SearchFilterView[]> {
  return apiRequest<SearchFilterView[]>('/system/search-filters', {
    query: pageKey ? { pageKey } : undefined,
  });
}

export function createFilter(body: SearchFilterSaveBody): Promise<SearchFilterView> {
  return apiRequest<SearchFilterView>('/system/search-filters', { method: 'POST', body });
}

export function updateFilter(id: number, body: SearchFilterSaveBody): Promise<SearchFilterView> {
  return apiRequest<SearchFilterView>(`/system/search-filters/${id}`, { method: 'PUT', body });
}

export function setDefaultFilter(id: number): Promise<SearchFilterView> {
  return apiRequest<SearchFilterView>(`/system/search-filters/${id}/default`, { method: 'PUT' });
}

export function deleteFilter(id: number): Promise<void> {
  return apiRequest<void>(`/system/search-filters/${id}`, { method: 'DELETE' });
}

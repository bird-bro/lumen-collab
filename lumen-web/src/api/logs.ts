/**
 * Operation-log typed client (task 4.3). Path verbatim from the
 * web-system-console spec delta section "Operation Log Query" - a paged,
 * read-only surface (append-only data; no mutation endpoints exist).
 */
import { apiRequest } from './client';
import type { ListLogsQuery, LogItem, Page } from './types';

/**
 * GET /system/logs - the IPage-wrapped log query, actionTime descending.
 * Filters (`type`/`uid`/`dateYm`) are optional and AND-combined server-side;
 * paging uses the `page`/`pageSize` keys of the logs endpoint's own
 * convention (note: NOT the pageNum/pageSize of roles/dicts).
 */
export function listLogs(query: ListLogsQuery): Promise<Page<LogItem>> {
  return apiRequest<Page<LogItem>>('/system/logs', { method: 'GET', query });
}

/**
 * Operation-log typed client (task 4.3). Path verbatim from the
 * web-system-console spec delta section "Operation Log Query" - a paged,
 * read-only surface (append-only data; no mutation endpoints exist).
 */
import { apiRequest } from './client';
import type { ListLogsQuery, ListSignLogsQuery, LogItem, LogSignItem, Page } from './types';

/**
 * GET /system/logs - the IPage-wrapped log query, actionTime descending.
 * Filters (`type`/`uid`/`dateYm`) are optional and AND-combined server-side;
 * paging uses the `page`/`pageSize` keys of the logs endpoint's own
 * convention (note: NOT the pageNum/pageSize of roles/dicts).
 */
export function listLogs(query: ListLogsQuery): Promise<Page<LogItem>> {
  return apiRequest<Page<LogItem>>('/system/logs', { method: 'GET', query });
}

/**
 * GET /system/logs/sign - the sign-log (登录日志) query, actionTime descending
 * (sign-log-query batch 4, task 4.1; api delta "Sign Log Query Endpoint").
 * Same `page`/`pageSize` convention as `listLogs`; filters (`account`/`uid`/
 * `dateYm`) are optional and AND-combined server-side. The server selects
 * every column except `token` (design D7), and the `LogSignItem` type carries
 * no token field either - credential material never reaches admin screens.
 */
export function listSignLogs(query: ListSignLogsQuery): Promise<Page<LogSignItem>> {
  return apiRequest<Page<LogSignItem>>('/system/logs/sign', { method: 'GET', query });
}

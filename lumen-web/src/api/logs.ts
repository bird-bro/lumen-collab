/**
 * Audit-log typed client (audit-log-unify-web 1.2 — the unified endpoint).
 * Path verbatim from the web-system-console spec delta section "Operation Log
 * Query" (as modified by audit-log-unify): `GET /system/logs` is the SINGLE
 * audit query serving BOTH event families (operations 1–5, sign events
 * 10/11/12) — the former `listSignLogs` (`/system/logs/sign`) retired with the
 * backend endpoint (coordination audit-log-unify D8). Read-only surface;
 * append-only data; no mutation endpoints exist.
 */
import { apiRequest } from './client';
import type { ListLogsQuery, LogItem, Page } from './types';

/**
 * GET /system/logs - the IPage-wrapped audit query, actionTime descending.
 * Filters (`type` Integer code / `uid` / `account` / `dateYm`) are optional and
 * AND-combined server-side; paging uses the `page`/`pageSize` keys of the logs
 * endpoint's own convention (note: NOT the pageNum/pageSize of roles/dicts).
 * Rows never carry the `session` credential column (server select-exclusion).
 */
export function listLogs(query: ListLogsQuery): Promise<Page<LogItem>> {
  return apiRequest<Page<LogItem>>('/system/logs', { method: 'GET', query });
}

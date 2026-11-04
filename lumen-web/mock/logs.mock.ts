import { defineMock } from 'vite-plugin-mock-dev-server'

import {
  guard,
  ok,
  pageOf,
  seedLogs,
  state,
  type MockLog,
} from './_fixtures'

/** Shared dev-session state (globalThis bucket - see _fixtures.ts for the plugin-convention note). */
const logs = () => state<Record<string, MockLog[]>>('logs', () => ({ list: seedLogs() })).list

/**
 * GET /api/v1/system/logs (LogController) - the paged operation-log query with the
 * optional type/uid/dateYm filters AND-combined, actionTime descending, read-only
 * (append-only data - deliberately no mutation endpoints).
 */
export default defineMock([
  {
    url: '/api/v1/system/logs',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const page = Number(request.query?.page ?? 1)
      const pageSize = Number(request.query?.pageSize ?? 20)
      const type = request.query?.type as string | undefined
      const uid = request.query?.uid != null ? Number(request.query.uid) : null
      const dateYm = request.query?.dateYm != null ? Number(request.query.dateYm) : null
      const rows = logs()
        .filter(
          l =>
            (type == null || l.type === type)
            && (uid == null || l.uid === uid)
            && (dateYm == null || l.dateYm === dateYm),
        )
        .sort((a, b) => b.actionTime.localeCompare(a.actionTime)) // actionTime DESC
      return ok(pageOf(rows.slice((page - 1) * pageSize, page * pageSize), page, pageSize))
    },
  },
])

# Design — audit-log-unify-web

> Coordination: `openspec/coordination/audit-log-unify.md`. Backend sibling
> `audit-log-unify-api` lands FIRST; this change builds against the real merged
> endpoint (no mock layer ruling).

## Decisions

D-web-1 one page one table (superset columns, em-dash for cross-family gaps) ·
D-web-2 dict-driven 类别 Select + column labels via the existing `useDictItems`
hook (role-type-dict D4 fallback pattern verbatim: fetch failure → one message.error
+ raw-code fallback, never blocks) · D-web-3 client convergence (single `LogItem`,
`type: number`, `account` filter; `LogSignItem`/`listSignLogs` deleted) · D-web-4
`sign.tsx`/`/sign` route/menu-path mapping deleted with the retired backend surface ·
D-web-5 existing-page edit — UI-prototype gate skipped.

## Page shape — one page, one table

`pages/system/logs/index.tsx` becomes the 审计日志 page (route/component
unchanged — menu drives the title). `sign.tsx` is deleted; the router loses the
`/sign` route and `menu-paths.ts` drops the `system/log/sign` mapping (menu 17
retired backend-side; the mapping was fixture-parity anyway).

**Columns** (superset of both old tables, one `LogItem` type):

| 列 | dataIndex | sign row | operation row |
|:--|:--|:--|:--|
| 时间 | actionTime | ✓ | ✓ |
| 类别 | type | dict label (登录成功/失败/登出) | dict label (新增/修改/…) |
| 标题 | title | "Sign-IN"/"Sign-OUT" | 模块名 |
| 描述 | descr | ✓ | ✓ |
| 操作人 | account | ✓ | ✓ |
| IP / 操作系统 / 浏览器 | ip/os/browser | ✓ | ✓ (aspect-filled) |
| 方法 / URL | method/url | — | ✓ |

**Filter bar**: 类别 = dict-driven Select (values = codes as numbers), 账号 =
Input (sign-page parity), 操作人ID = InputNumber, 月份 = InputNumber —
replacing the old free-text 类型 Input (the dict Select is strictly better:
only valid codes are sendable).

## Dict label resolution — role-type-dict pattern, no new machinery

`useDictItems('审计日志类型')` (session-cached, one wire call per session) →
code→label map; column render `map.get(type) ?? String(type)`; Select options
from the same list; fetch failure → one `message.error` + raw-code fallback
(role-type-dict D4 verbatim). No new client function — the by-name endpoint
and hook exist since role-type-dict.

## API client convergence

`types.ts`: `LogItem.type: number`; `LogSignItem` deleted. `logs.ts`:
`listLogs` query gains `account?: string`, `type` numeric; `listSignLogs`
deleted. The IPage unwrap and error path are unchanged.

## Edge cases

- Legacy rows (if any survive with String type in a dev DB): the backend
  re-type happens in the same deploy; the client renders `String(type)`
  fallback for any code the dict lacks — never crashes.
- `session` field: typed payload never declares it; nothing can render it.
- Empty dict catalog (seed not yet applied): Select renders empty, table shows
  raw codes — degrade, don't block.

## Testing

- `sign-logs-page.test.tsx` deleted; logs-page test extends to: category
  Select sends `type` as number; account filter sends `account`; sign-fixture
  row renders — in 方法/URL and a dict label in 类别; dict-fetch failure
  degrades to raw codes; no mutation controls (read-only assertion kept).
- `api-clients.test.ts`: `listLogs` query-shape tests updated (account join,
  numeric type); sign-client tests removed.
- Existing-page edit — UI-prototype gate skipped per workflow.

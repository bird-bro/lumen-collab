# Proposal: us-slot-list-filter

## Why
The 预约设置 list renders every configured policy at once; as the 检查类型 catalog
grows the operator cannot zero in on one type's policy.

## What Changes
- The list tool row gains a 检查类型 filter Select (aria-label 检查类型过滤, allowClear,
  width 160 — the device-page filter chrome) fed by the same dict-1024 items the
  drawer's select uses.
- Filtering is CLIENT-SIDE over the mount-time `GET /us/slot-configs` payload (no
  contract change): the endpoint returns one policy per 检查类型 — a catalog-bounded
  list — so an in-memory narrowing is materially equivalent to a server query.

## Affected Stacks
- single-stack: lumen-web (no backend change; the list contract is untouched)

## Scope
- In scope: `src/pages/setting/exam-slot/index.tsx` (filter state + Select +
  in-memory narrowing), `src/test/us-slot-config-page.test.tsx` (filter scenario).
- Out of scope: server-side query params on `/us/slot-configs` (a contract change —
  needs its own proposal if the list ever outgrows catalog scale), pagination.

## Success Criteria
- Picking a 检查类型 in the filter narrows the table to that policy; clearing the
  filter restores the full list.
- Filtering issues NO additional wire request (one list call per mount).

## Constraints
- Existing-page edit: no prototype round (skip per the web workflow).
- Filter chrome mirrors the device page's Select filter verbatim (width 160, allowClear).

## Risks
- None: read-only narrowing over an already-loaded payload.

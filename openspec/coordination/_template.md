# Coordination: <feature>

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "<feature>"`.

## Why
[Business reason for the cross-stack feature]

## Shared Design
[API contracts / data model / error codes negotiated across stacks - single source of truth]

## Decisions
- [Decision] - [rationale] - [date]

## Change Registry
| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | [change name] | proposing | - |
| frontend | [change name] | proposing | - |

## Gate
Feature done = ALL registered changes verify PASS -> archive each.

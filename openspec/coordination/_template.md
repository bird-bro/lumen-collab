# Coordination: <feature>

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "<feature>"`.

## Why
[Business reason for the cross-stack feature]

## Shared Design
[API contracts / data model / error codes negotiated across stacks - single source of truth.
Do NOT duplicate these details in per-stack proposals; per-stack artifacts reference this file.]

## Decisions
- [Decision] - [rationale] - [date]

## Change Registry
| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | [change name] | proposing | - |
| frontend | [change name] | proposing | - |

## Gate
Verify each stack independently. When ALL registered changes have an acceptable verify credential
(`PASS`, or user-confirmed `PASS_WITH_ISSUES`), preflight all credentials and batch-archive them.

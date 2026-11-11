# Changes - Delta Proposals

Active work lives here. Each proposal is a directory containing:
- `proposal.md` - Why / What / Scope / Success Criteria / Constraints / Risks
- `specs/<capability>/spec.md` - delta spec (## ADDED/MODIFIED/REMOVED Requirements + ### Requirement: + #### Scenario:)
- `loop-state.yaml` - per-change workflow state (copy from `_template/`; parallel-safe)
- `files.yaml` (optional) - declared file ownership, one path per line; consumed by
  `openspec/check-conflicts.sh` to detect overlap between active changes

## Parallel development
Different modules touching disjoint files may run in parallel sessions:
1. Each change keeps its own `loop-state.yaml` (never write the global `openspec/loop-state.yaml`).
2. Declare owned paths in `files.yaml`; run `bash openspec/check-conflicts.sh` before apply.
3. No overlap -> parallel-safe. Overlap -> serialize the changes or use separate git worktrees.
4. Parallel apply is fine; archive remains SERIAL (spec merges go to main `openspec/specs/`).

Start from `_template/`. On completion, move the directory to `../archive/` via `/opsx:archive`.

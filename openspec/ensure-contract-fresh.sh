#!/usr/bin/env bash
# ensure-contract-fresh.sh - Check/update execution-contract freshness via SHA256 hash
# Usage:
#   bash ensure-contract-fresh.sh <change-dir>            # check mode: exit 0=fresh, 1=stale
#   bash ensure-contract-fresh.sh --update <change-dir>   # update mode: compute + store hash
set -euo pipefail

mode="check"
if [[ "${1:-}" == "--update" ]]; then
  mode="update"; shift
fi

dir="${1:-}"
[[ -n "$dir" ]] || { echo "Usage: ensure-contract-fresh.sh [--update] <change-dir>" >&2; exit 2; }
[[ -d "$dir" ]] || { echo "Error: not a directory: $dir" >&2; exit 2; }

# Resolve state file: per-change loop-state.yaml preferred (parallel-safe);
# fall back to openspec/loop-state.yaml (legacy single-session state).
abs_dir="$(cd "$dir" && pwd)"
change_state="$abs_dir/loop-state.yaml"
openspec_dir=""
cur="$abs_dir"
while [[ "$cur" != "/" ]]; do
  if [[ -f "$cur/openspec/loop-state.yaml" ]]; then
    openspec_dir="$cur/openspec"
    break
  fi
  cur="$(dirname "$cur")"
done

if [[ -f "$change_state" ]]; then
  state_file="$change_state"
elif [[ -n "$openspec_dir" ]]; then
  state_file="$openspec_dir/loop-state.yaml"
else
  echo "Error: neither $change_state nor openspec/loop-state.yaml found" >&2
  exit 1
fi

# Compute current hash of planning artifacts
hash_input=""
for f in "$dir/proposal.md" "$dir/design.md" "$dir/tasks.md"; do
  [[ -f "$f" ]] && hash_input+="$(cat "$f")"
done
specs_dir="$dir/specs"
if [[ -d "$specs_dir" ]]; then
  while IFS= read -r sf; do
    [[ -f "$sf" ]] && hash_input+="$(cat "$sf")"
  done < <(find "$specs_dir" -name "spec.md" | sort)
fi

current_hash=$(printf '%s' "$hash_input" | shasum -a 256 | awk '{print $1}')

if [[ "$mode" == "update" ]]; then
  # Update hash in the PER-CHANGE state file (parallel-safe); create it if missing.
  if [[ ! -f "$change_state" ]]; then
    {
      echo "# Per-change workflow state (parallel-safe: each change owns its own file)."
      echo "phase: proposing"
      echo "change: $(basename "$dir")"
    } > "$change_state"
    # Seed workflow/progress fields from the legacy global state if present.
    if [[ -n "$openspec_dir" && -f "$openspec_dir/loop-state.yaml" ]]; then
      grep -E '^(workflow_mode|execution_mode|tasks_total|tasks_done):' "$openspec_dir/loop-state.yaml" >> "$change_state" 2>/dev/null || true
    fi
    grep -q '^workflow_mode:' "$change_state" || echo "workflow_mode: null" >> "$change_state"
    grep -q '^execution_mode:' "$change_state" || echo "execution_mode: null" >> "$change_state"
    grep -q '^tasks_total:' "$change_state" || echo "tasks_total: 0" >> "$change_state"
    grep -q '^tasks_done:' "$change_state" || echo "tasks_done: 0" >> "$change_state"
  fi
  if grep -q '^artifacts_hash:' "$change_state"; then
    # Portable in-place sed (macOS BSD sed needs -i '' with space; GNU sed needs -i'')
    _tmp=$(mktemp)
    sed "s/^artifacts_hash:.*/artifacts_hash: $current_hash/" "$change_state" > "$_tmp" && mv "$_tmp" "$change_state"
  else
    echo "artifacts_hash: $current_hash" >> "$change_state"
  fi
  echo "Updated artifacts_hash: $current_hash (in $change_state)"
  exit 0
fi

# Check mode: compare stored vs current
stored_hash=$(grep '^artifacts_hash:' "$state_file" 2>/dev/null | sed 's/^artifacts_hash: *//' | tr -d '"' || true)

if [[ -z "$stored_hash" || "$stored_hash" == "null" ]]; then
  echo "BLOCKED: artifacts_hash not set. Generate execution-contract.md and run: bash ensure-contract-fresh.sh --update <change-dir>" >&2
  exit 1
fi

if [[ "$stored_hash" == "$current_hash" ]]; then
  exit 0
else
  echo "BLOCKED: execution-contract.md is STALE. Planning artifacts changed but contract not regenerated." >&2
  echo "  stored:  $stored_hash" >&2
  echo "  current: $current_hash" >&2
  echo "  Fix: regenerate execution-contract.md, then run: bash ensure-contract-fresh.sh --update <change-dir>" >&2
  exit 1
fi

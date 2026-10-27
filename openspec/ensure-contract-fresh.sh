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

# Find loop-state.yaml by searching upward for openspec/
abs_dir="$(cd "$dir" && pwd)"
openspec_dir=""
cur="$abs_dir"
while [[ "$cur" != "/" ]]; do
  if [[ -f "$cur/openspec/loop-state.yaml" ]]; then
    openspec_dir="$cur/openspec"
    break
  fi
  cur="$(dirname "$cur")"
done

if [[ -z "$openspec_dir" ]]; then
  echo "Error: openspec/loop-state.yaml not found" >&2
  exit 1
fi

state_file="$openspec_dir/loop-state.yaml"

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
  # Update hash in loop-state.yaml
  if grep -q '^artifacts_hash:' "$state_file"; then
    # macOS sed needs -i ''
    # Portable in-place sed (macOS BSD sed needs -i '' with space; GNU sed needs -i'')
    _tmp=$(mktemp)
    sed "s/^artifacts_hash:.*/artifacts_hash: $current_hash/" "$state_file" > "$_tmp" && mv "$_tmp" "$state_file"
  else
    echo "artifacts_hash: $current_hash" >> "$state_file"
  fi
  echo "Updated artifacts_hash: $current_hash"
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

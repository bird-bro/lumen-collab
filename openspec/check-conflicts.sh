#!/usr/bin/env bash
# check-conflicts.sh - File-ownership overlap check between active changes (parallel-safety).
# Usage: bash openspec/check-conflicts.sh [project-dir]
# Exit: 0 = no overlap (parallel-safe); 1 = overlap detected; 2 = error.
#
# File declarations per change (in priority order):
#   1. openspec/changes/<name>/files.yaml  - explicit, one path per line
#      (YAML list form "- path" or bare "path"; # comments allowed). RECOMMENDED.
#   2. Fallback: path-like tokens extracted from tasks.md + execution-contract.md.
#
# Parallel rule: no overlap -> changes are parallel-safe (per-change loop-state.yaml,
# serial archive only). Overlap -> serialize the changes or use separate git worktrees.
set -uo pipefail

proj="${1:-.}"
abs_proj="$(cd "$proj" 2>/dev/null && pwd)" || { echo "Error: not a directory: $proj" >&2; exit 2; }
changes_dir="$abs_proj/openspec/changes"
[[ -d "$changes_dir" ]] || { echo "Error: openspec/changes/ not found (run scaffold.sh first)" >&2; exit 2; }

tmpdir="$(mktemp -d)" || exit 2
trap 'rm -rf "$tmpdir"' EXIT

_names=()
_idx=0

# Normalize heuristic tokens and drop noise + the change's own artifact paths.
_clean() {
  sed -e 's#^\./##' -e 's#^/##' -e 's#\.\./##g' -e 's#/$##' \
    -e 's#[^A-Za-z0-9_./-]*$##' \
    | grep -vE '(^|/)\.' \
    | grep -vE '^openspec/' \
    | grep -vE 'coordination/' \
    | grep -vE '^https?' \
    | grep -vE '^@' \
    | grep -vE '^(proposal|design|tasks|execution-contract|verify|debt|spec)\.md$' \
    | awk '{ lines[NR] = $0 }
      END {
        for (i = 1; i <= NR; i++) {
          l = lines[i]
          if (l !~ /\//) {
            esc = l; gsub(/[.]/, "\\.", esc)
            dup = 0
            for (j = 1; j <= NR; j++)
              if (lines[j] ~ ("/" esc "$")) { dup = 1; break }
            if (!dup) print l
          } else print l
        }
      }' \
    | sort -u
}

# Known workflow/coordination doc basenames are prose references, not code targets.
_drop_basenames="$tmpdir/drop.txt"
: > "$_drop_basenames"
printf '%s\n' guard.sh build-contract.sh ensure-contract-fresh.sh ensure-branch.sh \
  check-conflicts.sh validate-artifacts.py loop-state.yaml verify.config.yaml \
  token-baseline.json project.md config.yaml verify-result.template.md \
  _template.md README.md >> "$_drop_basenames"
if [[ -d "$abs_proj/openspec/coordination" ]]; then
  find "$abs_proj/openspec/coordination" -type f -exec basename {} \; >> "$_drop_basenames"
fi
sort -u "$_drop_basenames" -o "$_drop_basenames"

# Heuristic-only plausibility filter (files.yaml entries bypass this):
# - bare tokens without any extension are dropped ("mvnw" from "./mvnw", prose words)
# - extensionless slashed tokens must be real project paths (exist at project root
#   or start with an existing top-level dir) - drops prose like "red/green",
#   "uid/account", "10/11", "api/v1/..."
# - slashed tokens whose last segment has an extension are kept (may be
#   stack-relative paths like pages/system/logs/index.tsx)
# - bare tokens with an extension are kept (stack-relative filenames)
# - workflow/coordination basenames are dropped (prose references, shared by design)
_filter_exists() {
  local tok first last base
  while IFS= read -r tok; do
    [[ -n "$tok" ]] || continue
    [[ "$tok" == *.* || "$tok" == */* ]] || continue
    base="${tok##*/}"
    grep -qxF "$base" "$_drop_basenames" && continue
    if [[ "$tok" == */* ]]; then
      last="${tok##*/}"
      if [[ "$last" != *.* ]]; then
        first="${tok%%/*}"
        [[ -e "$abs_proj/$tok" || -e "$abs_proj/$first" ]] || continue
      fi
    fi
    printf '%s\n' "$tok"
  done
}

collect() {
  local d="$1" name="$2" raw="$tmpdir/raw.$_idx.txt"
  : > "$raw"
  # Explicit declarations are trusted verbatim.
  if [[ -f "$d/files.yaml" ]]; then
    grep -vE '^[[:space:]]*(#|$)' "$d/files.yaml" \
      | sed -e 's/^[[:space:]]*-[[:space:]]*//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
      | grep -vE '^$' >> "$tmpdir/files.$_idx.txt"
  fi
  local src
  for src in "$d/tasks.md" "$d/execution-contract.md"; do
    [[ -f "$src" ]] || continue
    { grep -oE '([A-Za-z0-9_.@-]+/)+[A-Za-z0-9_.@-]+([^A-Za-z0-9_@./-]|$)' "$src" || true
      grep -oE '[A-Za-z0-9_-]+\.(md|ts|tsx|js|jsx|vue|py|java|kt|go|rs|rb|php|c|h|cpp|hpp|cs|swift|sql|yml|yaml|json|xml|sh|scss|css|html|wxml|wxss)([^A-Za-z0-9_.-]|$)' "$src" || true
    } >> "$raw"
  done
  # Heuristic tokens: normalize aggressively, then keep only plausible paths.
  _clean "$name" < "$raw" | _filter_exists >> "$tmpdir/files.$_idx.txt"
  sort -u "$tmpdir/files.$_idx.txt" -o "$tmpdir/files.$_idx.txt"
  if [[ -s "$tmpdir/files.$_idx.txt" ]]; then
    _names+=("$name")
    _idx=$((_idx+1))
  fi
}

for d in "$changes_dir"/*/; do
  [[ -d "$d" ]] || continue
  name="$(basename "$d")"
  [[ "$name" == "_template" || "$name" == "archive" ]] && continue
  collect "$d" "$name"
done

if [[ $_idx -eq 0 ]]; then
  echo "No active changes with file declarations - nothing to check."
  echo "Tip: add openspec/changes/<name>/files.yaml (one path per line) for reliable ownership checks."
  exit 0
fi

echo "Active changes with file declarations: $_idx"
echo ""
echo "Per-change file ownership:"
i=0
while [[ $i -lt $_idx ]]; do
  echo "  ${_names[$i]}:"
  sed 's/^/    /' "$tmpdir/files.$i.txt"
  i=$((i+1))
done

conflicts=0
i=0
while [[ $i -lt $_idx ]]; do
  j=$((i+1))
  while [[ $j -lt $_idx ]]; do
    overlap="$(comm -12 "$tmpdir/files.$i.txt" "$tmpdir/files.$j.txt")"
    if [[ -n "$overlap" ]]; then
      conflicts=1
      echo ""
      echo "[CONFLICT] ${_names[$i]} <-> ${_names[$j]}"
      printf '%s\n' "$overlap" | sed 's/^/    /'
    fi
    j=$((j+1))
  done
  i=$((i+1))
done

echo ""
if [[ $conflicts -eq 1 ]]; then
  echo "Result: OVERLAP detected - serialize these changes or use separate git worktrees."
  exit 1
else
  echo "Result: no file overlap - changes are parallel-safe (archive serially)."
  exit 0
fi

#!/usr/bin/env bash
# openspec/ensure-branch.sh - enforce git isolation before editing main/master.
# Ported from spec-superflow ensure-branch.mjs (MIT) + legacy git-state resilience.
# Usage: bash openspec/ensure-branch.sh [change-name] [--force]
set -euo pipefail
change_name="${1:-}"; force=0
for a in "$@"; do [[ "$a" == "--force" ]] && force=1; done

# --- legacy resilience: degrade instead of hard-fail on non-standard states ---
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
  echo "ensure-branch: not a git repo - no isolation possible, proceed with caution" >&2; exit 0; }
branch="$(git branch --show-current 2>/dev/null || true)"
if [[ -z "$branch" ]]; then
  echo "ensure-branch: detached HEAD. WARN: switch to a branch before editing (git switch -c <change-name>). Proceeding without isolation." >&2; exit 0; fi

case "$branch" in main|master) ;; *)
  echo "ensure-branch: already on branch '$branch'. Proceed." >&2; exit 0 ;; esac

echo "ensure-branch: on protected branch '$branch'. Creating an isolated context..." >&2
repo_name="$(basename "$PWD")"; name="${change_name:-$repo_name}"; wt_path="../${repo_name}-${name}"
if git worktree add "$wt_path" -b "$name" 2>/dev/null; then
  echo "ensure-branch: created worktree at $wt_path on branch '$name'. Edit there." >&2; exit 0; fi
echo "ensure-branch: worktree failed, trying local branch..." >&2
if git switch -c "$name" 2>/dev/null; then
  echo "ensure-branch: created branch '$name'. Edit there." >&2; exit 0; fi
if [[ $force -eq 1 ]]; then
  echo "ensure-branch: WARNING - editing protected branch in place with --force." >&2; exit 0; fi
echo "ensure-branch: could not isolate and no --force. STOP and ask the user." >&2; exit 1

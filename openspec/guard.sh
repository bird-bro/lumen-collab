#!/usr/bin/env bash
# guard.sh - Phase transition guard for LoopForge workflow
# Usage: bash guard.sh [--json] <from-phase> <to-phase> <change-dir>
# Exit 0 = all checks pass, 1 = blocked
# --json: output {"pass":true/false,"failures":[...]} on stdout (for CI/programmatic use)
set -euo pipefail

_json=0
_failures=()
_args=()
for _a in "$@"; do
  [[ "$_a" == "--json" ]] && _json=1 || _args+=("$_a")
done
set -- "${_args[@]}"

from="${1:-}"
to="${2:-}"
dir="${3:-}"

[[ -n "$from" && -n "$to" && -n "$dir" ]] || {
  echo "Usage: guard.sh [--json] <from-phase> <to-phase> <change-dir>" >&2
  exit 2
}

# Resolve openspec/ directory
abs_dir="$(cd "$dir" 2>/dev/null && pwd)"
openspec_dir=""
cur="$abs_dir"
while [[ "$cur" != "/" ]]; do
  if [[ -d "$cur/openspec" ]]; then
    openspec_dir="$cur/openspec"
    break
  fi
  cur="$(dirname "$cur")"
done
[[ -z "$openspec_dir" ]] && openspec_dir="$(dirname "$abs_dir")"

if [[ $_json -eq 1 ]]; then
  fail() { _failures+=("$1"); }
else
  fail() { echo "  [BLOCKED] $1" >&2; exit 1; }
fi

check_artifacts_exist() {
  [[ -f "$dir/proposal.md" ]] || fail "proposal.md missing"
  { [[ -f "$dir/spec.md" ]] || [[ -d "$dir/specs" ]]; } || fail "spec.md or specs/ missing"
}

check_schema_valid() {
  local vpy="$openspec_dir/validate-artifacts.py"
  if [[ -f "$vpy" ]] && command -v python3 >/dev/null 2>&1; then
    if [[ $_json -eq 1 ]]; then
      python3 "$vpy" "$dir" >/dev/null 2>&1 || fail "artifact validation failed"
    else
      python3 "$vpy" "$dir" 2>&1 || fail "artifact validation failed"
    fi
    return 0
  fi
  # Bash fallback (no python3): lightweight required-field check
  [[ $_json -eq 0 ]] && echo "  [guard] schema fallback (no python3) - basic field check" >&2
  local _has_intent=0
  if [[ -f "$dir/proposal.md" ]]; then
    grep -qiE '## (Intent|Problem|In scope|Out of scope)' "$dir/proposal.md" && _has_intent=1
  fi
  [[ $_has_intent -eq 1 ]] || fail "proposal.md missing intent/problem/scope section"
  # Check spec has at least one WHEN/THEN scenario
  if [[ -d "$dir/specs" ]]; then
    grep -qrE '## Scenario:|WHEN|THEN' "$dir/specs/" 2>/dev/null || fail "specs/ has no WHEN/THEN scenarios"
  elif [[ -f "$dir/spec.md" ]]; then
    grep -qE '## Scenario:|WHEN|THEN' "$dir"/specs/*/spec.md 2>/dev/null || fail "specs/*/spec.md has no WHEN/THEN scenarios"
  fi
}

check_abandon_safe() {
  # Abandon is allowed if debt.md has entries OR no tasks were started yet
  local debt="$dir/debt.md"
  if [[ -f "$debt" ]] && grep -qE '\[DEBT\]' "$debt" 2>/dev/null; then
    [[ $_json -eq 0 ]] && echo "  [guard] abandon OK - debt.md has entries"
    return 0
  fi
  local state="$openspec_dir/loop-state.yaml"
  if [[ -f "$state" ]]; then
    local done
    done=$(grep '^tasks_done:' "$state" 2>/dev/null | awk '{print $2}')
    if [[ "${done:-0}" == "0" ]]; then
      [[ $_json -eq 0 ]] && echo "  [guard] abandon OK - no tasks started yet"
      return 0
    fi
  fi
  fail "cannot abandon: tasks were done but debt.md has no [DEBT] entries - record debt first"
}

check_contract_fresh() {
  local contract="$dir/execution-contract.md"
  [[ -f "$contract" ]] || fail "execution-contract.md missing - generate it first (see propose inject block)"
  local fresh_sh="$openspec_dir/ensure-contract-fresh.sh"
  [[ -f "$fresh_sh" ]] || { [[ $_json -eq 0 ]] && echo "  [SKIP] ensure-contract-fresh.sh not found" >&2; return 0; }
  if [[ $_json -eq 1 ]]; then
    bash "$fresh_sh" "$dir" >/dev/null 2>&1 || fail "execution-contract.md is stale - regenerate it"
  else
    bash "$fresh_sh" "$dir" 2>&1 || fail "execution-contract.md is stale - regenerate it"
  fi
}

check_tasks_complete() {
  local state="$openspec_dir/loop-state.yaml"
  [[ -f "$state" ]] || fail "loop-state.yaml not found"
  local done total
  done=$(grep '^tasks_done:' "$state" | awk '{print $2}')
  total=$(grep '^tasks_total:' "$state" | awk '{print $2}')
  [[ "$done" == "$total" ]] || fail "tasks incomplete: $done/$total"
}

check_verify_pass() {
  local verify="$dir/verify.md"
  [[ -f "$verify" ]] || fail "verify.md missing - run /opsx:verify first"
  grep -q 'overall:.*PASS' "$verify" || fail "verify.md overall is not PASS"
}

key="${from}:${to}"
case "$key" in
  proposing:applying)
    [[ $_json -eq 0 ]] && echo "  [guard] proposing -> applying"
    check_artifacts_exist
    check_schema_valid
    check_contract_fresh
    ;;
  applying:verifying)
    [[ $_json -eq 0 ]] && echo "  [guard] applying -> verifying"
    check_tasks_complete
    check_contract_fresh
    ;;
  verifying:archived)
    [[ $_json -eq 0 ]] && echo "  [guard] verifying -> archived"
    check_verify_pass
    ;;
  *:abandoned)
    [[ $_json -eq 0 ]] && echo "  [guard] $from -> abandoned"
    check_abandon_safe
    ;;
  *)
    [[ $_json -eq 0 ]] && echo "  [guard] $from -> $to (no checks required)"
    ;;
esac

if [[ $_json -eq 1 ]]; then
  if [[ ${#_failures[@]} -eq 0 ]]; then
    printf '{"pass":true,"failures":[]}\n'
    exit 0
  else
    printf '{"pass":false,"failures":['
    _first=1
    for _f in "${_failures[@]}"; do
      [[ $_first -eq 0 ]] && printf ','
      _first=0
      _esc="${_f//\"/\"}"
      printf '"%s"' "$_esc"
    done
    printf ']}\n'
    exit 1
  fi
fi
echo "  [guard] All checks passed."
exit 0

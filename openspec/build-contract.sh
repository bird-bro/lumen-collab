#!/usr/bin/env bash
# build-contract.sh - Auto-generate execution-contract.md from planning artifacts
# Extracts content from: proposal.md, specs/, design.md, tasks.md
# Usage:
#   bash build-contract.sh <change-dir>          # generate (refuse if exists)
#   bash build-contract.sh --force <change-dir>  # overwrite existing
set -euo pipefail

force=0
[[ "${1:-}" == "--force" ]] && { force=1; shift; }
dir="${1:-}"
[[ -n "$dir" ]] || { echo "Usage: build-contract.sh [--force] <change-dir>" >&2; exit 2; }
[[ -d "$dir" ]] || { echo "Error: not a directory: $dir" >&2; exit 2; }

contract="$dir/execution-contract.md"
if [[ -f "$contract" && $force -eq 0 ]]; then
  echo "execution-contract.md already exists. Use --force to overwrite." >&2
  exit 1
fi

# --- helpers ---
# extract_h2 <file> <header_text>: print lines under "## <header>" until next "## "
extract_h2() {
  local file="$1" hdr="$2"
  [[ -f "$file" ]] || return 0
  awk -v h="$hdr" '
    BEGIN { h=tolower(h) }
    { line=tolower($0) }
    line ~ "^## " h "([ \t]|$)" { found=1; next }
    found && /^## / { exit }
    found { print }
  ' "$file" | sed '/^[[:space:]]*$/d'
}

# --- extract from proposal.md ---
change_name=""
if [[ -f "$dir/proposal.md" ]]; then
  change_name=$(grep -m1 '^# Proposal:' "$dir/proposal.md" 2>/dev/null | sed 's/^# Proposal:[[:space:]]*//' || true)
fi
[[ -z "$change_name" ]] && change_name=$(basename "$dir")

why=""
what=""
scope_raw=""
constraints=""
if [[ -f "$dir/proposal.md" ]]; then
  why=$(extract_h2 "$dir/proposal.md" "Why" | head -n 8)
  what=$(extract_h2 "$dir/proposal.md" "What" | head -n 12)
  scope_raw=$(extract_h2 "$dir/proposal.md" "Scope" | head -n 20)
  constraints=$(extract_h2 "$dir/proposal.md" "Constraints" | head -n 15)
fi

# --- compact scenario index: headings + line refs, not full WHEN/THEN text ---
specs_content=""
if [[ -d "$dir/specs" ]]; then
  while IFS= read -r sf; do
    [[ -f "$sf" ]] || continue
    rel="${sf#"$dir"/}"
    idx=$(grep -nE '^### |^#### Scenario:' "$sf" | head -n 30 || true)
    [[ -n "$idx" ]] && specs_content+="- \`$rel\`"$'\n'"$idx"$'\n'
  done < <(find "$dir/specs" -name "spec.md" | sort)
fi
if [[ -z "$specs_content" && -f "$dir/spec.md" ]]; then
  specs_content=$(grep -nE '^### |^#### Scenario:' "$dir/spec.md" | head -n 30 || true)
fi
[[ -z "$specs_content" ]] && specs_content="<!-- AI: no verification scenario headings found in specs/ - add them -->"

# --- extract from design.md ---
design_decisions=""
if [[ -f "$dir/design.md" ]]; then
  design_decisions=$(extract_h2 "$dir/design.md" "Decisions" | head -n 12)
  [[ -z "$design_decisions" ]] && design_decisions=$(extract_h2 "$dir/design.md" "Architecture" | head -n 12)
fi

# --- compact task index: task headings only, not full task bodies ---
tasks_raw="<!-- AI: no tasks.md found - create it first -->"
if [[ -f "$dir/tasks.md" ]]; then
  tasks_raw=$(grep -nE '^[[:space:]]*[-*][[:space:]]+\[[ xX]\][[:space:]]+|^[[:space:]]*[0-9]+[.)][[:space:]]+' "$dir/tasks.md" | head -n 50 || true)
  [[ -z "$tasks_raw" ]] && tasks_raw="<!-- AI: tasks.md exists but no checkbox/numbered task lines found -->"
fi

# --- assemble contract ---
{
  echo "# Execution Contract"
  echo ""
  echo "> Auto-generated compact bridge from planning artifacts."
  echo "> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs."
  echo "> Review AI-marked sections before applying. guard.sh checks contract existence + freshness."
  echo ""
  echo "## Source Map"
  echo ""
  echo "- Proposal: \`proposal.md\`"
  if [[ -d "$dir/specs" ]]; then echo "- Specs: \`specs/*/spec.md\`"; elif [[ -f "$dir/spec.md" ]]; then echo "- Spec: \`spec.md\`"; fi
  [[ -f "$dir/design.md" ]] && echo "- Design: \`design.md\`"
  [[ -f "$dir/tasks.md" ]] && echo "- Tasks: \`tasks.md\`"
  echo ""
  echo "## Intent Lock"
  echo ""
  echo "- **Change name**: $change_name"
  echo "- **Problem to solve**:"
  echo "$why"
  echo ""
  echo "<!-- AI: summarize the above into 1-2 sentences -->"
  echo "- **In scope**:"
  echo "$scope_raw"
  echo "- **Out of scope**:"
  echo "<!-- AI: extract 'Out of scope' items from the scope section above -->"
  echo ""
  echo "## Approved Behavior"
  echo ""
  echo "<!-- AI: review and confirm these verification scenarios -->"
  echo "$specs_content"
  echo ""
  echo "## Design Constraints"
  echo ""
  echo "- **Architecture/Decisions**:"
  [[ -n "$design_decisions" ]] && echo "$design_decisions" || echo "<!-- AI: no design.md found - create it first -->"
  echo "- **Technical constraints**:"
  [[ -n "$constraints" ]] && echo "$constraints" || echo "<!-- AI: fill from proposal Constraints -->"
  echo ""
  echo "## Task Batches"
  echo ""
  echo "<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->"
  echo "$tasks_raw"
  echo ""
  echo "## Test Obligations"
  echo ""
  echo "<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->"
  echo "- **Behaviors requiring failing-test-first**: "
  echo "- **Required edge cases**: "
  echo "- **Regression-sensitive areas**: "
  echo ""
  echo "## Execution Mode"
  echo ""
  echo "- **Mode**: \`Inline\` | \`Batch Inline\` | \`SDD\`"
  echo "- **Selection rationale**: <!-- AI: choose based on task count and complexity -->"
  echo ""
  echo "## Verification Dimensions"
  echo ""
  echo "| Dimension | Status | Findings |"
  echo "|:--|:--|:--|"
  echo "| Completeness | Pending | - |"
  echo "| Correctness | Pending | - |"
  echo "| Coherence | Pending | - |"
  echo ""
  echo "**Overall**: Pending"
  echo ""
  echo "## Review Gates"
  echo ""
  echo "- **Mandatory review points**: "
  echo "- **Blocking categories**: "
  echo ""
  echo "## Escalation Rules"
  echo ""
  echo "- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially"
  echo "- **When NOT to continue**: contract intent lock no longer matches proposal scope"
} > "$contract"

echo "Generated: $contract"
echo "Next: review AI-marked sections, then run: bash openspec/ensure-contract-fresh.sh --update $dir"

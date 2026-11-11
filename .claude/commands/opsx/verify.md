---
name: "OPSX: Verify"
description: Three-layer verification (L1 build / L2 spec alignment / L3 tests) for a change - writes verify.md as the archive credential
category: Workflow
tags: [workflow, verification, experimental]
---

Three-layer verification for an OpenSpec change. Confirms the implementation actually satisfies the spec's WHEN/THEN scenarios, then writes a `verify.md` credential into the change directory that `/opsx:archive` checks before archiving.

**The three layers**

| Layer | What it checks | How |
|:--|:--|:--|
| **L1 Build** | Code compiles / builds | Runs each affected stack's build command |
| **L2 Spec alignment** | Code implements the spec's WHEN/THEN scenarios | Reviewer-style audit: per scenario, find code evidence, score IMPLEMENTED / PARTIAL / NOT_IMPLEMENTED |
| **L3 Tests** | Existing test suite passes | Runs each affected stack's test command (SKIP if none) |

**Overall status** (written into `verify.md`, read by `/opsx:archive`)

| Overall | Rule | Archive |
|:--|:--|:--|
| `PASS` | L1 + L3 pass, every scenario IMPLEMENTED | ✅ direct |
| `PASS_WITH_ISSUES` | L1 + L3 pass, ≥1 scenario PARTIAL | ⚠ confirm first |
| `FAIL` | any L1 build failed / any L3 test failed / any scenario NOT_IMPLEMENTED | ❌ fix first |
| `NOT_RUN` | no verify.md yet | ⚠ archive warns |

---

**Input**: Optionally specify a change name (e.g., `/opsx:verify add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes and use the **AskUserQuestion tool** to let the user select

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx:verify <other>`).

2. **Resolve change context**

   ```bash
   openspec status --change "<name>" --json
   ```

   Parse the JSON for:
   - `changeRoot` - where to write `verify.md`
   - `artifactPaths.specs.existingOutputPaths` - delta spec files holding the WHEN/THEN scenarios (the L2 source of truth)
   - `root.path` (or `actionContext.allowedEditRoots[0]`) - project root, to locate `openspec/verify.config.yaml` (see step 4) and per-stack `CLAUDE.md`
   - `actionContext.allowedEditRoots` / stack hints - which stacks are affected

   **Scope guard:** `actionContext.mode` is `repo-local` for normal changes (the `workspace-planning` mode was removed from OpenSpec). If a change spans multiple stacks, identify all affected stacks from `allowedEditRoots`, the change's `design.md`, or `tasks.md` stack tags - do not run builds against stacks the change does not touch.

3. **Validate artifacts first (fail fast)**

   ```bash
   openspec validate "<name>" --json
   ```

   If validation reports malformed specs/tasks, STOP and report - there is no point verifying against a broken spec. Suggest `/opsx:propose` to fix the spec.

4. **Load the verify config (build & test commands per stack)**

   Resolution order (use the first that yields commands):
   a. **`openspec/verify.config.yaml`** (project-level, authoritative). Schema:
      ```yaml
      stacks:
        <stack-id>:
          dir: <stack-dir>        # relative to project root
          build: <build-cmd>      # L1
          test: <test-cmd> | null # L3; null or omitted => SKIP
      ```
   b. **Infer from per-stack `CLAUDE.md`** - read each affected stack's `CLAUDE.md`, extract commands from its `## Build Commands` (→ L1) and `## Test` (→ L3) sections.
   c. **Ask once and persist** - use the **AskUserQuestion tool** to ask for the build (and optional test) command per affected stack, then write the answers to `openspec/verify.config.yaml` so future runs are deterministic.

   Identify **affected stacks** from: status JSON `allowedEditRoots`, the change's `design.md`, or `tasks.md` stack tags. If only one stack exists, use it.

5. **L1 - Build check (per affected stack)**

   For each affected stack, run its build command **inside that stack's `dir`** and keep raw output out of the model context:
   ```bash
   mkdir -p <changeRoot>/logs
   cd "<stack-dir>" && <build-cmd> > <changeRoot>/logs/build-<stack-id>.log 2>&1; echo "exit=$?"
   ```
   - On PASS, report only the command, exit code, and log path.
   - On FAIL, inspect with `tail -n 50` / `grep -iE 'error|failed|exception'`; quote only the key lines, not the whole log.
   - Record `L1 = PASS` (exit 0) or `L1 = FAIL` (non-zero).
   - If a stack has no build command, record `L1 = SKIP`.

6. **L2 - Spec alignment (reviewer-style, agent-driven)**

   This is the core layer. It is NOT a CLI call - it is a semantic audit you perform.

   a. **Read every delta spec** from `artifactPaths.specs.existingOutputPaths`. Extract each `## Scenario:` block and its WHEN/THEN clauses. These scenarios ARE the verification cases.
      - For MODIFIED-heavy changes, run `openspec show <name> --diff` first: the delta restates every kept scenario verbatim, so the full delta hides what actually changed - `--diff` isolates the real per-requirement edits (ADDED in full, REMOVED/RENAMED as authored). Also use it to recover a REMOVED requirement's original text when checking "the behavior is actually gone".
   b. **For each scenario**, search the affected stacks' code for implementation evidence:
      - Use `rg` to find the code path(s) the scenario describes (e.g., a route handler, a component, a service method).
      - Read the matched code to confirm it actually fulfills the WHEN/THEN.
      - **Honor `verify-meta`**: if the scenario carries an HTML comment such as
        `<!-- verify-meta: { "endpoint": "POST /api/repair", "expectedStatus": 201 } -->`,
        use the structured fields for a precise check (does that exact route exist? does it return that status?). Absent `verify-meta`, fall back to semantic matching.
      - **Cross-domain rule**: a backend scenario is checked against backend code only; a frontend scenario against frontend code only. Never cite a frontend file as evidence for a backend scenario, and vice versa.
   c. **Score each scenario**:
      - `✓ IMPLEMENTED` - clear, working evidence found → cite `file:line`
      - `⚠ PARTIAL` - partial / fragile / missing edge case → cite what's there and what's missing
      - `✗ NOT_IMPLEMENTED` - no evidence found → state what the spec requires vs. what exists
   d. Tally: `N/M scenarios implemented`.

7. **L3 - Test execution (per affected stack)**

   For each affected stack with a non-null test command, run it inside that stack's `dir` and keep raw output out of the model context:
   ```bash
   cd "<stack-dir>" && <test-cmd> > <changeRoot>/logs/test-<stack-id>.log 2>&1; echo "exit=$?"
   ```
   - Prefer the smallest affected test set during task-level apply; this verify step may run the configured suite.
   - On PASS, report pass/fail counts (if available), exit code, and log path.
   - On FAIL, inspect with `tail -n 50` / `grep -iE 'FAIL|ERROR|AssertionError|failed'`; name only failing tests and quote key error lines.
   - Stacks with `test: null` record `L3 = SKIP` (e.g., "only stack-a has Vitest; other stacks report SKIP").

8. **Compute overall status**

   Apply in order (first match wins):
   - `FAIL` if any L1 = FAIL **or** any L3 = FAIL **or** any scenario = NOT_IMPLEMENTED
   - `PASS_WITH_ISSUES` if L1 + L3 all pass/SKIP **and** ≥1 scenario = PARTIAL
   - `PASS` if L1 + L3 all pass/SKIP **and** every scenario = IMPLEMENTED
   - (NOT_RUN is only the pre-verify state; once you run, the result is one of the above three.)

9. **Write `verify.md` to `<changeRoot>/verify.md`**

   This file is the archive credential. Use the template in `openspec/verify-result.template.md`. Its YAML frontmatter MUST be machine-readable because `/opsx:archive` parses `overall` from it:
   ```yaml
   ---
   verify:
     change: <name>
     date: YYYY-MM-DD
     overall: PASS | PASS_WITH_ISSUES | FAIL
     stacks:
       <stack-id>:
         L1: PASS | FAIL | SKIP
         L2: "<implemented>/<total>"
         L3: PASS | FAIL | SKIP
   ---
   ```
   Followed by the human-readable tables (per-stack L1/L2/L3 + scenario alignment detail + issues).

10. **Display the report and suggest the next action**

    Match the report format below. Then:
    - `FAIL` → list blocking issues, suggest `/opsx:apply <name>` to fix
    - `PASS_WITH_ISSUES` → list PARTIALs, ask "Fix now?" → if yes, hand off to `/opsx:apply`
    - `PASS` → suggest `/opsx:archive <name>`

**Output (report)**

Keep this report compact: no raw logs, no full source excerpts, and cite evidence as `file:line`.

```
## Verification: <change-name>

### <stack-id>
| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | <build-cmd>; exit=0; logs/build-<stack-id>.log |
| L2 spec | 5/6 | 1 PARTIAL |
| L3 tests | PASS | <test-cmd>; exit=0; logs/test-<stack-id>.log |

### Scenario alignment
| Scenario | Status | Evidence |
|:---|:---|:---|
| Submit repair request | IMPLEMENTED | repair.vue:42-58 |
| Refresh list after submit | PARTIAL | missing refresh call |

Overall: PASS_WITH_ISSUES
Fix the PARTIAL now?
```

**Guardrails**
- `verify.md` is the single source of truth for verification status - always (over)write it at the end, even on FAIL, so the record is honest.
- Never mark a scenario IMPLEMENTED without a concrete `file:line` evidence citation.
- Never run a build/test command outside its stack's `dir`.
- Never let backend evidence satisfy a frontend scenario or vice versa (cross-domain prohibition).
- L2 is judgment work - if you cannot find evidence, score NOT_IMPLEMENTED and say so; do not guess.
- If `verify.config.yaml` is missing, create it from the first run so subsequent runs are deterministic (do not re-ask).
- Do not modify any source code during verify - verification is read/execute only. Fixes belong to `/opsx:apply`.

**Fluid Workflow Integration**
- Re-runnable any number of times; each run overwrites `verify.md`.
- After a `/opsx:apply` fix, re-run `/opsx:verify` to refresh the credential before archiving.

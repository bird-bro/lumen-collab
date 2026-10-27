---
name: "OPSX: Apply"
description: "Implement tasks from an OpenSpec change (Experimental)"
allowed-tools: Bash(openspec:*)
category: "Workflow"
tags: ["workflow", "artifacts", "experimental"]
---

<!-- LoopForge: superpowers-trigger-apply -->
## Superpowers Integration (auto-triggered - TDD discipline + hard gates)

**Fast-path mode detection (runs before preflight):**
Read `tasks.md` in the change directory. Count tasks and assess scope:
- **tweak** (<=4 tasks, ALL config/doc/.env/.yml/.json/.md files, no source code): skip contract (0a) + TDD (2). Edit directly. L1 build check (4) still applies. Verify = L1 only.
- **hotfix** (<=2 tasks, <=2 source files, no new schema/API/module): contract relaxed (minimal intent lock OK, skip full contract if <change>/execution-contract.md absent). TDD (2) applies, characterization allowed. Full verify.
- **full** (anything larger): normal flow - all steps apply.
If unsure, default to `full`. Persist: set `workflow_mode: <tweak|hotfix|full>` in `loop-state.yaml`. State the mode before proceeding.

0. **Preflight (mandatory, before ANY implementation edit):**
   a. **Contract freshness:** run `bash openspec/ensure-contract-fresh.sh <change-dir>`. Non-zero exit -> STOP (spec changed since contract was generated; re-run propose to regenerate the contract and update the hash).
   b. **Worktree isolation:** run `bash openspec/ensure-branch.sh <change-name>`. Non-zero exit -> STOP, do NOT edit main/master in place; ask the user (re-run with `--force` only after they agree). (Legacy repos: the script warns + proceeds on detached/non-repo states - it will not block you there, but prefer creating a branch.)
1. **Activate `executing-plans`** before coding - tasks in order, smallest scope first.
2. **TDD Iron Law (legacy-aware) - no production code without a failing OR characterization test first.**
   Read the affected stack `test` field in `openspec/verify.config.yaml`:
   - **If `test` is set (test runner exists):** strict RED -> GREEN -> REFACTOR.
     Write a failing test for the task WHEN/THEN; run it; confirm FAIL (`✓ Red 确认`).
     If it passes unexpectedly -> test is wrong or behavior exists; STOP, investigate.
     Then write minimum code; confirm GREEN (`✓ Green`); refactor; keep green.
     Red flags (all = STOP, write test first): "先快速实现回头补测试" / "跳过测试手动验证" /
     "我知道它能跑" / "就这一次不写测试".
   - **If `test` is null (legacy module, no runner):** characterization mode - do ONE of:
     (a) write a characterization test locking CURRENT behavior (run it; snapshot baseline,
         pass or fail either is the record); OR
     (b) append to `openspec/changes/<change>/debt.md`: `- [DEBT] <task>: no harness for
         <stack>; changed <what>; risk <low|med|high>` and mark the task `DEBT` in tasks.md.
     NEVER silently change legacy code with neither a test nor a debt record.
2b. **Retry cap (3-strike) -> debugging side-path:** if a task build/test FAILs, attempt a fix and increment `retry_count` in `openspec/loop-state.yaml`. If `retry_count >= 3` for the SAME task -> enter **debugging phase** (do NOT keep blind-retrying):
   - Update `loop-state.yaml`: set `phase: debugging`, increment `debug_attempts`.
   - **Debug protocol (4 steps):**
     (a) **Reproduce**: write a minimal reproducer (test or script) that reliably triggers the failure. If you cannot reproduce it, you cannot fix it - say so.
     (b) **Root cause**: use `git bisect` or binary-search the change to find what introduced the issue. Read the failing code path end-to-end. State the root cause in one sentence.
     (c) **Minimal fix**: fix ONLY the root cause - no refactoring, no new features. Smallest change that makes the reproducer pass.
     (d) **Regression guard**: add a test covering the root cause scenario (or append to debt.md if legacy). Run full suite to confirm no regressions.
   - After debugging: set `phase: applying`, reset `retry_count: 0`, reset `debug_attempts: 0`. If debugging reveals spec/contract is wrong -> rewind to `proposing` (see 2c).
   - If `debug_attempts >= 2` for the SAME task -> STOP, escalate to user: architecture or spec assumption is likely wrong.
   Reset `retry_count` to 0 when moving to the next task.
2c. **Mandatory Rewind:** if new scope appears, interfaces change materially, or design assumptions fail during implementation -> rewind to `proposing`, update the spec, then resume. Never patch the spec inside `applying` via ad-hoc chat. Update `loop-state.yaml`: set `phase: proposing`.
2d. **SDD Execution (when execution_mode: SDD):** for each task batch:
   - Dispatch an implementer subagent using `openspec/sdd/implementer-prompt.md` template
     (CC: Task tool; Codex: role-switch to Implementer). Fill placeholders: [task name],
     [BRIEF_FILE], [directory], [REPORT_FILE], [MODEL].
   - **Model selection** (choose per role, always specify explicitly):
     | Role | Model tier | When to use |
     |:--|:--|:--|
     | Implementer (mechanical) | cheap/fast | Single-file CRUD, config, boilerplate, formatting |
     | Implementer (integration) | standard | Multi-file, API wiring, cross-module, state changes |
     | Implementer (architecture) | capable | New module, schema design, refactor, algorithmic |
     | Reviewer | match implementer or +1 | Review diff against spec WHEN/THEN |
     | Final review | most capable | Cross-batch coherence, spec drift, architecture check |
     If unsure, default to standard. For legacy characterization tests, mechanical suffices.
   - On DONE: dispatch a reviewer subagent using `openspec/sdd/reviewer-prompt.md`.
   - Update `openspec/sdd/progress.md` after each task (status, commits, review verdict).
   - On BLOCKED/NEEDS_CONTEXT: escalate to user; do not auto-retry (retry cap 2b applies).
   - For Batch Inline mode: implement tasks sequentially in-context (no subagent dispatch).
   - For Inline mode: implement directly (no subagent, no batch).
3. After each task code change: **`code-review`** the diff against the spec WHEN/THEN.
4. **Per-task build check (L1 quick verify):** after the code change, before marking the task done, run the affected stack build command from `openspec/verify.config.yaml` inside that stack dir. On PASS print `✓ 构建检查通过`; on FAIL do NOT mark done - pause and report. (Reuses the same config as /opsx:verify - one source of truth.)
5. On full completion: **`verification-before-completion`**, then suggest `/opsx:verify <change>` (not archive directly).
6. **Phase gate (apply -> verify - programmatic):** run `bash openspec/guard.sh applying verifying <change-dir>`. Non-zero exit -> STOP. This checks: tasks complete + contract fresh. On PASS, update `loop-state.yaml`: set `phase: verifying`.
<!-- /LoopForge: superpowers-trigger-apply -->

Implement tasks from an OpenSpec change.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Project check:** These steps expect a project that already uses OpenSpec. Before the first step that writes anything (`new change`, `archive`, `sync specs`, or authoring an artifact file), confirm the project has a root: run `openspec list --json` (with `--store <id>` when a store is selected, since the store is then the root) and read `root`. A root object means the project is set up. `"root": null` means it is not - there is no `openspec/` directory here, and a write such as `openspec new change` would create one as a side effect. The command also exits non-zero, which is that answer rather than a broken CLI, so read the JSON instead of retrying or working around it.

One `"root": null` is not about setup: when a `status` error message starts with `Declared in` or `Invalid store declaration in` and names this project's `openspec/config.yaml` (or `config.yml`), the project does use OpenSpec through a store it declares, which this machine cannot resolve (the store is not registered, or the `store:` line is malformed). Do not treat it as uninitialized and skip the branches below: stop before writing and show the user that error's `message` and `fix`.

Otherwise, with no root, what happens next depends on how this workflow was reached:

- **Auto-selected**: you chose this workflow yourself, without the user naming OpenSpec, naming this skill, or running its slash command. Stop using OpenSpec and answer the request normally, as you would with no OpenSpec installed. Do not ask them to set anything up and do not mention OpenSpec setup.
- **Explicit OpenSpec request**: the user named OpenSpec, named this skill, or ran its slash command. Stop before writing and ask how to proceed: set this project up (`openspec init`), target a store they already have (`--store <id>`), or continue without OpenSpec for this request. Wait for their answer.

In both branches, never create the root as a side effect: do not run `openspec init` until the user asks for it, do not hand-create `openspec/` files, and do not let a command create it.

**Input**: Optionally specify a change name (e.g., `/opsx:apply add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, run `openspec list --json` to get available changes and ask the user to select one

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx:apply <other>`).

2. **Check status to understand the schema**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to understand:
   - `schemaName`: The workflow being used (e.g., "spec-driven")
   - `planningHome`, `changeRoot`, and `actionContext`: planning scope and edit constraints
   - Which artifact contains the tasks (typically "tasks" for spec-driven, check status for others)

3. **Get apply instructions**

   ```bash
   openspec instructions apply --change "<name>" --json
   ```

   This returns:
   - `contextFiles`: artifact ID -> array of concrete file paths (varies by schema - could be proposal/specs/design/tasks or spec/tests/implementation/docs)
   - Progress (total, complete, remaining)
   - Task list with status, source path, and source line
   - Dynamic instruction based on current state
   - Optional `context`: current required project instruction input from the selected root
   - Optional `operationGuidance`: current advisory guidance for apply
   - `missingArtifacts` (when present): required artifact ids with no output

   **Handle states:**
   - If `state: "blocked"`: show the message and pause implementation.
     - If `missingArtifacts` is non-empty: suggest completing the missing artifacts. Run `openspec status --change "<name>" --json`, select the next `ready` artifact (not `skipped` or `blocked`), and use `openspec instructions "<artifact-id>" --change "<name>" --json` for its rules and template. Keep the selected `--store <id>` on both commands.
     - Otherwise, follow the CLI instruction to create or repair the schema-configured tracking file from existing planning artifacts. Do not assume another artifact is ready or start implementation while blocked.
   - If `state: "all_done"`: report that all tracked tasks are complete and suggest review or verification as appropriate before archiving
   - Otherwise: proceed to implementation

   Treat `context` as a required prompt-level input. Read and consider it, and
   apply relevant project facts, conventions, and constraints while implementing.
   Treat `operationGuidance` as optional additive advice. Read and consider every
   entry, and follow entries that are applicable and compatible with the built-in
   workflow.

   Keep both fields separate from CLI-returned state, missing artifacts, tasks,
   progress, `contextFiles`, and the built-in `instruction`. They are not
   evidence of task completion, do not replace the built-in instruction, and do
   not permit bypassing a blocked state. If context conflicts with the built-in
   instruction, an explicit user choice, or a CLI-controlled value, report the
   conflict and preserve the controlling value. If guidance is inapplicable or
   conflicts with those controlling inputs, do not follow it and explain why.
   These are prompt-level behavior contracts, not enforceable checks.

4. **Read context files**

   Read every file path listed under `contextFiles` from the apply instructions output.
   The files depend on the schema being used:
   - **spec-driven**: proposal, specs, design, tasks
   - Other schemas: follow the contextFiles from CLI output

   Do not copy `context` or `operationGuidance` verbatim into implementation
   files or planning artifacts unless the user separately asks for that content.

5. **Show current progress**

   Display:
   - Schema being used
   - Progress: "N/M tasks complete"
   - Remaining tasks overview
   - Dynamic instruction from CLI

6. **Implement tasks (loop until done or blocked)**

   For each pending task:
   - Show which task is being worked on
   - Make the code changes required
   - Keep changes minimal and focused
   - Before editing, confirm the checkbox at the returned `sourcePath` and `line` still matches the task description; if it does not, rerun the apply instructions and use the refreshed location
   - Mark the task complete at its returned `sourcePath` and `line`: `- [ ]` → `- [x]`
   - Rerun the apply instructions and confirm that task is now done and progress changed
   - Continue to next task

   **Pause if:**
   - Task is unclear → ask for clarification
   - Implementation reveals a design issue → suggest updating artifacts
   - A task needs work beyond what the spec and tasks describe, or you are tempted to drop, narrow, defer, or accept exceptions to specified behavior to make it fit → surface the added scope and ask; do not absorb it silently
   - Error or blocker encountered → report and wait for guidance
   - User interrupts

7. **On completion or pause, show status**

   Display:
   - Tasks completed this session
   - Overall progress: "N/M tasks complete"
   - If all done: report that tracked tasks are complete and suggest review or verification as appropriate before archiving
   - If paused: explain why and wait for guidance

**Output During Implementation**

```
## Implementing: <change-name> (schema: <schema-name>)

Working on task 3/7: <task description>
[...implementation happening...]
✓ Task complete

Working on task 4/7: <task description>
[...implementation happening...]
✓ Task complete
```

**Output On Completion**

```
## Implementation Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 7/7 tasks complete ✓

### Completed This Session
- [x] Task 1
- [x] Task 2
...

All tracked tasks are complete. Review or verify the change as appropriate
before archiving. You can archive this change with `/opsx:archive`.
```

**Output On Pause (Issue Encountered)**

```
## Implementation Paused

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 4/7 tasks complete

### Issue Encountered
<description of the issue>

**Options:**
1. <option 1>
2. <option 2>
3. Other approach

What would you like to do?
```

**Guardrails**
- Keep going through tasks until done or blocked
- Always read context files before starting (from the apply instructions output)
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update task checkbox immediately after completing each task
- Pause on errors, blockers, or unclear requirements - don't guess
- When a task needs work beyond what the spec describes, surface the added scope and pause - never silently narrow, defer, or simplify away specified behavior
- Only mark a task `- [x]` when its specified behavior is fully implemented, not when it is partially done or deferred
- Use contextFiles from CLI output, don't assume specific file names
- Use each task's sourcePath and line to update its exact checkbox
- Do not use context or operation guidance as proof that a task is complete
- Apply relevant project context; report conflicts with controlling workflow inputs
- Consider every guidance entry; explain any inapplicable or conflicting advice
- Do not copy runtime context or operation guidance into implementation files or planning artifacts
- Preserve CLI-controlled blocked/ready/all-done behavior and completion criteria

**Fluid Workflow Integration**

This skill supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts - not phase-locked, work fluidly

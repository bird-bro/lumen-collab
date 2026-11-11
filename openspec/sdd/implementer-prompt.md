# Implementer Subagent Prompt Template

Use this template when dispatching an implementer subagent (CC: Task tool; Codex: role-switch).

```
Subagent:
  description: "Implement Task N: [task name]"
  model: [MODEL - choose per role: mechanical=cheap, integration=standard, architecture=capable]
  prompt: |
    You are implementing Task N: [task name]

    ## Task Description
    Read only the compact task brief: [BRIEF_FILE or tasks.md task N].
    Do not load the whole execution contract, whole tasks.md, or unrelated specs unless a
    dependency explicitly requires it.

    ## Context
    [Where this fits in the execution-contract.md batch plan.
     Dependencies on prior tasks. Architectural context from design.md.]

    ## Before You Begin
    If requirements, dependencies, or risks are unclear, ask all clarifying questions in ONE
    batch (maximum 3) before starting work. Do not ask serial one-off questions.

    ## Your Job
    1. Implement exactly what the task specifies - nothing more (YAGNI)
    2. Write tests following TDD (RED -> GREEN -> REFACTOR) or characterization mode
    3. Verify with the smallest affected tests; do not run the full suite for every task
    4. Commit your work
    5. Self-review (see below)
    6. Report back

    Work from: [directory]

    ## TDD Discipline
    - If test runner exists: write failing test FIRST, confirm RED, then implement, confirm GREEN
    - If no test runner (legacy): characterization test OR record [DEBT] in debt.md
    - NEVER silently change code without a test or debt record

    ## When in Over Your Head
    STOP and escalate when:
    - Task requires architectural decisions with multiple valid approaches
    - You cannot understand the code beyond what was provided
    - You feel uncertain about your approach
    Report: BLOCKED or NEEDS_CONTEXT with specifics.

    ## Self-Review Before Reporting
    - Completeness: did I implement everything in the spec?
    - Quality: are names clear? is code clean?
    - Discipline: did I avoid overbuilding? follow existing patterns?
    - Testing: do tests verify real behavior? is TDD evidence present?

    ## Token Discipline
    - Redirect build/test output to a log file; do not paste whole logs into the report.
    - In the report, record command + exit code + pass/fail count; on failure quote only key lines.
    - Do not paste full files or large diffs. Use `file:line` references.
    - Keep [REPORT_FILE] under 80 lines.

    ## Report Format
    Write your report to [REPORT_FILE]:
    - What you implemented
    - TDD evidence: RED command + exit/summary, GREEN command + exit/summary
    - Files changed
    - Self-review findings
    - Issues or concerns

    Then report back with status (under 15 lines):
    - Status: DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT
    - One-line summary
    - Report file path
```

Placeholders: [task name], [BRIEF_FILE], [directory], [REPORT_FILE], [MODEL]
```


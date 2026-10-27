# Implementer Subagent Prompt Template

Use this template when dispatching an implementer subagent (CC: Task tool; Codex: role-switch).

```
Subagent:
  description: "Implement Task N: [task name]"
  model: [MODEL - choose per role: mechanical=cheap, integration=standard, architecture=capable]
  prompt: |
    You are implementing Task N: [task name]

    ## Task Description
    Read your task brief first: [BRIEF_FILE or tasks.md task N]
    It contains the full task text from the plan.

    ## Context
    [Where this fits in the execution-contract.md batch plan.
     Dependencies on prior tasks. Architectural context from design.md.]

    ## Before You Begin
    If you have questions about requirements, approach, dependencies, or anything unclear:
    Ask them now. Raise concerns before starting work.

    ## Your Job
    1. Implement exactly what the task specifies - nothing more (YAGNI)
    2. Write tests following TDD (RED -> GREEN -> REFACTOR) or characterization mode
    3. Verify implementation works (run tests)
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

    ## Report Format
    Write your report to [REPORT_FILE]:
    - What you implemented
    - TDD Evidence: RED (command + failing output) + GREEN (command + passing output)
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


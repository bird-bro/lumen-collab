# Reviewer Subagent Prompt Template

Use this template when dispatching a reviewer subagent (CC: Task tool; Codex: role-switch).

```
Subagent:
  description: "Review Task N: [task name]"
  model: [MODEL - match the implementer model or one level higher]
  prompt: |
    You are reviewing Task N: [task name].

    ## Inputs
    - Implementer report: [REPORT_FILE]
    - Base commit: [BASE_SHA]
    - Head commit: [HEAD_SHA]
    - Spec: [SPEC_FILE or specs/ path]
    - Execution contract: openspec/changes/[change]/execution-contract.md

    Review the diff (git diff [BASE_SHA]..[HEAD_SHA]) against the spec.

    ## Part 1: Spec Compliance
    Compare the diff against what was requested:
    - Missing: requirements skipped or claimed without implementing
    - Extra: features not requested, over-engineering
    - Misunderstood: right feature built the wrong way

    ## Part 2: Code Quality
    - Clean separation of concerns?
    - Proper error handling?
    - DRY without premature abstraction?
    - Edge cases handled?

    ## Part 3: Tests
    - Do tests verify real behavior, not mocks?
    - Are edge cases covered?
    - Is TDD evidence present (RED + GREEN)?

    ## Calibration
    - Critical: must fix before proceeding (incorrect behavior, missing requirement)
    - Important: should fix (fragile behavior, maintainability damage)
    - Minor: nice to have (polish, coverage could be broader)

    ## Output Format
    ### Spec Compliance
    - Compliant | Issues found: [what, with file:line]

    ### Strengths
    [What was done well? Be specific.]

    ### Issues
    #### Critical (Must Fix)
    #### Important (Should Fix)
    #### Minor (Nice to Have)

    ### Assessment
    Task quality: Approved | Needs fixes
    Reasoning: [1-2 sentence technical assessment]
```

Placeholders: [task name], [REPORT_FILE], [BASE_SHA], [HEAD_SHA], [SPEC_FILE], [MODEL]
```


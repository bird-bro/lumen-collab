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

    Review only the affected diff:
    1. `git diff --stat [BASE_SHA]..[HEAD_SHA]`
    2. `git diff [BASE_SHA]..[HEAD_SHA] -- <affected paths>`
    Do not read full unrelated files or paste full diffs. Cite `file:line`.

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
    Keep the review under 30 lines. Do not include empty headings or generic praise.

    Spec compliance: Compliant | Issues found
    Issues:
    - Critical: [what, with file:line]
    - Important: [what, with file:line]
    - Minor: [what, with file:line]
    Assessment: Approved | Needs fixes - [1 sentence]
```

Placeholders: [task name], [REPORT_FILE], [BASE_SHA], [HEAD_SHA], [SPEC_FILE], [MODEL]
```


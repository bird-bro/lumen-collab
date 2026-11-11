<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: CHANGE_NAME
  date: YYYY-MM-DD
  overall: PASS_WITH_ISSUES   # PASS | PASS_WITH_ISSUES | FAIL
  stacks:
    STACK_ID:
      L1: PASS                 # PASS | FAIL | SKIP
      L2: "5/6"                # implemented/total scenarios
      L3: PASS                 # PASS | FAIL | SKIP
---

# Verification: CHANGE_NAME

Overall: **PASS_WITH_ISSUES**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| STACK_ID | PASS | 5/6 | PASS | logs/build-STACK_ID.log |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Example scenario | IMPLEMENTED | file:line |
| Example edge case | PARTIAL | missing validation |

## Issues

- PARTIAL: "Example edge case" - expected X; found Y - fix in file:line.

## Next

- PASS -> archive CHANGE_NAME.
- PASS_WITH_ISSUES -> fix or obtain explicit user confirmation before archive.
- FAIL -> return to apply, fix blockers, then re-run verify.

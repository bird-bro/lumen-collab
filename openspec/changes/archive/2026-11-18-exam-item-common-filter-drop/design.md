# Design — exam-item-common-filter-drop

## Decisions

- **D1 (coordination `exam-item-polish.md`)**: removal scope is the PAGE QUERY only.
  `ExamItem.isCommon`, both write DTOs, the entity column + index, and the drawer
  switch all survive; the web change keeps the switch and relocates it.
- **No test churn**: no backend test ever exercised the page-filter isCommon branch
  (AuthExamItemApiTests:170 is a create payload — stays; SettingSchemaSeedTests:49
  pins the schema column list — stays). The existing suite doubles as the regression
  guard for the removal.
- **Javadoc hygiene**: the removal prose touches three javadoc sites (service
  interface getPage, impl class-level, controller page method) + the DTO class
  javadoc — all reworded in the same pass so no stale 常用项目 filter mention
  survives a grep.

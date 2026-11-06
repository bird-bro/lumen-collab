# Design: org-management-redesign-web

## Context

The current organization page renders `OrgTree` inside `ConsolePanel`, with local hooks and
mutation re-query. It has only a name form and treats every node alike. The department page
reuses the same tree and lists departments for any selected org. The backend change converts
this into a controlled hospital/campus model and adds a paginated department query.

## Goals / Non-Goals

**Goals:**

- Keep the established console shell and API-client seam.
- Make organization management focus on hospital profile and campus operations.
- Move the hospital/campus tree to department management as a retrieval filter.
- Keep one organization page and one department page rather than inventing separate routes.
- Scale department maintenance with a table, filters, and server-side pagination.
- Surface backend validation directly, especially structural and duplicate-code errors.
- Avoid creating a root organization from normal hospital administration.

**Non-Goals:**

- No group-of-hospitals UI.
- No external partner organization UI.
- No organization logo upload service; logo URL is entered or left empty.
- No drag-and-drop ordering.
- No department nodes in the left department filter tree.
- No user-campus authorization UI; that belongs to a later permission change.

## Decisions

### D1 - Hospital card plus campus table

The organization page uses a dictionary-like layout. The left side is a stable hospital
profile card and edit action. The right side is a campus table with search/status filtering
and actions for create/edit/default/delete. This avoids mixing all campus data into a tree
and makes the page scale like other dictionary/console lists.

### D2 - Controlled root behavior

The root-creation button is hidden in the normal on-premise UI. A campus can be created only
from the organization page. There is no parent selector for organization create/edit; the
backend also rejects reparenting.

### D3 - Type-aware forms

The hospital form and campus form share profile controls but differ in title, context, and
default-campus handling. Type is display-only in edit mode; clients do not send a type
change. Province/city/district can reuse the city reference picker already used elsewhere.

### D4 - Department tree is a filter, not a CRUD surface

The department page's left tree contains the hospital and campuses only. Selecting the
hospital means "all campuses" and issues the page query without `orgId`; selecting a campus
issues the query with that campus. The tree never renders departments and is not the place
to create/edit/delete departments.

### D5 - Department table and form

The right department table uses server-side pagination and keyword/status filters. When the
hospital root is selected, it shows all campuses and displays the campus column. The
create/edit dialog always requires a live, enabled campus owner. If the filter is on a
campus, the dialog prefills that campus; if the filter is on the hospital, the user selects
the campus. The form allows changing the campus owner but not the department code.

### D6 - Permission gates remain page-level

`system:org:list` gates organization mutations and `system:dep:list` gates department
mutations. No new permission string is invented in this change.

### D7 - First-run completion uses normal editing

Fresh deployments start from the backend's editable `未命名医院` root and `总院区` default
campus. The organization page shows a first-run completion hint for bootstrap placeholders and
uses the existing hospital edit form to collect real details. It does not introduce a
root-creation button or a separate generic initialization permission.

## UX shape

### Organization management

```text
机构管理
┌──────────────┬────────────────────────────────────────────┐
│ 医院信息卡片  │ 院区列表                                    │
│              │ [搜索] [状态] [新增院区]                     │
│ 名称：...     │ ┌────────────────────────────────────────┐ │
│ 代码：...     │ │ 院区 | 代码 | 默认 | 状态 | 操作         │ │
│ 状态：...     │ │ ...  | ...  | ...  | ...  | 编辑/删除  │ │
│ 地址：...     │ └────────────────────────────────────────┘ │
│ [编辑医院]    │                                            │
└──────────────┴────────────────────────────────────────────┘
```

### Department management

```text
科室管理
┌──────────────┬────────────────────────────────────────────┐
│ 医院/院区树   │ 科室列表                                    │
│ ● 全部院区    │ [关键字] [状态] [新增科室]                   │
│ ├ 总院区      │ ┌────────────────────────────────────────┐ │
│ ├ 东院区      │ │ 院区 | 科室 | 代码 | 状态 | 操作         │ │
│ └ 浦东分院    │ │ ...  | ...  | ...  | ...  | 编辑/删除  │ │
│              │ └────────────────────────────────────────┘ │
└──────────────┴────────────────────────────────────────────┘
```

## Risks

- Users may expect to edit departments in the left tree. → The tree is labeled as a campus
  filter, while all department actions live in the right table.
- Users may expect arbitrary organization nesting. → The organization page labels hospital
  profile and campus list explicitly and explains that departments belong to campuses.
- Existing tests assert generic root creation and an unpaginated department list. → Tests are
  rewritten around controlled hospital/campus behavior and the page endpoint.
- Same department codes on different campuses may surprise operators. → Cross-campus rows
  show campus names and duplicate messages identify the campus scope.

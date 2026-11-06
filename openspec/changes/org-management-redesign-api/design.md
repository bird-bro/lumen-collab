# Design: org-management-redesign-api

## Context

The current `org_info` table is a generic `up_id` hierarchy. `type` is present but not used
to constrain the tree; `up_id = 0` is documented as a multi-root affordance; departments in
`org_dep` can reference any live org node; and department code is globally unique among live
rows. The department list returns an unfiltered array. The on-premise deployment needs a
controlled hospital/campus model plus a scalable department table.

## Goals / Non-Goals

**Goals:**

- Preserve existing API paths and frontend compatibility where practical.
- Make hospital/campus/department relationships explicit and enforceable.
- Keep existing department ids and codes stable during migration.
- Provide enough profile fields for real hospital and campus records.
- Support large department counts with server-side filtering and pagination.
- Use A-class business errors for invalid structure, invalid owners, and duplicate codes.

**Non-Goals:**

- No group-of-hospitals hierarchy in this change.
- No external/partner organization registry.
- No user-to-campus authorization scopes; that is a later authorization change.
- No PACS/HL7/MWL integration configuration in this change.
- No file-upload service for organization logos; `logoUrl` is a nullable string.
- No department-tree modeling inside `org_info`.

## Decisions

### D1 - Controlled hospital/campus tree

For on-premise v1, `type = 0` means hospital and `type = 1` means campus. At most one live
hospital root is allowed. A hospital may contain campus children; a campus is an
`org_info` leaf. This gives later workflows a stable campus context without creating a new
top-level tenant table.

### D2 - First-run root is seeded, not created by normal administration

Fresh deployments use Option B: a deterministic first-run hospital root (`未命名医院`) and
default campus (`总院区`) are seeded with editable profile details. Administrators complete
the profile through the existing update endpoint; no deployment-time hospital profile
configuration is required. For recovery only, `POST /system/orgs` with `upId = 0` remains
permitted when no live hospital root exists. Once a root exists, further root creation is
rejected.

### D3 - Organization type and parent are controlled

Organization type is immutable after create. Reparenting is rejected in v1. `level` is
derived from the parent, not trusted from the client. This prevents the tree from becoming
an arbitrary registry while retaining the existing request fields for compatibility.

### D4 - Departments anchor to campuses and remain a separate resource

Create and update of a department must reference a live, enabled campus. A hospital root is
not a valid owner. This allows `放射科` to exist on several campuses as distinct operating
departments. Department rows remain in `org_dep`; they are not inserted into `org_info`.

### D5 - Department code is immutable and campus-scoped

A department code is created once and cannot be changed later. Uniqueness moves from global
live-code uniqueness to per-campus live-code uniqueness. When a department is reassigned to
another campus, the target campus is validated and the same code is checked against that
campus.

### D6 - Department table uses an additive page endpoint

Existing `GET /system/org-deps/list` stays unchanged for compatible callers. The new
`GET /system/org-deps/page` supports `pageNum`, `pageSize`, optional `orgId`, `keyword`,
and `status`; deterministic ordering is by department id. An absent `orgId` represents
"all campuses" for the hospital-root selection in the UI. A present `orgId` must be a
campus.

### D7 - Deletes are guarded

Organization deletion is refused if the node has live child organizations or departments.
Department deletion remains a soft delete. This prevents silent orphaning and preserves
medical referential context at the system boundary.

### D8 - Migration keeps the current tree usable

The seeded hospital remains the hospital root. The original product seed is normalized to
the first-run placeholder details; custom hospital names are preserved. A default campus is
created for each existing hospital root, and departments attached to the root move to that
campus. Existing department ids and codes do not change, and migrated departments are
enabled. Organization codes are backfilled deterministically when absent.

### D9 - Additive compatibility

The response envelope, endpoint paths, permission gates, and audit-field behavior remain
unchanged. Existing clients that only use `id`, `upId`, `level`, `type`, and `name` continue
to work; new clients can use the richer profile fields and department page query.

## Risks

- Existing integrations may assume department code is globally unique. → Preserve existing
  codes and keep the endpoint contract; document that uniqueness is now campus-scoped.
- Existing UI code may allow selecting a hospital node as a department owner. → The backend
  rejects non-campus owners and the web change restricts ownership selection to campus nodes.
- A large unfiltered department list may still be used by old clients. → The existing list
  stays for compatibility, but the console uses the paginated endpoint.
- Profile fields may encourage premature group modeling. → Group mode, partner organizations,
  and multi-hospital hierarchy are explicitly out of scope.

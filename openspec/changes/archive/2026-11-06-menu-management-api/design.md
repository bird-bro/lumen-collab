# Design: menu-management-api

## Context

`MenuController` currently serves only `GET /tree` (class-gated `system:menu:list`),
backed by the concrete `MenuTreeService` (tree assembly + `permsSet`). The entity is
`MenuInfo` (`menu_info`: upId tree, `type` M/C/F, `perms`, `path`, `component`, `icon`,
`sort`, `visible` + `EntityBase` audit columns with `@TableLogic` soft delete); grants live
in `role_menu` (`RoleMenu` mapper). Console-scale data (tens of nodes) and the shared
design in `openspec/coordination/menu-management.md` fix the contract; see proposal.md for
the why.

## Goals / Non-Goals

**Goals:**
- Additive CRUD family on the existing controller/service seam, matching the established
  manage-service patterns (`OrgServiceImpl`/`DictDetailManageServiceImpl` semantics,
  `BusinessException(ExceptionEnum.ERROR_A0400, msg)` rejections).
- Tree-integrity guards cheap enough to be obviously correct at console scale.

**Non-Goals:**
- No schema/migration change, no workstation fields, no perms validation or uniqueness,
  no drag-order API (`sort` is a plain field), no grant backfill on UPDATE.

## Decisions

- **D1 - New concrete `MenuManageService`** beside `MenuTreeService` (same package,
  concrete class like `MenuTreeService`; no interface - single-impl family, and the tree
  service already set that precedent). CRUD logic must not blur into the tree assembler;
  `MenuTreeService` stays untouched (read seam for tests unchanged).
- **D2 - Guard computation is in-memory.** One `selectList` of live nodes feeds an
  id→node map + children index; the cycle guard walks the descendant closure of the
  candidate parent; F-leaf and parent-existence guards read the same map. Alternatives:
  recursive SQL CTE (overkill at console scale) or per-level queries (N round-trips).
  The map load also serves create-parent validation and delete child detection with zero
  extra queries.
- **D3 - Auto-grant is a guarded insert, transactional with the node insert.** Create =
  `@Transactional { insert menu; insert role_menu(1, id) with NOT EXISTS live-binding
  guard }` - the guard keeps re-running a create that somehow already has the binding
  idempotent (mirrors the V5 seed discipline). Delete =
  `@Transactional { deleteById (renders UPDATE deleted=1 via @TableLogic);
  roleMenuMapper.delete(by menuId) (same UPDATE shape) }` - the exact cascade shape of
  `RoleServiceImpl.delete`.
- **D4 - Rejections are `BusinessException(ExceptionEnum.ERROR_A0400, <msg>)`** for all
  structural guards (F-parent, cycle, unknown parent, live children on delete). No new
  error-code enums: there is no unique-constraint or state-conflict scenario that would
  warrant an A07xx domain code, and the errors spec keeps A0400 as the
  request-parameter/business-rejection class.
- **D5 - DTOs follow the role family**: `CreateMenuRequest` and `UpdateMenuRequest` as
  separate `@Valid` DTOs (`title` `@NotBlank`; `type` `@Pattern("M|C|F")`; `upId`
  `@NotNull`; others optional), `BeanUtil.copyProperties(request, entity, true)` into the
  entity (house idiom; fields match 1:1). Type flips (M↔C↔F) stay allowed - a plain
  field update; the F-parent guard (not type immutability) protects leaf semantics.
- **D6 - Controller grows by new handler methods only** (standing "never modify existing
  public methods" rule): `POST`/`PUT /{id}`/`DELETE /{id}` join the existing class;
  the class-level `@PreAuthorize` already covers them.

## Risks / Trade-offs

- [In-memory guard map goes stale under concurrent edits] → window is one request; last
  writer wins is the accepted console semantics (same as org/dict manage services). The
  DB has no tree FK to violate, so worst case is a silently orphaned node that TreeUtils
  stops rendering - recoverable by re-parenting.
- [Auto-grant makes create a two-row write] → single service transaction; the NOT EXISTS
  guard + role 1 being non-deletable in practice keeps it benign.
- [Type flips can turn a `C` with button children into an `M` or an `F`] → flipping to
  `F` with live children would violate leaf semantics via the back door; the update path
  re-runs the leaf guard when `type = F` and the node has live children (reject), and the
  frontend labels types explicitly so a flip is always a deliberate form action.

## Migration Plan

Pure additive backend: new DTOs, new service, new controller methods. Deploy = rebuild
`lumen-system`; no migration, no data backfill. Rollback = redeploy previous artifact
(unused endpoints vanish; no data shape changed). Spec sync (`api`, `auth-rbac`) happens
at `/opsx:archive` per the standing flow.

## Open Questions

(none - the four scope rulings are recorded in the coordination doc)

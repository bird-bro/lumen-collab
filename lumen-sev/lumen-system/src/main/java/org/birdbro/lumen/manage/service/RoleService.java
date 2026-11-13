package org.birdbro.lumen.manage.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.manage.dto.AssignRoleMenusRequest;
import org.birdbro.lumen.manage.dto.CreateRoleRequest;
import org.birdbro.lumen.manage.dto.RoleItemDto;
import org.birdbro.lumen.manage.dto.RoleListQuery;
import org.birdbro.lumen.manage.dto.UpdateRoleRequest;

/**
 * Role management (auth-rbac-foundation task 4.4): CRUD on {@code role} plus the
 * role-menu assignment endpoint. The assignment is FULL-REPLACE semantics (the role's
 * existing {@code role_menu} rows are logically deleted, then the given set inserted),
 * and the logical role delete CASCADES to the role's {@code user_role}/{@code
 * role_menu} rows (recorded decision - the perms walk does not join {@code role}, so
 * live bindings to a dead role would keep granting).
 *
 * @author birdbro
 */
public interface RoleService {

    /**
     * Creates a role; a duplicate live name is rejected with {@code A0714} (the partial
     * unique index stays the backstop).
     *
     * @param request name + optional type/remark
     * @return the database-generated id of the new row
     */
    Integer create(CreateRoleRequest request);

    /**
     * Partial update (rename / type / remark); an unknown target id is rejected with
     * {@code B0031}, a duplicate live name with {@code A0714}.
     *
     * @param id      the role to update
     * @param request the fields to change
     * @return {@code Boolean.TRUE} (envelope {@code "200"}; a void return would fall
     *         into the copied advice's null branch and render the {@code "204"} code)
     */
    Boolean update(Integer id, UpdateRoleRequest request);

    /**
     * Logical delete of the role PLUS its bindings: the role's {@code user_role} and
     * {@code role_menu} rows are logically deleted in the same transaction (recorded
     * cascade decision).
     *
     * @param id the role to delete
     * @return {@code Boolean.TRUE} (see the update note on the envelope code)
     */
    Boolean delete(Integer id);

    /**
     * FULL-REPLACE role-menu assignment: validates the role and every menu id exist
     * ({@code A0400} otherwise), logically deletes the role's existing
     * {@code role_menu} rows, and inserts one row per given id. An empty list clears
     * all grants.
     *
     * @param roleId the role to (re)grant
     * @param menuIds the complete menu-id set the role should own
     * @return {@code Boolean.TRUE} (see the update note on the envelope code)
     */
    Boolean assignMenus(Integer roleId, AssignRoleMenusRequest request);

    /**
     * Paged role list (role-list-api): name-substring and type-equality filters with
     * the copied PageSizeConfig pagination (defaults 1/20), rows ascending by id, each
     * carrying the {@code menuIds} grant echo - the role's live {@code role_menu} menu
     * ids assembled in one batched query for the page (an empty list for an ungranted
     * role).
     *
     * @param query pagination plus optional name/type filters
     * @return the IPage of role rows with the grant echo
     */
    IPage<RoleItemDto> page(RoleListQuery query);

    /**
     * Enable toggle (role-type-dict-api D7, the org-status-toggle mirror): persists
     * {@code 1}/{@code 0} on {@code role_info.enable} and returns true. An unknown
     * role id is rejected {@code A0400}; the strict 0/1 body check lives on
     * {@code SetRoleEnableRequest}'s bean validation. Store/display/toggle ONLY -
     * no behavioral wiring (no perm recalc / login rejection) in this change.
     *
     * @param roleId the live role id
     * @param enable 1 to enable, 0 to disable
     * @return always true (the standard Boolean envelope payload)
     */
    Boolean setEnable(Integer roleId, Integer enable);

    /**
     * The menu-create auto-grant (role-list-api D4 - moved from the MenuManageService
     * 寄住 form per the 2026-11-06 ruling): inserts the new node into the admin role's
     * grant (role 1) when no live binding exists yet (NOT-EXISTS guard, the V5 seed
     * discipline - keeps a re-run idempotent). Called inside the menu write's
     * transaction; all {@code role_menu} writes live in the role family.
     *
     * @param menuId the newly created menu node
     */
    void grantNewMenuToAdmin(Integer menuId);

    /**
     * Menu-delete binding cleanup (role-list-api D4): logically deletes the menu's
     * {@code role_menu} rows so a deleted node stops granting its perms (the same
     * shape as the role-delete cascade). Called inside the menu write's transaction.
     *
     * @param menuId the menu node being deleted
     */
    void unbindMenu(Integer menuId);
}

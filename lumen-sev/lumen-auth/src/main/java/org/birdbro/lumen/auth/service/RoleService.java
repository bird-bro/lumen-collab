package org.birdbro.lumen.auth.service;

import org.birdbro.lumen.auth.dto.AssignRoleMenusRequest;
import org.birdbro.lumen.auth.dto.CreateRoleRequest;
import org.birdbro.lumen.auth.dto.UpdateRoleRequest;

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
}

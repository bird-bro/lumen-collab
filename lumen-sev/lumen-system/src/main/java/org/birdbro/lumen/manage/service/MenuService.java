package org.birdbro.lumen.manage.service;

import org.birdbro.lumen.manage.dto.CreateMenuRequest;
import org.birdbro.lumen.manage.dto.UpdateMenuRequest;

/**
 * Menu node lifecycle management (menu-management-api; renamed from the former
 * {@code MenuManageService} 寄住 form by role-list-api per the 2026-11-06 ruling):
 * create / update / delete behind the {@code /api/v1/system/menus} family, beside
 * (never inside) the read-side
 * {@link org.birdbro.lumen.manage.service.support.MenuTreeService}. Tree integrity is
 * SERVICE-level - {@code menu_info} has no
 * self-FK, so every structural guard reads one live-node map per request (console-scale
 * data; the same map serves parent validation, the cycle walk and delete's child check
 * with zero extra queries).
 *
 * <p>The {@code role_menu} binding writes this class used to host (the create-time
 * admin auto-grant and the delete-time binding cleanup) now live in their true domain -
 * {@code RoleService#grantNewMenuToAdmin} / {@code RoleService#unbindMenu} (user ruling
 * 2026-11-06: 授权/绑定本质是角色域逻辑; role-list-api design D4). This service calls
 * them inside its own transactions, so the write stays atomic exactly as before.
 * Recorded decisions (menu-management-api design.md D1-D5):</p>
 * <ol>
 *   <li><b>Auto-grant on create</b> - lumen keeps the explicit {@code role_menu} model
 *       (no {@code *:*:*} wildcard), so every new node is granted to the admin role
 *       (role 1) in the same transaction (delegated to the role family). UPDATE never
 *       touches grants.</li>
 *   <li><b>Rejections are {@code A0400}</b> - unknown/non-live parent, button ({@code F})
 *       parent (buttons are leaves), {@code upId} = self or inside the node's own
 *       descendant subtree (cycle), and deleting a node with live children. A type flip
 *       to {@code F} while children are live is rejected as the back-door leaf
 *       violation.</li>
 *   <li><b>Delete = guarded soft delete + binding cleanup</b> - {@code deleteById}
 *       renders {@code UPDATE deleted = 1} via {@code @TableLogic}, and the node's
 *       {@code role_menu} rows are soft-cleaned (delegated to the role family) so a
 *       deleted button stops granting its perms. No cascade: live children block the
 *       delete.</li>
 * </ol>
 *
 * <p>Cycle detection walks UP the {@code upId} chain from the candidate parent: the
 * candidate is inside the node's subtree exactly when the walk reaches the node.</p>
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/MenuServiceImpl} body is unchanged from the former concrete class).</p>
 *
 * @author birdbro
 */
public interface MenuService {

    /**
     * Creates a menu node from the request ({@code BeanUtil.copyProperties} 1:1; the
     * fields match) and auto-grants it to the admin role - both in one transaction.
     *
     * @param request the create body ({@code upId = 0} = top level)
     * @return the new node id ({@code IdType.AUTO} backfill)
     */
    Integer create(CreateMenuRequest request);

    /**
     * Updates a node's full field set, including reparenting via {@code upId}. Guards
     * run before the write: the node must exist live; the new parent must be live and
     * not a button; the move must not target the node itself or its own subtree; a
     * type flip to {@code F} must not orphan children semantics. Grants are untouched.
     *
     * @param id      the node to update
     * @param request the full-field update body
     * @return {@code true} on success
     */
    Boolean update(Integer id, UpdateMenuRequest request);

    /**
     * Deletes a childless node: soft-deletes the row ({@code @TableLogic}) and
     * soft-cleans its {@code role_menu} bindings in one transaction - the same cascade
     * shape as {@code RoleServiceImpl.delete}. Live children block the delete.
     *
     * @param id the node to delete
     * @return {@code true} on success
     */
    Boolean delete(Integer id);
}

package org.birdbro.lumen.auth.service;

import java.util.List;
import java.util.Set;

import org.birdbro.lumen.auth.dto.LoginResponse;
import org.birdbro.lumen.auth.dto.MeResponse;
import org.birdbro.common.core.pojo.user.MenuTree;

/**
 * The login loop (auth-rbac-foundation task 3.1): credential verification against
 * {@code user_info}, JWT issuance through the copied {@code TokenService}, and the fresh
 * profile/menu/perms read for the me endpoint.
 *
 * @author birdbro
 */
public interface AuthService {

    /**
     * Verifies the credentials and issues a JWT. Lookup is by username among live rows
     * ({@code deleted = 0}); unknown accounts map to A0201, disabled accounts to A0202,
     * BCrypt mismatches to A0210 (each as a {@code BusinessException} that the copied
     * {@code GlobalExceptionHandler} renders as the A-class envelope).
     *
     * @param username the login name
     * @param rawPassword the raw password as posted
     * @return token + profile + menu tree + perms set
     */
    LoginResponse login(String username, String rawPassword);

    /**
     * Builds the me response for the JWT-authenticated principal: profile, menus, and
     * perms are re-read from the database (never trusted from the Redis session copy).
     *
     * @return profile + menu tree + perms set of the current user
     */
    MeResponse me();

    /**
     * The DISTINCT permission strings granted to the user through
     * {@code user_role -> role_menu -> menu_info} (live rows only).
     *
     * @param userId the user to collect permissions for
     * @return the non-blank perms strings (empty set when the user owns no menus)
     */
    Set<String> permissionSet(Integer userId);

    /**
     * The menu tree granted to the user through the same joins, assembled by
     * {@code MenuTreeService} (auth-rbac-foundation task 2.3).
     *
     * @param userId the user to assemble the tree for
     * @return the top-level menus with their nested children
     */
    List<MenuTree> menuTree(Integer userId);
}

package org.birdbro.lumen.system.service;

import java.util.List;

import org.birdbro.lumen.system.dto.CreateUserRequest;
import org.birdbro.lumen.system.dto.UserItemDto;

/**
 * User management (auth-rbac-foundation task 4.3, extending the Batch 3 minimal create
 * into the full endpoint set): list (DTO without the password hash), create with the
 * service-level duplicate-username rejection ({@code A0111} - the Batch-3 review
 * handoff; the DB partial unique index stays the backstop), the admin password reset
 * (BCrypt + {@code must_change_password = 1}), and the enable flag.
 *
 * @author birdbro
 */
public interface UserInfoService {

    /**
     * Creates a user: the BCrypt-encoded password replaces the raw one, defaults are
     * {@code must_change_password = 0} / {@code enable = 1}, and the copied
     * {@code MetaObjectConfig} fills {@code create_by} from the bridged USER-HEADER
     * identity during the insert. A username that already exists among LIVE rows is
     * rejected with {@code A0111} (username already exists).
     *
     * @param request username + raw password + display name
     * @return the database-generated id of the new row
     */
    Integer createUser(CreateUserRequest request);

    /**
     * Lists every live user as {@link UserItemDto} items - NEVER the entity, whose
     * {@code password} column must not serialize. Ordered by id for a stable response.
     *
     * @return the live users
     */
    List<UserItemDto> listUsers();

    /**
     * Admin password reset: stores the BCrypt hash of the raw replacement and forces
     * {@code must_change_password = 1}. The old hash is REPLACED, not added to.
     *
     * @param userId      the user to reset
     * @param newPassword the raw replacement password
     * @return {@code Boolean.TRUE} (envelope {@code "200"}; a void return would fall
     *         into the copied advice's null branch and render the {@code "204"} code)
     */
    Boolean resetPassword(Integer userId, String newPassword);

    /**
     * Sets the enable flag ({@code 1 = active}, {@code 0 = disabled}); a disabled user
     * cannot log in (the copied login loop answers {@code A0202}).
     *
     * @param userId the user to flag
     * @param enable 1 or 0 (bean-validated on the request DTO)
     * @return {@code Boolean.TRUE} (see the reset note on the envelope code)
     */
    Boolean setEnable(Integer userId, Integer enable);
}

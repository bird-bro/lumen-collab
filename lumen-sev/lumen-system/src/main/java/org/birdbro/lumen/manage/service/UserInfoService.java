package org.birdbro.lumen.manage.service;

import java.util.List;

import org.birdbro.lumen.manage.dto.CreateUserRequest;
import org.birdbro.lumen.manage.dto.UpdateUserRequest;
import org.birdbro.lumen.manage.dto.UserDetailDto;
import org.birdbro.lumen.manage.dto.UserItemDto;

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

    /**
     * Lists every live user as {@link UserItemDto} items enriched with the profile
     * surface (user-edit-delete-profile-api task 2.1): the V26 profile columns PLUS the
     * batch-resolved {@code roleNames} and {@code depName}. NEVER the entity, whose
     * {@code password} column must not serialize. The role/department names resolve in
     * TWO batch fetches for the whole page (no N+1 per-row mapper calls).
     *
     * @return the live users with their profile payload
     */
    List<UserItemDto> listUsersWithProfile();

    /**
     * Returns one live user's full profile detail (task 2.2) - the edit dialog's echo
     * payload, {@code roleIds} included (the update's full-replace source). The BCrypt
     * hash never leaves the service ({@code UserDetailDto} carries no password
     * property). A dead id answers B0031.
     *
     * @param userId the user to read
     * @return the profile detail
     */
    UserDetailDto getUserDetail(Integer userId);

    /**
     * Partial-updates a user's profile (task 3.2): the present fields apply, the
     * absent ones stay. {@code username} is NOT a field of {@link UpdateUserRequest} -
     * immutable through this path; the password stays on the SM2 reset endpoint. A
     * present {@code roleIds} REPLACES the user's bindings (full-replace, the
     * role-menu assignment precedent) in the same transaction; an absent one leaves
     * them untouched. A {@code depId} that misses a live department or a
     * {@code roleIds} entry missing a live role rejects {@code A0400} and changes
     * NOTHING.
     *
     * @param userId  the user to update
     * @param request the partial profile patch
     * @return {@code Boolean.TRUE} (envelope {@code "200"})
     */
    Boolean updateUser(Integer userId, UpdateUserRequest request);

    /**
     * Logically deletes a user (task 3.3) and unbinds its live {@code user_role} rows,
     * one transaction. The freed username becomes re-creatable (the partial unique
     * index covers live rows). Two protections reject A-class with a descriptive msg
     * (user ruling 2026-11-10): the CALLER'S OWN account (a self-delete) and the seed
     * administrator (id 1 - the only guaranteed bootstrap login).
     *
     * @param userId the user to delete
     * @return {@code Boolean.TRUE} (envelope {@code "200"})
     */
    Boolean deleteUser(Integer userId);
}

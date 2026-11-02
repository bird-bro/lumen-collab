package org.birdbro.lumen.auth.service;

import org.birdbro.lumen.auth.dto.CreateUserRequest;

/**
 * User management (auth-rbac-foundation task 3.3): only the create operation exists at
 * this stage - it is the vehicle that proves {@code MetaObjectConfig} audit filling
 * against the real database. The full CRUD surface (list, duplicate rejection, password
 * change, enable/disable) ships with task 4.3.
 *
 * @author birdbro
 */
public interface UserInfoService {

    /**
     * Creates a user: the BCrypt-encoded password replaces the raw one, defaults are
     * {@code must_change_password = 0} / {@code enable = 1}, and the copied
     * {@code MetaObjectConfig} fills {@code create_by} from the bridged USER-HEADER
     * identity during the insert.
     *
     * @param request username + raw password + display name
     * @return the database-generated id of the new row
     */
    Integer createUser(CreateUserRequest request);
}

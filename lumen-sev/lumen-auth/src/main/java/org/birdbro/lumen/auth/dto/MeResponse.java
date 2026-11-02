package org.birdbro.lumen.auth.dto;

import java.util.List;
import java.util.Set;

import org.birdbro.common.core.pojo.user.MenuTree;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Profile response body ({@code GET /api/v1/auth/me}, auth-rbac-foundation task 3.1).
 * Profile/menu/perms data is re-read from the database on every call (fresh grants) -
 * the Redis copy of the session holds identity, never profile truth. Same envelope
 * semantics as {@link LoginResponse} minus the token.
 *
 * @author birdbro
 */
@Data
public class MeResponse {

    @ApiModelProperty("The authenticated user's profile")
    private ProfileDto profile;

    @ApiModelProperty("Dynamic menu tree (copied MenuTree contract)")
    private List<MenuTree> menus;

    @ApiModelProperty("Button gating permission strings")
    private Set<String> perms;
}

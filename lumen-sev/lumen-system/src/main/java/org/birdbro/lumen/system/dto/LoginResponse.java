package org.birdbro.lumen.system.dto;

import java.util.List;
import java.util.Set;

import org.birdbro.common.core.pojo.user.MenuTree;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Successful login response body ({@code POST /api/v1/system/login}, auth-rbac-foundation
 * task 3.1): the JWT issued by the copied {@code TokenService} plus everything the
 * frontend needs to render the session - profile, dynamic menu tree, and the button
 * gating perms set. The copied {@code GlobalResponseAdvice} wraps this into the standard
 * {@code code:"200"} envelope.
 *
 * @author birdbro
 */
@Data
public class LoginResponse {

    @ApiModelProperty("JWT issued by TokenService (state in Redis)")
    private String token;

    @ApiModelProperty("The authenticated user's profile")
    private ProfileDto profile;

    @ApiModelProperty("Dynamic menu tree (copied MenuTree contract)")
    private List<MenuTree> menus;

    @ApiModelProperty("Button gating permission strings")
    private Set<String> perms;

}

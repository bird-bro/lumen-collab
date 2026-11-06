package org.birdbro.lumen.system.dto;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * The authenticated user's profile - the response shape shared by login and me
 * (auth-rbac-foundation task 3.1). Deliberately NOT the {@code user_info} entity: the
 * BCrypt hash must never serialize (Batch-2 review handoff), so every field is mapped
 * explicitly and no password field exists.
 *
 * @author birdbro
 */
@Data
public class ProfileDto {

    @ApiModelProperty("UID (user_info.id)")
    private Integer uid;

    @ApiModelProperty("Login name")
    private String username;

    @ApiModelProperty("Display name")
    private String name;

    @ApiModelProperty("Enable flag: 1 = active, 0 = disabled")
    private Integer enable;

    @ApiModelProperty("1 = the user must change the password at first login")
    private Integer mustChangePassword;

    @ApiModelProperty("Live role names granted through user_role")
    private List<String> roles;

}

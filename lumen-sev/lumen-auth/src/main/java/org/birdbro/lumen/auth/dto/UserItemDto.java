package org.birdbro.lumen.auth.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * User list item ({@code GET /api/v1/auth/users/list}, auth-rbac-foundation task 4.3).
 * Deliberately NOT the {@code user_info} entity: the entity's {@code password} column
 * holds the BCrypt hash, and a DTO with an explicit field set is the only guarantee it
 * NEVER serializes (task 4.3 binding "no entity leakage", lumen-sev coding standard).
 *
 * @author birdbro
 */
@Data
public class UserItemDto {

    @ApiModelProperty("UID (user_info.id)")
    private Integer uid;

    @ApiModelProperty("Login name")
    private String username;

    @ApiModelProperty("Display name")
    private String name;

    @ApiModelProperty("Enable flag: 1 = active, 0 = disabled")
    private Integer enable;

    @ApiModelProperty("1 = the user must change the password at next login")
    private Integer mustChangePassword;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty("Row creation time")
    private Date createTime;
}

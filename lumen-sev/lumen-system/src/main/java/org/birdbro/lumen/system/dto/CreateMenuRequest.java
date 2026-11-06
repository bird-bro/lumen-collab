package org.birdbro.lumen.system.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-menu request body ({@code POST /api/v1/system/menus}, menu-management-api).
 * Nodes hang off the {@code upId} tree ({@code 0} = top level) as directories
 * ({@code M}), menus ({@code C}) or buttons ({@code F}); {@code perms} is stored
 * verbatim (free text, the {@code 模块:资源:动作} convention) and creation auto-grants
 * the node to the admin role - the explicit {@code role_menu} model has no wildcard,
 * so an ungranted node would be invisible to the super admin. Structural guards
 * (live F-leaf parent, etc.) are service-side.
 *
 * @author birdbro
 */
@Data
public class CreateMenuRequest {

    @NotNull(message = "upId must not be null")
    @ApiModelProperty("Parent menu id: 0 = top level")
    private Integer upId;

    @NotBlank(message = "type must not be blank")
    @Pattern(regexp = "M|C|F", message = "type must be M (directory), C (menu) or F (button)")
    @ApiModelProperty("Node type: M directory / C menu / F button")
    private String type;

    @NotBlank(message = "title must not be blank")
    @ApiModelProperty("Display title")
    private String title;

    @ApiModelProperty("Permission string (e.g. system:user:list); free text, null for pure navigation")
    private String perms;

    @ApiModelProperty("Frontend route path")
    private String path;

    @ApiModelProperty("Frontend component")
    private String component;

    @ApiModelProperty("Icon name")
    private String icon;

    @ApiModelProperty("Sort order among siblings")
    private Integer sort;

    @ApiModelProperty("Visible flag: 1 = visible (default), 0 = hidden")
    private Integer visible;
}

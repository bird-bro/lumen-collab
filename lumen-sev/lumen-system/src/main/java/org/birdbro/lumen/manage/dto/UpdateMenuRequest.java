package org.birdbro.lumen.manage.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-menu request body ({@code PUT /api/v1/system/menus/{id}}, menu-management-api):
 * full-field update including reparenting via {@code upId} (the frontend posts the
 * whole form). Guards are service-side: {@code upId = id} and {@code upId} inside the
 * node's own descendant subtree are rejected (cycle protection), a button ({@code F})
 * parent is rejected, and a type flip to {@code F} with live children is rejected (the
 * back-door leaf violation). Updating never touches {@code role_menu} grants - grants
 * change on CREATE only.
 *
 * @author birdbro
 */
@Data
public class UpdateMenuRequest {

    @NotNull(message = "upId must not be null")
    @ApiModelProperty("Parent menu id: 0 = top level")
    private Integer upId;

    @NotBlank(message = "type must not be blank")
    @Pattern(regexp = "M|C|F|W", message = "type must be M (directory), C (menu), F (button) or W (workstation)")
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

    @ApiModelProperty("Optional free-text remark")
    private String remark;
}

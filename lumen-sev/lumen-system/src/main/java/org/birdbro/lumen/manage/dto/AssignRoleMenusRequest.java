package org.birdbro.lumen.manage.dto;

import java.util.List;

import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Role-menu assignment request body ({@code PUT /api/v1/system/roles/{id}/menus},
 * auth-rbac-foundation task 4.4). The semantics are FULL REPLACE: the role's existing
 * {@code role_menu} rows are logically deleted, then one row per given menu id is
 * inserted - an EMPTY list is therefore a legitimate "clear all grants".
 *
 * @author birdbro
 */
@Data
public class AssignRoleMenusRequest {

    @NotNull(message = "menuIds must not be null (an empty array clears all grants)")
    @ApiModelProperty("The complete menu-id set the role should own (full replace)")
    private List<Integer> menuIds;
}

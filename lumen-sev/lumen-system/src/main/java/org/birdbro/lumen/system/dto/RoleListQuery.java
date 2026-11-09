package org.birdbro.lumen.system.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Role list query ({@code GET /api/v1/system/roles/list}, role-list-api): name substring
 * and type equality filters with the standard pagination (the copied PageSizeConfig
 * defaults 1/20), mirroring the copied {@code OrgDepPageQuery} query-DTO convention.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RoleListQuery extends PageSizeConfig {

    @ApiModelProperty("Name substring filter (LIKE contains); absent means all roles")
    private String name;

    @ApiModelProperty("Role type filter (equality): 10 system / 11 business; absent means all types")
    private Integer type;
}

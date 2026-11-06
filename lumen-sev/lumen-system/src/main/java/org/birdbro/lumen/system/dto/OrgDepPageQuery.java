package org.birdbro.lumen.system.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Department table query ({@code GET /api/v1/system/org-deps/page},
 * org-management-redesign): keyword, status, and campus filters with standard pagination.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class OrgDepPageQuery extends PageSizeConfig {

    @ApiModelProperty("Owning campus org id; absent means all campuses")
    private Integer orgId;

    @ApiModelProperty("Keyword matched against department code or name")
    private String keyword;

    @ApiModelProperty("Lifecycle status: 1 = enabled, 0 = disabled")
    private Integer status;
}

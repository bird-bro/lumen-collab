package org.birdbro.lumen.manage.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Role list row ({@code GET /api/v1/system/roles/list}, role-list-api): the {@code
 * role_info} fields the console's {@code RoleItem} contract consumes, plus the
 * {@code menuIds} grant echo. Deliberately NOT the entity (the {@code UserItemDto}
 * precedent): an explicit field set is the layering guarantee, and the echo is a
 * projection that has no place on {@code RoleInfo}.
 *
 * @author birdbro
 */
@Data
public class RoleItemDto {

    @ApiModelProperty("Role id (role_info.id)")
    private Integer id;

    @ApiModelProperty("Role name, unique among live rows")
    private String name;

    @ApiModelProperty("Role type: 10 system / 11 business")
    private Integer type;

    @ApiModelProperty("Free-text remark")
    private String remark;

    @ApiModelProperty("Lifecycle flag (role-type-dict-api D7): 1 enabled / 0 disabled - display only, no behavioral wiring")
    private Integer enable;

    @ApiModelProperty("Creator user id")
    private Integer createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty("Row creation time")
    private Date createTime;

    @ApiModelProperty("Last modifier user id")
    private Integer updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty("Row update time")
    private Date updateTime;

    @ApiModelProperty("Grant echo: the role's live role_menu menu ids, ascending (empty for an ungranted role)")
    private List<Integer> menuIds;
}

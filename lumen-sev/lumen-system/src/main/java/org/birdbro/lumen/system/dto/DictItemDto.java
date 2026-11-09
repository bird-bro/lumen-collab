package org.birdbro.lumen.system.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Slim dict item projection ({@code GET /api/v1/system/dicts/detail/items/{catalogName}},
 * role-type-dict-api): the label-lookup pair consumers render — the numeric {@code code}
 * plus the V9 single-text {@code value}. Deliberately NOT the {@code DictDetailEntity}:
 * an explicit field set is the layering guarantee (the {@code RoleItemDto} precedent) —
 * tag/flag/color/css/audit columns have no place on a lookup payload.
 *
 * @author birdbro
 */
@Data
public class DictItemDto {

    @ApiModelProperty("Detail numeric code (the value consumers store, e.g. role_info.type)")
    private Integer code;

    @ApiModelProperty("Detail display text (the V9 single-text model)")
    private String value;
}

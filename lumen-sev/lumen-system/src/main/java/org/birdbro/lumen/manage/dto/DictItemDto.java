package org.birdbro.lumen.manage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Slim dict item projection ({@code GET /api/v1/system/dicts/detail/items/{catalogName}},
 * role-type-dict-api): the label-lookup pair consumers render — the numeric {@code code}
 * plus the V9 single-text {@code value}. Deliberately NOT the {@code DictDetailEntity}:
 * an explicit field set is the layering guarantee (the {@code RoleItemDto} precedent).
 *
 * <p>The V9 spare pair's string half ({@code tag}) rides as OPTIONAL display metadata
 * (registration-order-status-dict-sev design D8, user ruling 2026-11-26 — the status
 * COLOR carrier): {@code NON_NULL} keeps untagged catalogs on the exact two-key
 * {@code {code, value}} shape, so the recorded "no spare columns on the payload"
 * stance is amended for this first legitimate consumer only; {@code flag} stays off.</p>
 *
 * @author birdbro
 */
@Data
public class DictItemDto {

    @ApiModelProperty("Detail numeric code (the value consumers store, e.g. role_info.type)")
    private Integer code;

    @ApiModelProperty("Detail display text (the V9 single-text model)")
    private String value;

    @ApiModelProperty("Optional display metadata (the V9 spare string column — the "
            + "1031 登记单状态 color token; absent on untagged catalogs)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tag;
}

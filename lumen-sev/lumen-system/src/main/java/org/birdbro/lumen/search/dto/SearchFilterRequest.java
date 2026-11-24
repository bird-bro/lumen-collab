package org.birdbro.lumen.search.dto;

import java.util.Map;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The saved-search create/update body (us-registration-redesign-api task 5.2, design
 * D3). The uid is NEVER client-bindable (server-side from the session identity - the
 * RepTempServiceImpl ownership precedent); {@code isDefault} rides the dedicated
 * set-default endpoint (full-replace semantics), never the generic update.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchFilterRequest {

    @ApiModelProperty("工作台页区分键")
    private String pageKey;

    @ApiModelProperty("筛选项名称")
    private String name;

    @ApiModelProperty("过滤条件原文（jsonb 透传，形状归各工作台）")
    private Map<String, Object> query;

    @ApiModelProperty("是否页面默认（仅 create 可带；变更走 set-default 端点）")
    private Boolean isDefault;
}

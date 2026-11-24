package org.birdbro.lumen.search.dto;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The saved-search row (us-registration-redesign-api task 5.2): the caller's own
 * filter - id/name/query/isDefault/updateTime (the uid rides the server-side
 * scoping, never the wire).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchFilterView {

    @ApiModelProperty("筛选项 id")
    private Integer id;

    @ApiModelProperty("工作台页区分键")
    private String pageKey;

    @ApiModelProperty("筛选项名称")
    private String name;

    @ApiModelProperty("过滤条件原文（jsonb 透传）")
    private Map<String, Object> query;

    @ApiModelProperty("是否页面默认（每 uid+pageKey 至多一个）")
    private Boolean isDefault;

    @ApiModelProperty("更新时间（列表按它倒序）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}

package org.birdbro.lumen.setting.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Item table query ({@code GET /api/v1/setting/exam-items/page}, exam-setting-api):
 * modality-scoped with the 部位分组 cascade filter (resolved through the body join)
 * and the 名称/编码 keyword. No 常用项目 filter - the flag stays a per-row fact for
 * the drawer switch, never a list filter (exam-item-common-filter-drop, 2026-11-16).
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ItemPageQuery extends PageSizeConfig {

    @ApiModelProperty("检查类型 (dict 1024 value, required)")
    private String modality;

    @ApiModelProperty("部位分组 (optional - filters items whose body belongs to it)")
    private Integer groupId;

    @ApiModelProperty("关键字, matched against item name or code")
    private String keyword;
}

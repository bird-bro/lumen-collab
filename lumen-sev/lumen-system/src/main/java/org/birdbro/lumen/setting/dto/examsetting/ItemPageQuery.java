package org.birdbro.lumen.setting.dto.examsetting;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Item table query ({@code GET /api/v1/setting/exam-items/page}, exam-setting-api):
 * modality-scoped with the 部位分组 cascade filter (resolved through the body join),
 * the 常用项目 toggle, and the 名称/编码 keyword.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ItemPageQuery extends PageSizeConfig {

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    private String modality;

    @ApiModelProperty("部位分组 (optional - filters items whose body belongs to it)")
    private Integer groupId;

    @ApiModelProperty("关键字, matched against item name or code")
    private String keyword;

    @ApiModelProperty("常用项目 (optional - absent lists both)")
    private Boolean isCommon;
}

package org.birdbro.lumen.setting.dto.examsetting;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Method table query ({@code GET /api/v1/setting/exam-methods/page},
 * exam-setting-api): modality-scoped with the console's two query types (名称/编码)
 * collapsed into one keyword matched against both columns.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MethodPageQuery extends PageSizeConfig {

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    private String modality;

    @ApiModelProperty("关键字, matched against method name or code")
    private String keyword;
}

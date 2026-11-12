package org.birdbro.lumen.setting.dto.examsetting;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Item create request ({@code POST /api/v1/setting/exam-items}, exam-setting-api):
 * the full catalog surface INCLUDING the performance-score weights (scoreRep/scoreAud,
 * nullable = excluded from that weighting - user ruling 2026-11-12) and the method
 * selection as an ID ARRAY (the join table replaces RCP's comma string; the rewrite is
 * transactional). The body anchors the item; its modality must match the body's.
 *
 * @author birdbro
 */
@Data
public class ItemCreateRequest {

    @ApiModelProperty("检查类型 (dict 1027 value, must match the body's)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("项目名称 (required)")
    @NotBlank(message = "exam 不能为空")
    private String exam;

    @ApiModelProperty("项目编码 (optional, unique within the modality)")
    private String code;

    @ApiModelProperty("检查部位 (required)")
    @NotNull(message = "bodyId 不能为空")
    private Integer bodyId;

    @ApiModelProperty("检查方法 id 集合 (the exam_item_method join set)")
    private List<Integer> methodIds;

    @ApiModelProperty("单价（元）")
    private BigDecimal price;

    @ApiModelProperty("折扣 (0-1)")
    private BigDecimal rate;

    @ApiModelProperty("诊断计费数量")
    private Integer tag;

    @ApiModelProperty("诊断得分（绩效权重，空 = 不计）")
    private Integer scoreRep;

    @ApiModelProperty("审核得分（绩效权重，空 = 不计）")
    private Integer scoreAud;

    @ApiModelProperty("常用项目")
    private Boolean isCommon;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("启用 (dict 1000 codes, required)")
    @NotNull(message = "enable 不能为空")
    private Integer enable;

    @ApiModelProperty("备注")
    private String remark;
}

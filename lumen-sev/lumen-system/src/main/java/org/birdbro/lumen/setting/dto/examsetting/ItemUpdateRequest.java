package org.birdbro.lumen.setting.dto.examsetting;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Item full-replace update request ({@code PUT /api/v1/setting/exam-items/{id}},
 * exam-setting-api). Same surface as create; D9 null-aware (absent optionals CLEAR),
 * and the {@code methodIds} set REPLACES the binding set inside the item's transaction
 * (delete-by-exam_id + batch insert) - a mid-rewrite failure rolls the whole update
 * back (the AuthExamItemTransactionTests proof).
 *
 * @author birdbro
 */
@Data
public class ItemUpdateRequest {

    @ApiModelProperty("检查类型 (dict 1027 value, must match the body's)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("项目名称 (required)")
    @NotBlank(message = "exam 不能为空")
    private String exam;

    @ApiModelProperty("项目编码 (absent clears)")
    private String code;

    @ApiModelProperty("检查部位 (required)")
    @NotNull(message = "bodyId 不能为空")
    private Integer bodyId;

    @ApiModelProperty("检查方法 id 集合 (replaces the binding set)")
    private List<Integer> methodIds;

    @ApiModelProperty("单价（元，absent clears）")
    private BigDecimal price;

    @ApiModelProperty("折扣 (0-1, absent clears)")
    private BigDecimal rate;

    @ApiModelProperty("诊断计费数量 (absent clears)")
    private Integer tag;

    @ApiModelProperty("诊断得分（绩效权重，absent clears）")
    private Integer scoreRep;

    @ApiModelProperty("审核得分（绩效权重，absent clears）")
    private Integer scoreAud;

    @ApiModelProperty("常用项目 (absent clears)")
    private Boolean isCommon;

    @ApiModelProperty("排序 (absent clears)")
    private Integer sort;

    @ApiModelProperty("启用 (dict 1000 codes, required)")
    @NotNull(message = "enable 不能为空")
    private Integer enable;

    @ApiModelProperty("备注 (absent clears)")
    private String remark;
}

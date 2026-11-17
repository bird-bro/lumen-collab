package org.birdbro.lumen.patient.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Archive list query (patient-domain task 3.1): name substring, pinyin PREFIX
 * (LIKE 'x%', uppercased to match the server-stored uppercase pinyin), exact
 * pat_id, exact id_no, and phone substring - over LIVE UNMERGED archives only
 * (deleted = 0 via {@code @TableLogic}, merged_into IS NULL), ordered id desc.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PatientArchivePageQuery extends PageSizeConfig {

    @ApiModelProperty("姓名包含过滤（LIKE contains）")
    private String name;

    @ApiModelProperty("拼音前缀过滤（LIKE 'x%'）")
    private String pinyin;

    @ApiModelProperty("平台患者号精确过滤")
    private String patId;

    @ApiModelProperty("证件号码精确过滤")
    private String idNo;

    @ApiModelProperty("联系电话包含过滤")
    private String phone;
}

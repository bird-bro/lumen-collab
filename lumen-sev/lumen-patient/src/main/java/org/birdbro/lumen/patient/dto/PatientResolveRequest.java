package org.birdbro.lumen.patient.dto;

import java.util.Date;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Identity resolve request (patient-domain task 3.3): the workstation resolve form.
 * The service evaluates the chain (brief decision 6): exact (id_type, id_no) ->
 * card_no among live identifier rows -> soft (name+phone, name+gender+birth)
 * candidates -> create-new per the {@code provisional} flag. An id_no WITHOUT
 * id_type never exact-matches (the identity anchor is the pair) and, when nothing
 * matches and creation runs, is rejected by the create-flow A0410 guard.
 *
 * @author birdbro
 */
@Data
public class PatientResolveRequest {

    @ApiModelProperty("证件类型（与 idNo 成对出现才参与精确匹配）")
    private String idType;

    @ApiModelProperty("证件号码（精确匹配活档案身份锚点）")
    private String idNo;

    @ApiModelProperty("就诊卡号（patient_identifier id_type=card，软解析键）")
    private String cardNo;

    @ApiModelProperty("姓名（软匹配键之一）")
    private String name;

    @ApiModelProperty("性别 0 未知 / 1 男 / 2 女（软匹配键之一）")
    private Integer gender;

    @ApiModelProperty("出生日期（软匹配键之一）")
    private Date birthDate;

    @ApiModelProperty("联系电话（软匹配键之一）")
    private String phone;

    @ApiModelProperty("无任何匹配时的建档标志：true = PROVISIONAL，缺省/ false = RESOLVED")
    private Boolean provisional;
}

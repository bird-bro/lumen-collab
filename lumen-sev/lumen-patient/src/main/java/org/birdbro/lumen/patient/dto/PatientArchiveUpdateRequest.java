package org.birdbro.lumen.patient.dto;

import java.util.Date;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Archive demographics update request (patient-domain task 3.1). DELIBERATELY NO
 * {@code archive_status} field: the one-way PROVISIONAL -> RESOLVED machine has no
 * v1 endpoint, and a DTO without the field cannot violate it (brief decision 4).
 * A name change regenerates the server pinyin; an identity change re-runs the
 * id_no uniqueness check (excluding the row itself).
 *
 * @author birdbro
 */
@Data
public class PatientArchiveUpdateRequest {

    @ApiModelProperty("证件类型（随证件号变更时必填）")
    private String idType;

    @ApiModelProperty("证件号码（变更时按活档案重查唯一性）")
    private String idNo;

    @ApiModelProperty("姓名（变更时重新生成拼音）")
    private String name;

    @ApiModelProperty("性别 0 未知 / 1 男 / 2 女")
    private Integer gender;

    @ApiModelProperty("出生日期")
    private Date birthDate;

    @ApiModelProperty("联系电话")
    private String phone;

    @ApiModelProperty("民族")
    private String nation;

    @ApiModelProperty("地址")
    private String address;
}

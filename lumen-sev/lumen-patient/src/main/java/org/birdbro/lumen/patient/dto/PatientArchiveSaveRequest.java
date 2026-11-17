package org.birdbro.lumen.patient.dto;

import java.util.Date;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Archive create request (patient-domain task 3.1): demographics + identity document
 * + the additional identifier numbers the request carries + the optional
 * PROVISIONAL flag. The {@code pat_id} number and the pinyin are SERVER-generated
 * (never request fields); {@code archive_status} follows the one-way machine from
 * the flag (true = PROVISIONAL placeholder, otherwise RESOLVED).
 *
 * <p>Guard (b1 review carry-forward): {@code id_type} is REQUIRED whenever
 * {@code id_no} is present - the identity partial unique index does not pin
 * {@code id_type}, so the service check is the only NULL-id_type guard.</p>
 *
 * @author birdbro
 */
@Data
public class PatientArchiveSaveRequest {

    @ApiModelProperty("证件类型（id_no 存在时必填）")
    private String idType;

    @ApiModelProperty("证件号码（可空：无名急挂号可无证件）")
    private String idNo;

    @ApiModelProperty("姓名（可空）")
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

    @ApiModelProperty("就诊卡号（patient_identifier, id_type=card）")
    private String cardNo;

    @ApiModelProperty("体检号（patient_identifier, id_type=physical）")
    private String physicalNo;

    @ApiModelProperty("门诊号（patient_identifier, id_type=outpatient）")
    private String outpatientNo;

    @ApiModelProperty("住院号（patient_identifier, id_type=inpatient）")
    private String inpatientNo;

    @ApiModelProperty("HIS 号（patient_identifier, id_type=his）")
    private String hisNo;

    @ApiModelProperty("占位标志：true = PROVISIONAL 占位档案，缺省/false = RESOLVED")
    private Boolean provisional;
}

package org.birdbro.lumen.core.patient;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The patient-resolution request the kernel hands to the patient-registry port
 * (lumen-core-registration-kernel task 5.1, design D6): the demographics the exam side
 * collected, plus the natural keys the patient domain's resolution chain walks
 * (patient-domain decision #2: id_no &rarr; card_no &rarr; HIS mapping &rarr; soft-match
 * &rarr; new). The kernel never talks HTTP itself - consuming services adapt this to
 * the patient service's resolve/create endpoints.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientRegistrationRequest {

    /** 证件类型 (patient domain's id_type vocabulary). */
    private String idType;

    /** 证件号 (the resolution chain's first key). */
    private String idNo;

    /** 就诊卡号 (the second key; never a hard unique). */
    private String cardNo;

    /** 姓名. */
    private String name;

    /** 性别 0 未知 / 1 男 / 2 女. */
    private Integer gender;

    /** 检查时年龄. */
    private String age;

    /** 联系电话. */
    private String phone;

    /** 患者来源 (门诊/住院/急诊/体检...). */
    private String patientSource;

    /** 住院号. */
    private String admissionNumber;

    /** 床号. */
    private String bedNumber;

    /** 身高 (cm). */
    private BigDecimal height;

    /** 体重 (kg). */
    private BigDecimal weight;
}

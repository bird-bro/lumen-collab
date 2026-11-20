package org.birdbro.lumen.api.patient;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The resolve-or-register request body (us-station-registration-sev task 2.1, design
 * D3/D4): the demographics + natural keys the consuming service's registration adapter
 * hands to the patient domain's resolution chain (patient decision #2: id_no &rarr;
 * card_no &rarr; soft-match &rarr; new) through the internal composite endpoint - the
 * field-for-field contract view of the patient domain's resolve form. {@code id_type}
 * pairs with {@code id_no} for the decisive exact match; {@code provisional} governs
 * the fallback archive's state when nothing matches (true = PROVISIONAL placeholder).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientResolveOrRegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证件类型（与 idNo 成对出现才参与精确匹配）. */
    private String idType;

    /** 证件号码（精确匹配活档案身份锚点）. */
    private String idNo;

    /** 就诊卡号（patient_identifier id_type=card，软解析键）. */
    private String cardNo;

    /** 姓名（软匹配键之一）. */
    private String name;

    /** 性别 0 未知 / 1 男 / 2 女（软匹配键之一）. */
    private Integer gender;

    /** 出生日期（软匹配键之一）. */
    private java.util.Date birthDate;

    /** 联系电话（软匹配键之一）. */
    private String phone;

    /** 无任何匹配时的建档标志：true = PROVISIONAL，缺省/ false = RESOLVED. */
    private Boolean provisional;
}

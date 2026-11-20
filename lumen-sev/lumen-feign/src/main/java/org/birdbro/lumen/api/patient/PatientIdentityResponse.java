package org.birdbro.lumen.api.patient;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The resolved-or-registered patient identity (us-station-registration-sev task 2.1,
 * design D3/D4): the payload the composite endpoint returns for a decisive outcome -
 * {@code patUid} is the order-linkage key the consuming service builds its port result
 * around (NOT NULL on MATCHED/CREATED; a missing identity = an ambiguous result the
 * consumer refuses), {@code patId} is the patient domain's 影像号, {@code state} is the
 * archive state (PROVISIONAL placeholder included), and the remaining fields are the
 * archive display fields the caller needs (order snapshot / registration UI).
 *
 * <p>Deliberately DECOUPLED from the patient domain's entity: the contract carries its
 * own minimal shape of what a consuming service needs - the patient schema stays
 * patient-side.</p>
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientIdentityResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The archive's pat_uid (patient_master.id) - the order linkage key. */
    private Integer patUid;

    /** 影像号 pat_id（patient 域发放，随行展示）. */
    private String patId;

    /** 档案状态（PROVISIONAL 占位 / RESOLVED）. */
    private String state;

    /** 证件类型. */
    private String idType;

    /** 证件号码. */
    private String idNo;

    /** 姓名（无名占位为 NULL）. */
    private String name;

    /** 性别 0 未知 / 1 男 / 2 女. */
    private Integer gender;

    /** 出生日期. */
    private Date birthDate;

    /** 联系电话. */
    private String phone;
}

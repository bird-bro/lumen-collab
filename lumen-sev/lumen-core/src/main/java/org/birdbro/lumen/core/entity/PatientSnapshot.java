package org.birdbro.lumen.core.entity;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * jsonb 患者快照 value object (patient-domain decision #4: exam_order stores the
 * exam-time demographics - 年龄/患者来源/住院号/床号/身高体重 - as jsonb; a
 * patient.changed event refreshes it only while the report is not issued, never
 * silently after). Persisted through {@code KernelJsonbTypeHandler} (Types.OTHER -
 * real jsonb object, never a double-encoded string). Deliberately DECOUPLED from the
 * patient domain's entity: the kernel never imports lumen-patient, so the snapshot is
 * the kernel's own minimal shape of what an exam order needs.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientSnapshot {

    /** 影像号 (pat_id - issued by the patient service, carried for display). */
    private String patId;

    /** 姓名 (emergency placeholder patients carry the 无名氏 placeholder). */
    private String name;

    /** 性别 0 未知 / 1 男 / 2 女 (the patient domain's gender convention). */
    private Integer gender;

    /** 检查时年龄 (display string as captured at exam time). */
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

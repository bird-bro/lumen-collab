package org.birdbro.lumen.us.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.birdbro.common.core.base.EntityBase;

/**
 * The US-specific registration extension row (us-station-registration-sev ledger V2,
 * design D2; specs/us-station "US Registration Extension"): the 1:1 extension of the
 * vendored kernel order - {@code order_id} IS the primary key (the 1:1 anchor, itself
 * the exam_order.id reference; the reference stays LOGICAL, no physical FK, the
 * kernel/patient shape). Written ONLY through the kernel {@code RegistrationHook} seam
 * ({@code UsRegistrationHook} - inside the registration transaction, so an extension
 * failure rolls the whole registration back); read through the us query path. The
 * vendored kernel DDL stays verbatim - the US fields (孕检, user ruling 2026-11-20:
 * lumen decision #5) live HERE and nowhere in the four kernel tables.
 *
 * @author birdbro
 */
@TableName("us_registration_ext")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UsRegistrationExt extends EntityBase {

    /** The 1:1 anchor - exam_order.id itself (NOT database auto-increment). */
    @TableId(value = "order_id", type = IdType.INPUT)
    private Integer orderId;

    /** 妊娠状态 varchar(16) (dict-backed value, optional). */
    private String pregnancyStatus;

    /** 孕周 integer (weeks, optional). */
    private Integer gestationalWeeks;

    /** 备注 varchar(255) (optional). */
    private String note;

    /** 申请科室 varchar(64) - the HIS opening department, free text (us-apply-info, optional). */
    private String applyDept;

    /** 申请医生 varchar(64) - the HIS opening doctor, free text (us-apply-info, optional). */
    private String applyDoctor;

    /** 申请时间 varchar(19) - the raw {@code yyyy-MM-dd HH:mm:ss} HIS stamp, informational (us-apply-info, optional). */
    private String applyTime;

    /** 申请科室 id varchar(64) - the reserved id anchor behind {@code applyDept} (us-apply-info, optional; v1 UI does not send it). */
    private String applyDeptId;

    /** 申请医生 id varchar(64) - the reserved id anchor behind {@code applyDoctor} (us-apply-info, optional; v1 UI does not send it). */
    private String applyDoctorId;
}

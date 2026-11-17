package org.birdbro.lumen.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.birdbro.common.core.base.EntityBase;

/**
 * Number rule ({@code patient_number_rule}, patient-domain task 2.1): the config
 * parameters AND the hot {@code serial_cur} counter for one number family
 * ({@code rule_type}, {@code pat_id} today). {@code stamp} is a JAVA DATE FORMAT
 * pattern (any valid pattern works with zero code, B1 amendment #2);
 * {@code stamp_value} is runtime STATE - the stamp token the current {@code serial_cur}
 * was issued under, NULL until the first issuance (B1 amendment). Issuance increments
 * {@code serial_cur} under {@code SELECT ... FOR UPDATE} (design D1 row lock); exactly
 * one ACTIVE live rule per type is backstopped by
 * {@code uk_patient_number_rule_active} - only inactive rules may delete.
 *
 * @author birdbro
 */
@TableName("patient_number_rule")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PatientNumberRule extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 规则类型（'pat_id' 今天；未来就诊号等同模式复用）. */
    private String ruleType;

    /** 号码前缀. */
    private String prefix;

    /** 号码后缀. */
    private String suffix;

    /** 流水位数长（左补零）. */
    private Integer serialLength;

    /** 当前流水号（发号热计数器，行锁自增）. */
    private Integer serialCur;

    /** 是否按日期戳周期重置 0 否 / 1 是. */
    private Integer isStamp;

    /** 日期戳格式（Java 日期模式，v1 种子 yyMMdd）. */
    private String stamp;

    /** 当前流水号对应的日期戳令牌（首次发号前为 NULL，运行态）. */
    private String stampValue;

    /** 启用 1 / 停用 0（同类型唯一启用行由部分唯一索引兜底）. */
    private Integer active;
}

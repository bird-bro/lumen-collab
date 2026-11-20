package org.birdbro.lumen.manage.entity;

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
 * Platform number-rule ({@code number_rule}, number-rule-unification-sev task 2.1,
 * design D1): the config parameters AND the hot {@code serial_cur} counter for ONE
 * number family - the platform carries EVERY family here (影像号 / 登记号 /
 * 预约号, future families follow - the dict 编号类型 1028 vocabulary) in the
 * SYSTEM schema, replacing the per-domain duplicates. Issuance increments
 * {@code serial_cur} under {@code SELECT ... FOR UPDATE} (design D3 row lock); exactly
 * one ACTIVE live rule per family × modality scope is backstopped by
 * {@code uk_number_rule_active} (the COALESCE expression form, V49) - only inactive
 * rules may delete.
 *
 * @author birdbro
 */
@TableName("number_rule")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NumberRule extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 规则类型（dict 编号类型 1028 的条目值，即号码族机器 token / 未来族）. */
    private String ruleType;

    /** 适用范围模态（dict 模态 1024 的条目值 token，如 CT/DR/US；NULL=全局共用池，V49）. */
    private String modality;

    /** 备注 (optional operator note, V46 - the V38 exam-method remark precedent). */
    private String remark;

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

    /** 日期戳格式（Java 日期模式，种子 yyMMdd）. */
    private String stamp;

    /** 当前流水号对应的日期戳令牌（首次发号前为 NULL，运行态）. */
    private String stampValue;

    /** 启用 1 / 停用 0（同族同范围唯一启用行由部分唯一索引兜底）. */
    private Integer active;
}

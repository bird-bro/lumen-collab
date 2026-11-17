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
 * Additional patient identifiers ({@code patient_identifier}, patient-domain task 2.1):
 * MANY rows per master - visit card / physical / outpatient / inpatient / HIS numbers.
 * Deliberately NO unique index (design D4): the natural identity document lives on
 * {@code patient_master} under its partial unique index; additional numbers resolve
 * lookups by {@code id_no} only.
 *
 * @author birdbro
 */
@TableName("patient_identifier")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PatientIdentifier extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 所属主索引 id（patient_master.id，服务级引用，无物理外键）. */
    private Integer patientId;

    /** 号码类型（就诊卡/体检/门诊/住院/HIS...）. */
    private String idType;

    /** 号码值（card_no 解析链按此查询）. */
    private String idNo;
}

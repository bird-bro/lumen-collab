package org.birdbro.lumen.patient.entity;

import java.util.Date;

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
 * Patient master index ({@code patient_master}, patient-domain task 2.1): the EMPI
 * root - every row carries a platform {@code pat_id} number (PROVISIONAL placeholders
 * included). {@code id_type/id_no} are NULLable: an unnamed emergency registration may
 * carry no identity document yet; when present they anchor the identity through the
 * PARTIAL unique index {@code uk_patient_master_identity} (live unmerged rows only -
 * merged masters drop out without touching identifier rows, design D4).
 * {@code archive_status} is a service-enforced one-way machine (PROVISIONAL to
 * RESOLVED); {@code merged_into} points at the survivor master (logical reference -
 * no physical FK anywhere in this module).
 *
 * @author birdbro
 */
@TableName("patient_master")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PatientMaster extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 平台患者号（发号服务发放，PROVISIONAL 占位行同样携带）. */
    private String patId;

    /** 证件类型（可空：无名急挂号可无证件）. */
    private String idType;

    /** 证件号码（可空；存在时与 idType 构成身份锚点部分唯一索引）. */
    private String idNo;

    /** 姓名（NULLable: unnamed emergency）. */
    private String name;

    /** 拼音（服务端生成）. */
    private String pinyin;

    /** 性别 0 未知 / 1 男 / 2 女（DB 默认 0）. */
    private Integer gender;

    /** 出生日期（date 类型列，非字符串生日）. */
    private Date birthDate;

    /** 联系电话. */
    private String phone;

    /** 民族. */
    private String nation;

    /** 地址. */
    private String address;

    /** 档案状态（PROVISIONAL -> RESOLVED，服务端单向状态机，无 DB CHECK）. */
    private String archiveStatus;

    /** 存续主索引 id（合并后指向 survivor；NULL = 未合并）. */
    private Integer mergedInto;

    /** 发证机构 id（org_info.id，确知后回填）. */
    private Integer oid;
}

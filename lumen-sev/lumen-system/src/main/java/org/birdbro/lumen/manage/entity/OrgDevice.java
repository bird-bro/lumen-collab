package org.birdbro.lumen.manage.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.birdbro.common.core.base.EntityBase;

/**
 * OrgDevice ({@code org_device}, org-device-room-api): a medical imaging DEVICE as an
 * asset plus its DICOM access point. The 2026-11-10 split ruling keeps everything the
 * device CONNECTS with on this row - the {@code ae/ip/port} access block and the Box
 * acquisition block - while {@code org_room} stays a business place referencing a device
 * by {@code dev_id}. OPS baggage (vehicle fields, ezviz video keys, lessor, contract id)
 * is trimmed per the 2026-11-12 ruling. {@code did} is nullable on purpose: a device may
 * hang off the org before a department claims it. No physical FKs - references are
 * service-enforced (A0400 on a dead parent).
 *
 * <p>Dates carry the OPS {@code yyyy-MM-dd} JSON shape (design D8: a {@code date}
 * column needs no time component on the wire).</p>
 *
 * @author birdbro
 */
@TableName("org_device")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrgDevice extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 设备编号, unique within the owning org among live rows (service-enforced). */
    private String code;

    /** 设备名称. */
    private String name;

    /** 设备类型 - the dict-1024 VALUE (the modality token 'CT'/'DR'; ruling
     * 2026-11-12: business matching joins on the token, not a surrogate code). */
    private String type;

    /** 设备状态 (dict 1025 codes - asset lifecycle 在用/停用/维修/报废). */
    private Integer status;

    /** 归属类型 (dict 1026 codes - 10 自购 / 11 租赁, the OPS numerics). */
    private Integer tag;

    /** 品牌. */
    private String brand;

    /** 型号. */
    private String model;

    /** 规格. */
    private String spec;

    /** 序列号. */
    private String serialNum;

    /** 生产厂商. */
    private String manufacturer;

    /** 生产日期. */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date productionDate;

    /** 采购日期. */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date purchaseDate;

    /** 验收日期. */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date acceptanceDate;

    /** 负责人. */
    private String responsible;

    /** 负责人电话. */
    private String responsiblePhone;

    /** 使用开始日期. */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date startDate;

    /** 使用结束日期. */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;

    /** 备注. */
    private String remark;

    /** 所属机构 (org_info.id, NOT NULL). */
    private Integer oid;

    /** 所属部门 (org_dep.id, nullable - optional anchor). */
    private Integer did;

    /** DICOM AE title. */
    private String ae;

    /** DICOM device IP. */
    private String ip;

    /** DICOM device port. */
    private String port;

    /** Box 适配器. */
    private String boxAdapter;

    /** Box IP. */
    private String boxIp;

    /** Box port. */
    private String boxPort;

    /** Box MAC. */
    private String boxMac;

    /** Box 打印机. */
    private String boxPrint;

    /** 图像采集宽. */
    private Integer captureW;

    /** 图像采集高. */
    private Integer captureH;

    /** 是否剪裁. */
    private Boolean prune;

    /** 剪裁上. */
    private Integer pruneU;

    /** 剪裁下. */
    private Integer pruneD;

    /** 剪裁左. */
    private Integer pruneL;

    /** 剪裁右. */
    private Integer pruneR;
}

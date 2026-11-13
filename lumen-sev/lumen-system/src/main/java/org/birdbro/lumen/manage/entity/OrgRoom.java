package org.birdbro.lumen.manage.entity;

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
 * OrgRoom ({@code org_room}, org-device-room-api): an exam ROOM - the business place a
 * study anchors on. Deliberately device-free: the room references its equipment by the
 * rebindable {@code dev_id} (service-enforced, zero physical FKs) and carries NO inline
 * device access column of the OPS shape (the split ruling - the device row is the single
 * source for ae/ip/port). {@code oid + did} are both NOT NULL: a room is always anchored.
 * The 叫号 block ({@code call_ip/port/mac}) belongs HERE - the queue screen hangs at the
 * room door, not on the device.
 *
 * @author birdbro
 */
@TableName("org_room")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrgRoom extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 所属机构 (org_info.id, NOT NULL). */
    private Integer oid;

    /** 负责科室 (org_dep.id, NOT NULL). */
    private Integer did;

    /** 诊室名称, unique within the owning department among live rows (service-enforced). */
    private String name;

    /** 诊室编码 (optional operational label). */
    private String code;

    /** 启用状态 (dict common_status codes - 1 启用 / 0 停用). */
    private Integer enable;

    /** 叫号屏 IP. */
    private String callIp;

    /** 叫号屏 port. */
    private String callPort;

    /** 叫号屏 MAC. */
    private String callMac;

    /** 绑定设备 (org_device.id, service-enforced reference, nullable until bound). */
    private Integer devId;
}

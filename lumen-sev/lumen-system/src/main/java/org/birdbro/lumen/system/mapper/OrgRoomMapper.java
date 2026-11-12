package org.birdbro.lumen.system.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.birdbro.lumen.system.dto.deviceroom.RoomRow;
import org.birdbro.lumen.system.entity.OrgRoom;

/**
 * MyBatis-Plus mapper for {@code org_room} (org-device-room-api task 1.2). Beyond the
 * BaseMapper surface it carries the ONE cross-table read of the registry (design D2):
 * the room rows LEFT JOIN their bound device, so the page/detail reads resolve
 * devId/devName/devAe/devIp/devPort in a single query - no N+1 service loop, and the
 * device summary is always the LIVE device row (never a copied shadow column).
 *
 * @author birdbro
 */
@Mapper
public interface OrgRoomMapper extends BaseMapper<OrgRoom> {

    /**
     * Paged room rows with the bound-device summary. The join matches LIVE devices only;
     * an unbound (or dead-device) room renders null summary fields. Ordered by id
     * descending (the registry pages read newest-first).
     *
     * @param page   MP pagination carrier (the interceptor rewrites the query)
     * @param orgId  required org scope
     * @param depId  optional department filter
     * @param name   optional room-name contains filter
     * @return the filtered page of joined rows
     */
    @Select("<script>"
            + "SELECT r.id, r.oid, r.did, r.name, r.code, r.enable,"
            + " r.call_ip, r.call_port, r.call_mac, r.dev_id,"
            + " d.name AS dev_name, d.ae AS dev_ae, d.ip AS dev_ip, d.port AS dev_port"
            + " FROM org_room r"
            + " LEFT JOIN org_device d ON d.id = r.dev_id AND d.deleted = 0"
            + " WHERE r.deleted = 0"
            + " <if test='orgId != null'> AND r.oid = #{orgId}</if>"
            + " <if test='depId != null'> AND r.did = #{depId}</if>"
            + " <if test='name != null and name != \"\"'> AND r.name LIKE CONCAT('%', #{name}, '%')</if>"
            + " ORDER BY r.id DESC"
            + "</script>")
    IPage<RoomRow> selectRoomPage(Page<RoomRow> page, @Param("orgId") Integer orgId,
            @Param("depId") Integer depId, @Param("name") String name);

    /**
     * Detail echo with the same join shape (spec: the detail carries the bound-device
     * summary resolved server-side).
     *
     * @param id the room id
     * @return the joined row, or null when the id misses a live room
     */
    @Select("SELECT r.id, r.oid, r.did, r.name, r.code, r.enable,"
            + " r.call_ip, r.call_port, r.call_mac, r.dev_id,"
            + " d.name AS dev_name, d.ae AS dev_ae, d.ip AS dev_ip, d.port AS dev_port"
            + " FROM org_room r"
            + " LEFT JOIN org_device d ON d.id = r.dev_id AND d.deleted = 0"
            + " WHERE r.deleted = 0 AND r.id = #{id}")
    RoomRow selectRoomDetail(@Param("id") Integer id);

    /**
     * Picker feed: live rooms of one org (optionally one department), join-free - the
     * list contract stays summary-light (design D2).
     *
     * @param orgId required org scope
     * @param depId optional department scope
     * @return the live room entities
     */
    @Select("<script>"
            + "SELECT * FROM org_room WHERE deleted = 0 AND oid = #{orgId}"
            + " <if test='depId != null'> AND did = #{depId}</if>"
            + " ORDER BY id"
            + "</script>")
    List<OrgRoom> selectRoomList(@Param("orgId") Integer orgId, @Param("depId") Integer depId);
}

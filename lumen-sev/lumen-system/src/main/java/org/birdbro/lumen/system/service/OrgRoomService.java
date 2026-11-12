package org.birdbro.lumen.system.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.lumen.system.dto.deviceroom.RoomCreateRequest;
import org.birdbro.lumen.system.dto.deviceroom.RoomPageQuery;
import org.birdbro.lumen.system.dto.deviceroom.RoomRow;
import org.birdbro.lumen.system.dto.deviceroom.RoomStatusRequest;
import org.birdbro.lumen.system.dto.deviceroom.RoomUpdateRequest;
import org.birdbro.lumen.system.entity.OrgDep;
import org.birdbro.lumen.system.entity.OrgDevice;
import org.birdbro.lumen.system.entity.OrgRoom;
import org.birdbro.lumen.system.mapper.OrgDepMapper;
import org.birdbro.lumen.system.mapper.OrgDeviceMapper;
import org.birdbro.lumen.system.mapper.OrgInfoMapper;
import org.birdbro.lumen.system.mapper.OrgRoomMapper;
import org.springframework.stereotype.Service;

/**
 * Exam-room registry management (org-device-room-api task 3.2; concrete service - the
 * service-package-layout ruling, MenuService precedent): CRUD over {@code org_room}
 * under {@code /api/v1/system/org-rooms}. The room is a BUSINESS place: it references
 * its device by the rebindable {@code dev_id} and NEVER copies device fields (the
 * split ruling); reads resolve the device summary by the mapper's LEFT JOIN (D2, no
 * N+1). References are SERVICE-enforced (D3): live org, live same-org department,
 * live same-org device; the name is unique within the owning department among live
 * rows (D4). Updates are D9 NULL-AWARE full-replaces (absent optionals clear, incl.
 * the {@code dev_id} binding); the status toggle is a D6 dedicated command.
 *
 * <p>Error-code mapping: every registry violation is {@code A0400} with a descriptive
 * message (the spec's uniform rejection code for this family).</p>
 *
 * @author birdbro
 */
@Service
public class OrgRoomService {

    private final OrgRoomMapper orgRoomMapper;
    private final OrgDeviceMapper orgDeviceMapper;
    private final OrgInfoMapper orgInfoMapper;
    private final OrgDepMapper orgDepMapper;

    public OrgRoomService(OrgRoomMapper orgRoomMapper, OrgDeviceMapper orgDeviceMapper,
            OrgInfoMapper orgInfoMapper, OrgDepMapper orgDepMapper) {
        this.orgRoomMapper = orgRoomMapper;
        this.orgDeviceMapper = orgDeviceMapper;
        this.orgInfoMapper = orgInfoMapper;
        this.orgDepMapper = orgDepMapper;
    }

    /**
     * Paginated room table with the joined device summary (D10: a present orgId
     * filters to that campus, an absent one lists every campus); id descending.
     */
    public IPage<RoomRow> getPage(RoomPageQuery query) {
        return orgRoomMapper.selectRoomPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                query.getOrgId(), query.getDepId(), query.getName());
    }

    /** Picker/feed list: the org's (optionally one department's) live rooms, join-free. */
    public List<OrgRoom> getList(Integer orgId, Integer depId) {
        requireLiveOrg(orgId, "orgId");
        if (depId != null) {
            requireLiveDepartmentOfOrg(depId, orgId);
        }
        return orgRoomMapper.selectRoomList(orgId, depId);
    }

    /**
     * One room with the bound-device summary resolved server-side, or null when the id
     * misses a live row.
     */
    public RoomRow getById(Integer id) {
        return orgRoomMapper.selectRoomDetail(id);
    }

    /**
     * Creates a room (dead/foreign oid/did/devId and duplicate name-in-department
     * reject A0400, no row written); returns the database-generated id.
     */
    public Integer create(RoomCreateRequest request) {
        requireLiveOrg(request.getOid(), "oid");
        requireLiveDepartmentOfOrg(request.getDid(), request.getOid());
        requireLiveDeviceOfOrg(request.getDevId(), request.getOid());
        rejectDuplicateName(request.getName(), request.getDid(), null);

        OrgRoom room = new OrgRoom();
        room.setOid(request.getOid());
        room.setDid(request.getDid());
        room.setName(request.getName());
        room.setCode(request.getCode());
        room.setEnable(request.getEnable());
        room.setDevId(request.getDevId());
        room.setCallIp(request.getCallIp());
        room.setCallPort(request.getCallPort());
        room.setCallMac(request.getCallMac());
        orgRoomMapper.insert(room);
        return room.getId();
    }

    /**
     * Full-replaces the editable surface (D9 null-aware), including REBIND (a new live
     * same-org {@code devId}) and CLEAR ({@code devId} absent); a missing room id
     * rejects A0400.
     */
    public Boolean update(Integer id, RoomUpdateRequest request) {
        OrgRoom current = orgRoomMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "room id " + id + " does not exist");
        }
        requireLiveOrg(request.getOid(), "oid");
        requireLiveDepartmentOfOrg(request.getDid(), request.getOid());
        requireLiveDeviceOfOrg(request.getDevId(), request.getOid());
        rejectDuplicateName(request.getName(), request.getDid(), id);

        // D9 NULL-AWARE full replace: an explicit wrapper writes EVERY editable column,
        // so an absent devId CLEARS the binding and absent call fields clear too (the
        // split model's rebind/clear semantics, design D2/D9).
        LambdaUpdateWrapper<OrgRoom> uw = new LambdaUpdateWrapper<>();
        uw.eq(OrgRoom::getId, id)
                .set(OrgRoom::getOid, request.getOid())
                .set(OrgRoom::getDid, request.getDid())
                .set(OrgRoom::getName, request.getName())
                .set(OrgRoom::getCode, request.getCode())
                .set(OrgRoom::getDevId, request.getDevId())
                .set(OrgRoom::getCallIp, request.getCallIp())
                .set(OrgRoom::getCallPort, request.getCallPort())
                .set(OrgRoom::getCallMac, request.getCallMac())
                .set(OrgRoom::getUpdateBy, currentUserUid())
                .set(OrgRoom::getUpdateTime, new Date());
        orgRoomMapper.update(null, uw);
        return Boolean.TRUE;
    }

    /** Toggles only {@code enable} (D6 dedicated command); a missing id rejects A0400. */
    public Boolean setStatus(Integer id, RoomStatusRequest request) {
        OrgRoom current = orgRoomMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "room id " + id + " does not exist");
        }
        // A focused patch: only the enable column changes, independent of the generic
        // update path (D6; mirrors OrgDepServiceImpl.setStatus).
        LambdaUpdateWrapper<OrgRoom> uw = new LambdaUpdateWrapper<>();
        uw.eq(OrgRoom::getId, id)
                .set(OrgRoom::getEnable, request.getEnable())
                .set(OrgRoom::getUpdateBy, currentUserUid())
                .set(OrgRoom::getUpdateTime, new Date());
        orgRoomMapper.update(null, uw);
        return Boolean.TRUE;
    }

    /**
     * Logically deletes a room (no downstream rows exist yet - the workstation feature
     * adds its own guard); a missing room id rejects A0400.
     */
    public Boolean delete(Integer id) {
        OrgRoom current = orgRoomMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "room id " + id + " does not exist");
        }
        // @TableLogic turns this into UPDATE deleted = 1 (EntityBase contract).
        orgRoomMapper.deleteById(id);
        return Boolean.TRUE;
    }

    // ===================== D3/D4 service-level guards =====================

    private void requireLiveOrg(Integer oid, String field) {
        if (oid == null || orgInfoMapper.selectById(oid) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    field + " must reference a live org row");
        }
    }

    private void requireLiveDepartmentOfOrg(Integer did, Integer oid) {
        if (did == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "did must not be null - a room is always anchored");
        }
        OrgDep dep = orgDepMapper.selectById(did);
        if (dep == null || !dep.getOrgId().equals(oid)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "did must reference a live department of the same org");
        }
    }

    private void requireLiveDeviceOfOrg(Integer devId, Integer oid) {
        if (devId == null) {
            return;
        }
        OrgDevice device = orgDeviceMapper.selectById(devId);
        if (device == null || !device.getOid().equals(oid)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "devId must reference a live device of the same org");
        }
    }

    private void rejectDuplicateName(String name, Integer did, Integer excludeId) {
        LambdaQueryWrapper<OrgRoom> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgRoom::getName, name).eq(OrgRoom::getDid, did)
                .ne(excludeId != null, OrgRoom::getId, excludeId);
        if (orgRoomMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "room name " + name + " already exists in this department");
        }
    }

    private Integer currentUserUid() {
        UserHeader header = SecurityUtils.getUserHeader();
        return header == null ? null : header.getUid();
    }
}

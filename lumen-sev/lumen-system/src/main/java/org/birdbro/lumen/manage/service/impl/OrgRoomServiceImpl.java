package org.birdbro.lumen.manage.service.impl;

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
import org.birdbro.lumen.manage.dto.deviceroom.RoomCreateRequest;
import org.birdbro.lumen.manage.dto.deviceroom.RoomPageQuery;
import org.birdbro.lumen.manage.dto.deviceroom.RoomRow;
import org.birdbro.lumen.manage.dto.deviceroom.RoomStatusRequest;
import org.birdbro.lumen.manage.dto.deviceroom.RoomUpdateRequest;
import org.birdbro.lumen.manage.entity.OrgDep;
import org.birdbro.lumen.manage.entity.OrgDevice;
import org.birdbro.lumen.manage.entity.OrgRoom;
import org.birdbro.lumen.manage.mapper.OrgDepMapper;
import org.birdbro.lumen.manage.mapper.OrgDeviceMapper;
import org.birdbro.lumen.manage.mapper.OrgInfoMapper;
import org.birdbro.lumen.manage.mapper.OrgRoomMapper;
import org.birdbro.lumen.manage.service.OrgRoomService;
import org.springframework.stereotype.Service;

/**
 * {@link OrgRoomService} implementation - the body is unchanged from the former
 * concrete class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched).
 *
 * @author birdbro
 */
@Service
public class OrgRoomServiceImpl implements OrgRoomService {

    private final OrgRoomMapper orgRoomMapper;
    private final OrgDeviceMapper orgDeviceMapper;
    private final OrgInfoMapper orgInfoMapper;
    private final OrgDepMapper orgDepMapper;

    public OrgRoomServiceImpl(OrgRoomMapper orgRoomMapper, OrgDeviceMapper orgDeviceMapper,
            OrgInfoMapper orgInfoMapper, OrgDepMapper orgDepMapper) {
        this.orgRoomMapper = orgRoomMapper;
        this.orgDeviceMapper = orgDeviceMapper;
        this.orgInfoMapper = orgInfoMapper;
        this.orgDepMapper = orgDepMapper;
    }

    @Override
    public IPage<RoomRow> getPage(RoomPageQuery query) {
        return orgRoomMapper.selectRoomPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                query.getOrgId(), query.getDepId(), query.getName());
    }

    @Override
    public List<OrgRoom> getList(Integer orgId, Integer depId) {
        requireLiveOrg(orgId, "orgId");
        if (depId != null) {
            requireLiveDepartmentOfOrg(depId, orgId);
        }
        return orgRoomMapper.selectRoomList(orgId, depId);
    }

    @Override
    public RoomRow getById(Integer id) {
        return orgRoomMapper.selectRoomDetail(id);
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

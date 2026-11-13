package org.birdbro.lumen.manage.service.impl;

import java.util.Date;
import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.lumen.manage.dto.deviceroom.DeviceCreateRequest;
import org.birdbro.lumen.manage.dto.deviceroom.DevicePageQuery;
import org.birdbro.lumen.manage.dto.deviceroom.DeviceUpdateRequest;
import org.birdbro.lumen.manage.entity.OrgDep;
import org.birdbro.lumen.manage.entity.OrgDevice;
import org.birdbro.lumen.manage.entity.OrgRoom;
import org.birdbro.lumen.manage.mapper.OrgDepMapper;
import org.birdbro.lumen.manage.mapper.OrgDeviceMapper;
import org.birdbro.lumen.manage.mapper.OrgInfoMapper;
import org.birdbro.lumen.manage.mapper.OrgRoomMapper;
import org.birdbro.lumen.manage.service.OrgDeviceService;
import org.springframework.stereotype.Service;

/**
 * {@link OrgDeviceService} implementation - the body is unchanged from the former
 * concrete class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched).
 *
 * @author birdbro
 */
@Service
public class OrgDeviceServiceImpl implements OrgDeviceService {

    private final OrgDeviceMapper orgDeviceMapper;
    private final OrgInfoMapper orgInfoMapper;
    private final OrgDepMapper orgDepMapper;
    private final OrgRoomMapper orgRoomMapper;

    public OrgDeviceServiceImpl(OrgDeviceMapper orgDeviceMapper, OrgInfoMapper orgInfoMapper,
            OrgDepMapper orgDepMapper, OrgRoomMapper orgRoomMapper) {
        this.orgDeviceMapper = orgDeviceMapper;
        this.orgInfoMapper = orgInfoMapper;
        this.orgDepMapper = orgDepMapper;
        this.orgRoomMapper = orgRoomMapper;
    }

    @Override
    public IPage<OrgDevice> getPage(DevicePageQuery query) {
        LambdaQueryWrapper<OrgDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getOrgId() != null, OrgDevice::getOid, query.getOrgId());
        if (query.getType() != null) {
            wrapper.eq(OrgDevice::getType, query.getType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(OrgDevice::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(OrgDevice::getName, query.getKeyword())
                    .or().like(OrgDevice::getCode, query.getKeyword()));
        }
        wrapper.orderByDesc(OrgDevice::getId);
        return orgDeviceMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper);
    }

    @Override
    public List<OrgDevice> getList(Integer orgId, Integer depId) {
        requireLiveOrg(orgId, "orgId");
        if (depId != null) {
            requireLiveDepartmentOfOrg(depId, orgId);
        }
        LambdaQueryWrapper<OrgDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgDevice::getOid, orgId)
                .eq(depId != null, OrgDevice::getDid, depId)
                .orderByAsc(OrgDevice::getName);
        return orgDeviceMapper.selectList(wrapper);
    }

    @Override
    public OrgDevice getById(Integer id) {
        return orgDeviceMapper.selectById(id);
    }

    @Override
    public Integer create(DeviceCreateRequest request) {
        requireLiveOrg(request.getOid(), "oid");
        requireDeviceDepartment(request.getDid(), request.getOid());
        rejectDuplicateCode(request.getCode(), request.getOid(), null);

        OrgDevice device = new OrgDevice();
        BeanUtil.copyProperties(request, device, true);
        orgDeviceMapper.insert(device);
        return device.getId();
    }

    @Override
    public Boolean update(Integer id, DeviceUpdateRequest request) {
        OrgDevice current = orgDeviceMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "device id " + id + " does not exist");
        }
        requireLiveOrg(request.getOid(), "oid");
        requireDeviceDepartment(request.getDid(), request.getOid());
        rejectDuplicateCode(request.getCode(), request.getOid(), id);

        // D9 NULL-AWARE full replace: an explicit wrapper writes EVERY editable column,
        // so absent optionals (did, profile, access, acquisition) clear instead of
        // silently keeping their old values the way updateById's null-skip would.
        LambdaUpdateWrapper<OrgDevice> uw = new LambdaUpdateWrapper<>();
        uw.eq(OrgDevice::getId, id)
                .set(OrgDevice::getCode, request.getCode())
                .set(OrgDevice::getName, request.getName())
                .set(OrgDevice::getType, request.getType())
                .set(OrgDevice::getStatus, request.getStatus())
                .set(OrgDevice::getTag, request.getTag())
                .set(OrgDevice::getOid, request.getOid())
                .set(OrgDevice::getDid, request.getDid())
                .set(OrgDevice::getBrand, request.getBrand())
                .set(OrgDevice::getModel, request.getModel())
                .set(OrgDevice::getSpec, request.getSpec())
                .set(OrgDevice::getSerialNum, request.getSerialNum())
                .set(OrgDevice::getManufacturer, request.getManufacturer())
                .set(OrgDevice::getProductionDate, request.getProductionDate())
                .set(OrgDevice::getPurchaseDate, request.getPurchaseDate())
                .set(OrgDevice::getAcceptanceDate, request.getAcceptanceDate())
                .set(OrgDevice::getResponsible, request.getResponsible())
                .set(OrgDevice::getResponsiblePhone, request.getResponsiblePhone())
                .set(OrgDevice::getStartDate, request.getStartDate())
                .set(OrgDevice::getEndDate, request.getEndDate())
                .set(OrgDevice::getRemark, request.getRemark())
                .set(OrgDevice::getAe, request.getAe())
                .set(OrgDevice::getIp, request.getIp())
                .set(OrgDevice::getPort, request.getPort())
                .set(OrgDevice::getBoxAdapter, request.getBoxAdapter())
                .set(OrgDevice::getBoxIp, request.getBoxIp())
                .set(OrgDevice::getBoxPort, request.getBoxPort())
                .set(OrgDevice::getBoxMac, request.getBoxMac())
                .set(OrgDevice::getBoxPrint, request.getBoxPrint())
                .set(OrgDevice::getCaptureW, request.getCaptureW())
                .set(OrgDevice::getCaptureH, request.getCaptureH())
                .set(OrgDevice::getPrune, request.getPrune())
                .set(OrgDevice::getPruneU, request.getPruneU())
                .set(OrgDevice::getPruneD, request.getPruneD())
                .set(OrgDevice::getPruneL, request.getPruneL())
                .set(OrgDevice::getPruneR, request.getPruneR())
                .set(OrgDevice::getUpdateBy, currentUserUid())
                .set(OrgDevice::getUpdateTime, new Date());
        orgDeviceMapper.update(null, uw);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        OrgDevice current = orgDeviceMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "device id " + id + " does not exist");
        }
        Long roomCount = orgRoomMapper.selectCount(
                new LambdaQueryWrapper<OrgRoom>().eq(OrgRoom::getDevId, id));
        if (roomCount != null && roomCount > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "device is still referenced by " + roomCount
                            + " live room(s) - rebind the room(s) before deleting");
        }
        // @TableLogic turns this into UPDATE deleted = 1 (EntityBase contract).
        orgDeviceMapper.deleteById(id);
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
            return;
        }
        requireDeviceDepartment(did, oid);
    }

    /**
     * The second-round ruling's device anchor (D3): {@code did} is REQUIRED - a device
     * always belongs to a 使用科室 (the DB column stays nullable; the service is the
     * enforcement point).
     */
    private void requireDeviceDepartment(Integer did, Integer oid) {
        if (did == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "did must not be null - a device belongs to a department");
        }
        OrgDep dep = orgDepMapper.selectById(did);
        if (dep == null || !dep.getOrgId().equals(oid)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "did must reference a live department of the same org");
        }
    }

    private void rejectDuplicateCode(String code, Integer oid, Integer excludeId) {
        LambdaQueryWrapper<OrgDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgDevice::getCode, code).eq(OrgDevice::getOid, oid)
                .ne(excludeId != null, OrgDevice::getId, excludeId);
        if (orgDeviceMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "device code " + code + " already exists in this org");
        }
    }

    private Integer currentUserUid() {
        UserHeader header = SecurityUtils.getUserHeader();
        return header == null ? null : header.getUid();
    }
}

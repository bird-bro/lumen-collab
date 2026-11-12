package org.birdbro.lumen.system.service;

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
import org.birdbro.lumen.system.dto.deviceroom.DeviceCreateRequest;
import org.birdbro.lumen.system.dto.deviceroom.DevicePageQuery;
import org.birdbro.lumen.system.dto.deviceroom.DeviceUpdateRequest;
import org.birdbro.lumen.system.entity.OrgDep;
import org.birdbro.lumen.system.entity.OrgDevice;
import org.birdbro.lumen.system.entity.OrgRoom;
import org.birdbro.lumen.system.mapper.OrgDepMapper;
import org.birdbro.lumen.system.mapper.OrgDeviceMapper;
import org.birdbro.lumen.system.mapper.OrgInfoMapper;
import org.birdbro.lumen.system.mapper.OrgRoomMapper;
import org.springframework.stereotype.Service;

/**
 * Device registry management (org-device-room-api task 2.2; concrete service - the
 * service-package-layout ruling, MenuService precedent): CRUD over {@code org_device}
 * under {@code /api/v1/system/org-devices}. Every device is org-scoped; references are
 * SERVICE-enforced (zero physical FKs): {@code oid} must hit a live org, a present
 * {@code did} a live department of the SAME org, the {@code code} unique within the org
 * among live rows (D3/D4). Updates are D9 NULL-AWARE full-replaces - absent optional
 * fields CLEAR (an explicit wrapper update, never the null-skipping updateById). The
 * delete guard counts LIVE room references and names the count in the message - the
 * room must rebind before its device can go.
 *
 * <p>Error-code mapping: every registry violation is {@code A0400} with a descriptive
 * message (the spec's uniform rejection code for this family).</p>
 *
 * @author birdbro
 */
@Service
public class OrgDeviceService {

    private final OrgDeviceMapper orgDeviceMapper;
    private final OrgInfoMapper orgInfoMapper;
    private final OrgDepMapper orgDepMapper;
    private final OrgRoomMapper orgRoomMapper;

    public OrgDeviceService(OrgDeviceMapper orgDeviceMapper, OrgInfoMapper orgInfoMapper,
            OrgDepMapper orgDepMapper, OrgRoomMapper orgRoomMapper) {
        this.orgDeviceMapper = orgDeviceMapper;
        this.orgInfoMapper = orgInfoMapper;
        this.orgDepMapper = orgDepMapper;
        this.orgRoomMapper = orgRoomMapper;
    }

    /**
     * Paginated device table (D10, second-round ruling): a present {@code orgId}
     * filters to that campus, an ABSENT one lists every campus (the tree's
     * hospital-root selection shows the whole estate); type/status dict filters and
     * the name-or-code keyword apply on top. Ordered id-descending.
     */
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

    /**
     * Picker feed for the room form (D10): the org's live devices, name-ordered,
     * narrowed to one department when {@code depId} is present (the picker feeds from
     * the room's own 负责科室).
     */
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

    /** One device, or null when the id misses a live row. */
    public OrgDevice getById(Integer id) {
        return orgDeviceMapper.selectById(id);
    }

    /**
     * Creates a device (duplicate live code / dead or foreign department reject A0400,
     * no row written); returns the database-generated id of the new row.
     */
    public Integer create(DeviceCreateRequest request) {
        requireLiveOrg(request.getOid(), "oid");
        requireDeviceDepartment(request.getDid(), request.getOid());
        rejectDuplicateCode(request.getCode(), request.getOid(), null);

        OrgDevice device = new OrgDevice();
        BeanUtil.copyProperties(request, device, true);
        orgDeviceMapper.insert(device);
        return device.getId();
    }

    /** Full-replaces the editable surface (D9 null-aware); a missing id rejects A0400. */
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

    /**
     * Logically deletes a device; a device still referenced by any live room rejects
     * A0400 with the room count in the message (the room must rebind first).
     */
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

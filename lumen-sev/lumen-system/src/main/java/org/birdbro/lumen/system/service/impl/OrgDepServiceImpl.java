package org.birdbro.lumen.system.service.impl;

import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.lumen.system.dto.CreateOrgDepRequest;
import org.birdbro.lumen.system.dto.OrgDepPageQuery;
import org.birdbro.lumen.system.dto.UpdateOrgDepRequest;
import org.birdbro.lumen.system.entity.OrgDep;
import org.birdbro.lumen.system.entity.OrgInfo;
import org.birdbro.lumen.system.mapper.OrgDepMapper;
import org.birdbro.lumen.system.mapper.OrgInfoMapper;
import org.birdbro.lumen.system.service.OrgDepService;
import org.springframework.stereotype.Service;

/**
 * Campus-scoped department management (org-management-redesign). A department must
 * belong to a live, enabled campus; hospital roots are not valid owners. The code is
 * immutable after creation and unique within the owning campus, but the same code may be
 * reused on another campus.
 *
 * <p>Error-code mapping: an invalid/missing campus is {@code A0400}; a duplicate live
 * code is {@code A0712}; an update on a no-longer-live department is {@code B0031}.
 * Logical delete remains a bare {@code deleteById}.</p>
 *
 * @author birdbro
 */
@Service
public class OrgDepServiceImpl implements OrgDepService {

    private static final int ORG_TYPE_HOSPITAL = 0;
    private static final int ORG_TYPE_CAMPUS = 1;
    private static final int ORG_STATUS_ENABLED = 1;
    private static final int ORG_STATUS_DISABLED = 0;

    private final OrgDepMapper orgDepMapper;
    private final OrgInfoMapper orgInfoMapper;

    public OrgDepServiceImpl(OrgDepMapper orgDepMapper, OrgInfoMapper orgInfoMapper) {
        this.orgDepMapper = orgDepMapper;
        this.orgInfoMapper = orgInfoMapper;
    }

    @Override
    public Integer create(CreateOrgDepRequest request) {
        requireLiveCampus(request.getOrgId());
        validateStatus(request.getStatus());
        rejectDuplicateCode(request.getCode(), request.getOrgId(), null);

        OrgDep department = new OrgDep();
        BeanUtil.copyProperties(request, department, true);
        if (department.getStatus() == null) {
            department.setStatus(ORG_STATUS_ENABLED);
        }
        orgDepMapper.insert(department);
        return department.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateOrgDepRequest request) {
        OrgDep current = orgDepMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "department id " + id + " does not exist");
        }
        validateStatus(request.getStatus());

        Integer targetOrgId = request.getOrgId() == null ? current.getOrgId() : request.getOrgId();
        requireLiveCampus(targetOrgId);
        if (request.getCode() != null && !request.getCode().equals(current.getCode())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "department code cannot be changed");
        }
        rejectDuplicateCode(current.getCode(), targetOrgId, id);

        OrgDep patch = new OrgDep();
        BeanUtil.copyProperties(request, patch, true);
        patch.setId(id);
        orgDepMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        // @TableLogic turns this into UPDATE deleted = 1 (EntityBase contract).
        orgDepMapper.deleteById(id);
        return Boolean.TRUE;
    }

    @Override
    public List<OrgDep> list(Integer orgId) {
        LambdaQueryWrapper<OrgDep> wrapper = new LambdaQueryWrapper<>();
        if (orgId != null) {
            wrapper.eq(OrgDep::getOrgId, orgId);
        }
        wrapper.orderByAsc(OrgDep::getId);
        // @TableLogic appends deleted = 0.
        return orgDepMapper.selectList(wrapper);
    }

    @Override
    public IPage<OrgDep> page(OrgDepPageQuery query) {
        if (query.getOrgId() != null) {
            requireLiveCampus(query.getOrgId());
        }
        Page<OrgDep> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<OrgDep> wrapper = new LambdaQueryWrapper<OrgDep>()
                .eq(GadgetUtils.isNotNull(query.getOrgId()), OrgDep::getOrgId, query.getOrgId())
                .eq(GadgetUtils.isNotNull(query.getStatus()), OrgDep::getStatus, query.getStatus())
                .and(GadgetUtils.isNotEmpty(query.getKeyword()), condition -> condition
                        .like(OrgDep::getCode, query.getKeyword())
                        .or()
                        .like(OrgDep::getName, query.getKeyword()))
                .orderByAsc(OrgDep::getId);
        // @TableLogic appends deleted = 0.
        return orgDepMapper.selectPage(page, wrapper);
    }

    private void requireLiveCampus(Integer orgId) {
        OrgInfo org = orgInfoMapper.selectById(orgId);
        if (org == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "campus does not exist: org id " + orgId);
        }
        if (org.getType() == null || org.getType() == ORG_TYPE_HOSPITAL) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "a department must belong to a campus: org id " + orgId);
        }
        if (org.getType() != ORG_TYPE_CAMPUS) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "organization is not a campus: org id " + orgId);
        }
        if (org.getStatus() == null || org.getStatus() != ORG_STATUS_ENABLED) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "campus is disabled: org id " + orgId);
        }
    }

    /** The code is unique among live rows within the campus; {@code excludeId} spares the row itself. */
    private void rejectDuplicateCode(String code, Integer orgId, Integer excludeId) {
        LambdaQueryWrapper<OrgDep> wrapper = new LambdaQueryWrapper<OrgDep>()
                .eq(OrgDep::getCode, code)
                .eq(OrgDep::getOrgId, orgId);
        if (excludeId != null) {
            wrapper.ne(OrgDep::getId, excludeId);
        }
        if (orgDepMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0712,
                    "department code already exists on this campus: " + code);
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && status != ORG_STATUS_ENABLED && status != ORG_STATUS_DISABLED) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "status must be 1 (enabled) or 0 (disabled)");
        }
    }
}

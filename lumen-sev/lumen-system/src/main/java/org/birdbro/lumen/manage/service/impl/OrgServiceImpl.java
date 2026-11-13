package org.birdbro.lumen.manage.service.impl;

import java.util.List;
import java.util.UUID;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.lumen.manage.dto.CreateOrgRequest;
import org.birdbro.lumen.manage.dto.UpdateOrgRequest;
import org.birdbro.lumen.manage.entity.OrgDep;
import org.birdbro.lumen.manage.entity.OrgInfo;
import org.birdbro.lumen.manage.mapper.OrgDepMapper;
import org.birdbro.lumen.manage.mapper.OrgInfoMapper;
import org.birdbro.lumen.manage.service.OrgService;
import org.birdbro.lumen.manage.service.support.OrgTreeService;
import org.springframework.stereotype.Service;

/**
 * Controlled hospital/campus management (org-management-redesign). Tree integrity and
 * product-level invariants live here because {@code org_info} has no self-FK: only one
 * hospital root may exist, campuses may be created only under that root, and a campus is
 * a leaf. The old request fields remain accepted for compatibility, but type and level
 * are server-derived.
 *
 * <p>Error-code mapping: invalid structure or a referenced-but-missing parent is
 * {@code A0400}; an update on a no-longer-live row is {@code B0031}. Logical delete is
 * delegated to MyBatis-Plus and guarded by live dependents.</p>
 *
 * @author birdbro
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final int ROOT_PARENT_ID = 0;
    private static final int ORG_TYPE_HOSPITAL = 0;
    private static final int ORG_TYPE_CAMPUS = 1;
    private static final int ORG_STATUS_ENABLED = 1;
    private static final int ORG_STATUS_DISABLED = 0;
    private static final int ROOT_LEVEL = 1;
    private static final int CAMPUS_LEVEL = 2;

    private final OrgInfoMapper orgInfoMapper;
    private final OrgDepMapper orgDepMapper;
    private final OrgTreeService orgTreeService;

    public OrgServiceImpl(OrgInfoMapper orgInfoMapper,
                          OrgDepMapper orgDepMapper,
                          OrgTreeService orgTreeService) {
        this.orgInfoMapper = orgInfoMapper;
        this.orgDepMapper = orgDepMapper;
        this.orgTreeService = orgTreeService;
    }

    @Override
    public Integer create(CreateOrgRequest request) {
        Integer upId = request.getUpId();
        boolean root = upId == null || upId == ROOT_PARENT_ID;
        validateStatus(request.getStatus());

        OrgInfo org = new OrgInfo();
        BeanUtil.copyProperties(request, org, true);
        if (GadgetUtils.isNotEmpty(request.getCode())) {
            org.setCode(request.getCode().trim());
            rejectDuplicateOrgCode(org.getCode(), upId, null);
        } else {
            org.setCode(generateCode(root ? "HOSPITAL" : "CAMPUS"));
        }

        if (root) {
            if (countLiveRoots() > 0) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "a live hospital root already exists");
            }
            org.setUpId(ROOT_PARENT_ID);
            org.setType(ORG_TYPE_HOSPITAL);
            org.setLevel(ROOT_LEVEL);
        } else {
            OrgInfo parent = requireLiveOrg(upId, "parent org does not exist");
            if (parent.getType() == null || parent.getType() != ORG_TYPE_HOSPITAL) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "a campus can only be created under the hospital root");
            }
            org.setUpId(parent.getId());
            org.setType(ORG_TYPE_CAMPUS);
            org.setLevel(CAMPUS_LEVEL);
        }

        if (org.getStatus() == null) {
            org.setStatus(ORG_STATUS_ENABLED);
        }
        if (org.getSort() == null) {
            org.setSort(0);
        }
        org.setIsDefault(0);
        orgInfoMapper.insert(org);
        return org.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateOrgRequest request) {
        OrgInfo current = requireLiveOrg(id, "org does not exist");
        validateStatus(request.getStatus());

        if (request.getUpId() != null && !request.getUpId().equals(current.getUpId())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "organization parent cannot be changed");
        }
        if (request.getType() != null && !request.getType().equals(current.getType())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "organization type cannot be changed");
        }
        if (request.getCode() != null) {
            String nextCode = request.getCode().trim();
            if (GadgetUtils.isEmpty(nextCode)) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "organization code must not be blank");
            }
            rejectDuplicateOrgCode(nextCode, current.getUpId(), id);
        }

        // Null fields are skipped by the MyBatis-Plus NOT-NULL update strategy, so this
        // remains a true partial update. Level is intentionally not client-controlled.
        OrgInfo patch = new OrgInfo();
        BeanUtil.copyProperties(request, patch, true);
        patch.setId(id);
        patch.setLevel(null);
        if (patch.getCode() != null) {
            patch.setCode(patch.getCode().trim());
        }
        orgInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean setStatus(Integer id, Integer status) {
        requireLiveOrg(id, "org does not exist");
        validateStatus(status);

        // A focused patch: only the status column changes, independent of the
        // generic profile-update compatibility path.
        OrgInfo patch = new OrgInfo();
        patch.setId(id);
        patch.setStatus(status);
        orgInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        requireLiveOrg(id, "org does not exist");
        Long childCount = orgInfoMapper.selectCount(
                new LambdaQueryWrapper<OrgInfo>().eq(OrgInfo::getUpId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "organization still has live child campuses");
        }
        Long departmentCount = orgDepMapper.selectCount(
                new LambdaQueryWrapper<OrgDep>().eq(OrgDep::getOrgId, id));
        if (departmentCount != null && departmentCount > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "organization still has live departments");
        }

        // @TableLogic turns this into UPDATE deleted = 1 (EntityBase contract).
        orgInfoMapper.deleteById(id);
        return Boolean.TRUE;
    }

    @Override
    public List<OrgInfo> tree() {
        return orgTreeService.orgTree();
    }

    @Override
    public List<OrgInfo> tree(String name) {
        return orgTreeService.orgTree(name);
    }

    private Long countLiveRoots() {
        return orgInfoMapper.selectCount(
                new LambdaQueryWrapper<OrgInfo>().eq(OrgInfo::getUpId, ROOT_PARENT_ID));
    }

    private OrgInfo requireLiveOrg(Integer id, String detail) {
        OrgInfo org = orgInfoMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    detail + ": org id " + id);
        }
        return org;
    }

    private void rejectDuplicateOrgCode(String code, Integer upId, Integer excludeId) {
        LambdaQueryWrapper<OrgInfo> wrapper = new LambdaQueryWrapper<OrgInfo>()
                .eq(OrgInfo::getCode, code)
                .eq(OrgInfo::getUpId, upId == null ? ROOT_PARENT_ID : upId);
        if (excludeId != null) {
            wrapper.ne(OrgInfo::getId, excludeId);
        }
        if (orgInfoMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0712,
                    "organization code already exists: " + code);
        }
    }

    private String generateCode(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return prefix + "-" + suffix;
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != ORG_STATUS_ENABLED && status != ORG_STATUS_DISABLED) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "status must be 1 (enabled) or 0 (disabled)");
        }
    }
}

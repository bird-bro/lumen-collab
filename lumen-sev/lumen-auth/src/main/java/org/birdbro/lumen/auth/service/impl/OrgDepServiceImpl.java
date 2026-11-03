package org.birdbro.lumen.auth.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.auth.dto.CreateOrgDepRequest;
import org.birdbro.lumen.auth.dto.UpdateOrgDepRequest;
import org.birdbro.lumen.auth.entity.OrgDep;
import org.birdbro.lumen.auth.entity.OrgInfo;
import org.birdbro.lumen.auth.mapper.OrgDepMapper;
import org.birdbro.lumen.auth.mapper.OrgInfoMapper;
import org.birdbro.lumen.auth.service.OrgDepService;
import org.springframework.stereotype.Service;

/**
 * OrgDep management implementation (auth-rbac-foundation task 4.2).
 *
 * <p>Error-code mapping (recorded decision): a referenced-but-missing org is a request
 * parameter error ({@code A0400} - the caller asked for an impossible FK; the V1
 * database FK stays as the backstop for anything that slips past, e.g. a concurrent
 * delete); a duplicate live {@code code} is {@code A0712} 组织编码已存在 (the partial
 * unique index {@code uk_org_dep_code} stays the backstop); an update on a row that
 * is no longer live is {@code B0031}. Logical delete is a bare {@code deleteById}
 * ({@code @TableLogic} makes it {@code UPDATE deleted = 1}).</p>
 *
 * <p>Audit identity ({@code create_by}/{@code update_by}) is deliberately NOT set here:
 * the copied {@code MetaObjectConfig} fill owns it, fed by the lumen
 * {@code UserHeaderBridgeFilter} from the JWT principal.</p>
 *
 * @author birdbro
 */
@Service
public class OrgDepServiceImpl implements OrgDepService {

    private final OrgDepMapper orgDepMapper;
    private final OrgInfoMapper orgInfoMapper;

    public OrgDepServiceImpl(OrgDepMapper orgDepMapper, OrgInfoMapper orgInfoMapper) {
        this.orgDepMapper = orgDepMapper;
        this.orgInfoMapper = orgInfoMapper;
    }

    @Override
    public Integer create(CreateOrgDepRequest request) {
        requireLiveOrg(request.getOrgId());
        rejectDuplicateCode(request.getCode(), null);

        OrgDep department = new OrgDep();
        department.setOrgId(request.getOrgId());
        department.setCode(request.getCode());
        department.setName(request.getName());
        orgDepMapper.insert(department);
        return department.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateOrgDepRequest request) {
        if (orgDepMapper.selectById(id) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "department id " + id + " does not exist");
        }
        if (request.getOrgId() != null) {
            requireLiveOrg(request.getOrgId());
        }
        if (request.getCode() != null) {
            rejectDuplicateCode(request.getCode(), id);
        }

        OrgDep patch = new OrgDep();
        patch.setId(id);
        patch.setOrgId(request.getOrgId());
        patch.setCode(request.getCode());
        patch.setName(request.getName());
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

    /** The referenced org must be live ({@code @TableLogic}: deleted orgs count as missing). */
    private void requireLiveOrg(Integer orgId) {
        OrgInfo org = orgInfoMapper.selectById(orgId);
        if (org == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "org does not exist: org id " + orgId);
        }
    }

    /** The stable code is unique among live rows; {@code excludeId} spares the updated row itself. */
    private void rejectDuplicateCode(String code, Integer excludeId) {
        LambdaQueryWrapper<OrgDep> wrapper = new LambdaQueryWrapper<OrgDep>()
                .eq(OrgDep::getCode, code);
        if (excludeId != null) {
            wrapper.ne(OrgDep::getId, excludeId);
        }
        if (orgDepMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0712,
                    "department code already exists: " + code);
        }
    }
}

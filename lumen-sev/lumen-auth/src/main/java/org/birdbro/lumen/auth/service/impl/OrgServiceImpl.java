package org.birdbro.lumen.auth.service.impl;

import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.auth.dto.CreateOrgRequest;
import org.birdbro.lumen.auth.dto.UpdateOrgRequest;
import org.birdbro.lumen.auth.entity.OrgInfo;
import org.birdbro.lumen.auth.mapper.OrgInfoMapper;
import org.birdbro.lumen.auth.service.OrgService;
import org.birdbro.lumen.auth.service.OrgTreeService;
import org.springframework.stereotype.Service;

/**
 * Org management implementation (auth-rbac-foundation task 4.1). The tree integrity
 * checks live here because {@code org_info} has NO self-FK (design D2): a non-root
 * {@code upId} is verified against a live parent before the row is written - otherwise
 * the API could silently plant an orphan the {@code TreeUtils} assembly would drop.
 *
 * <p>Error-code mapping (recorded decision): a referenced-but-missing PARENT at
 * create/re-parent time is a request-parameter error ({@code A0400}, the request asked
 * for an impossible placement); an update on a row that is no longer live is
 * {@code B0031} (数据更新失败，记录可能已被删除). Logical delete is a bare
 * {@code deleteById} - {@code @TableLogic} makes it {@code UPDATE deleted = 1}.</p>
 *
 * <p>The audit identity ({@code create_by}/{@code update_by}) is NOT set here on
 * purpose: the copied {@code MetaObjectConfig} fill owns it, fed by the framework
 * {@code UserHeaderBridgeFilter} from the JWT principal.</p>
 *
 * @author birdbro
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final int ROOT_PARENT_ID = 0;

    private final OrgInfoMapper orgInfoMapper;
    private final OrgTreeService orgTreeService;

    public OrgServiceImpl(OrgInfoMapper orgInfoMapper, OrgTreeService orgTreeService) {
        this.orgInfoMapper = orgInfoMapper;
        this.orgTreeService = orgTreeService;
    }

    @Override
    public Integer create(CreateOrgRequest request) {
        Integer upId = request.getUpId();
        Integer level = request.getLevel();
        if (upId == null || upId == ROOT_PARENT_ID) {
            if (level == null) {
                level = 1; // a root sits at level 1
            }
        } else {
            OrgInfo parent = requireLiveOrg(upId, "parent org does not exist");
            if (level == null) {
                level = parent.getLevel() + 1; // one deeper than the parent
            }
        }

        OrgInfo org = new OrgInfo();
        BeanUtil.copyProperties(request, org, true);
        org.setLevel(level); // derived above (root/parent defaulting), not the raw request value
        orgInfoMapper.insert(org);
        return org.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateOrgRequest request) {
        requireLiveOrg(id, "org does not exist");

        Integer upId = request.getUpId();
        if (upId != null && upId != ROOT_PARENT_ID) {
            if (upId.equals(id)) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "a org cannot be re-parented under itself");
            }
            requireLiveOrg(upId, "new parent org does not exist");
        }

        // Null fields are skipped by the MyBatis-Plus NOT-NULL update strategy, so the
        // request is a true partial update.
        OrgInfo patch = new OrgInfo();
        BeanUtil.copyProperties(request, patch, true);
        patch.setId(id); // path param, not a request field
        orgInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        // @TableLogic turns this into UPDATE deleted = 1 (EntityBase contract).
        orgInfoMapper.deleteById(id);
        return Boolean.TRUE;
    }

    @Override
    public List<OrgInfo> tree() {
        return orgTreeService.orgTree();
    }

    /**
     * Loads a live org or rejects with {@code A0400} (a missing referenced org is a
     * bad request, not a server fault). {@code @TableLogic} appends {@code deleted = 0},
     * so a logically deleted org counts as missing.
     */
    private OrgInfo requireLiveOrg(Integer id, String detail) {
        OrgInfo org = orgInfoMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    detail + ": org id " + id);
        }
        return org;
    }
}

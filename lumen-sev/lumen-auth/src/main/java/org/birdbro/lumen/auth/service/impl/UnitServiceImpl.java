package org.birdbro.lumen.auth.service.impl;

import java.util.List;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.auth.dto.CreateUnitRequest;
import org.birdbro.lumen.auth.dto.UpdateUnitRequest;
import org.birdbro.lumen.auth.entity.UnitInfo;
import org.birdbro.lumen.auth.mapper.UnitInfoMapper;
import org.birdbro.lumen.auth.service.UnitService;
import org.birdbro.lumen.auth.service.UnitTreeService;
import org.springframework.stereotype.Service;

/**
 * Unit management implementation (auth-rbac-foundation task 4.1). The tree integrity
 * checks live here because {@code unit_info} has NO self-FK (design D2): a non-root
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
 * purpose: the copied {@code MetaObjectConfig} fill owns it, fed by the lumen
 * {@code UserHeaderBridgeFilter} from the JWT principal.</p>
 *
 * @author birdbro
 */
@Service
public class UnitServiceImpl implements UnitService {

    private static final int ROOT_PARENT_ID = 0;

    private final UnitInfoMapper unitInfoMapper;
    private final UnitTreeService unitTreeService;

    public UnitServiceImpl(UnitInfoMapper unitInfoMapper, UnitTreeService unitTreeService) {
        this.unitInfoMapper = unitInfoMapper;
        this.unitTreeService = unitTreeService;
    }

    @Override
    public Integer create(CreateUnitRequest request) {
        Integer upId = request.getUpId();
        Integer level = request.getLevel();
        if (upId == null || upId == ROOT_PARENT_ID) {
            if (level == null) {
                level = 1; // a root sits at level 1
            }
        } else {
            UnitInfo parent = requireLiveUnit(upId, "parent unit does not exist");
            if (level == null) {
                level = parent.getLevel() + 1; // one deeper than the parent
            }
        }

        UnitInfo unit = new UnitInfo();
        unit.setUpId(upId);
        unit.setLevel(level);
        unit.setType(request.getType());
        unit.setName(request.getName());
        unitInfoMapper.insert(unit);
        return unit.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateUnitRequest request) {
        requireLiveUnit(id, "unit does not exist");

        Integer upId = request.getUpId();
        if (upId != null && upId != ROOT_PARENT_ID) {
            if (upId.equals(id)) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "a unit cannot be re-parented under itself");
            }
            requireLiveUnit(upId, "new parent unit does not exist");
        }

        // Null fields are skipped by the MyBatis-Plus NOT-NULL update strategy, so the
        // request is a true partial update.
        UnitInfo patch = new UnitInfo();
        patch.setId(id);
        patch.setUpId(upId);
        patch.setLevel(request.getLevel());
        patch.setType(request.getType());
        patch.setName(request.getName());
        unitInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        // @TableLogic turns this into UPDATE deleted = 1 (EntityBase contract).
        unitInfoMapper.deleteById(id);
        return Boolean.TRUE;
    }

    @Override
    public List<UnitInfo> tree() {
        return unitTreeService.unitTree();
    }

    /**
     * Loads a live unit or rejects with {@code A0400} (a missing referenced unit is a
     * bad request, not a server fault). {@code @TableLogic} appends {@code deleted = 0},
     * so a logically deleted unit counts as missing.
     */
    private UnitInfo requireLiveUnit(Integer id, String detail) {
        UnitInfo unit = unitInfoMapper.selectById(id);
        if (unit == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    detail + ": unit id " + id);
        }
        return unit;
    }
}

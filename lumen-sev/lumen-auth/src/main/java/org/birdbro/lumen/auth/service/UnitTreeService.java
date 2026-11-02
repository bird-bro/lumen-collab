package org.birdbro.lumen.auth.service;

import java.util.List;

import org.birdbro.common.utils.TreeUtils;
import org.birdbro.lumen.auth.entity.UnitInfo;
import org.birdbro.lumen.auth.mapper.UnitInfoMapper;
import org.springframework.stereotype.Service;

/**
 * Assembles the flat {@code unit_info} table into the hierarchical unit tree
 * (auth-rbac-foundation task 2.3, design D2). The schema carries no self-FK - root rows
 * use {@code upId = 0} (the {@code TreeUtils} rootParentValue convention) and nesting is
 * resolved here, so a second root (cloud product) needs no schema change.
 *
 * <p>{@code TreeUtils.buildTree} semantics: roots are the rows with {@code upId = 0};
 * orphans (parent missing from the set) are dropped; leaf children stay {@code null}.</p>
 *
 * @author birdbro
 */
@Service
public class UnitTreeService {

    private final UnitInfoMapper unitInfoMapper;

    public UnitTreeService(UnitInfoMapper unitInfoMapper) {
        this.unitInfoMapper = unitInfoMapper;
    }

    /**
     * Loads every live unit ({@code deleted = 0} is applied by the copied EntityBase
     * {@code @TableLogic}) and assembles the tree.
     *
     * @return the root units with their nested children
     */
    public List<UnitInfo> unitTree() {
        return build(unitInfoMapper.selectList(null));
    }

    /**
     * Assembles a tree from an arbitrary flat unit list (also the seam for tests and
     * future scoped queries).
     *
     * @param units flat unit rows
     * @return the root units with their nested children
     */
    public List<UnitInfo> build(List<UnitInfo> units) {
        return TreeUtils.buildTree(units,
                UnitInfo::getId, UnitInfo::getUpId, 0,
                UnitInfo::getChildren, UnitInfo::setChildren);
    }
}

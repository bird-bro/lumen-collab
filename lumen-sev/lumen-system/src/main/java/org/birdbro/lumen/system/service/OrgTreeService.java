package org.birdbro.lumen.system.service;

import java.util.List;
import java.util.stream.Collectors;

import org.birdbro.common.utils.TreeUtils;
import org.birdbro.lumen.system.entity.OrgInfo;
import org.birdbro.lumen.system.mapper.OrgInfoMapper;
import org.springframework.stereotype.Service;

/**
 * Assembles the flat {@code org_info} table into the hierarchical org tree
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
public class OrgTreeService {

    private final OrgInfoMapper orgInfoMapper;

    public OrgTreeService(OrgInfoMapper orgInfoMapper) {
        this.orgInfoMapper = orgInfoMapper;
    }

    /**
     * Loads every live org ({@code deleted = 0} is applied by the copied EntityBase
     * {@code @TableLogic}) and assembles the tree.
     *
     * @return the root orgs with their nested children
     */
    public List<OrgInfo> orgTree() {
        return orgTree(null);
    }

    /**
     * Loads and assembles the tree, then optionally narrows each root's campus
     * children by display name. The root remains present for the hospital card;
     * a campus code is deliberately not searchable.
     *
     * @param name optional case-insensitive campus-name substring
     * @return the root orgs with their (optionally filtered) children
     */
    public List<OrgInfo> orgTree(String name) {
        List<OrgInfo> tree = build(orgInfoMapper.selectList(null));
        if (name == null || name.isBlank()) {
            return tree;
        }

        String needle = name.trim().toLowerCase();
        for (OrgInfo root : tree) {
            List<OrgInfo> children = root.getChildren();
            if (children == null) {
                continue;
            }
            List<OrgInfo> matchingChildren = children.stream()
                    .filter(campus -> campus.getName() != null
                            && campus.getName().toLowerCase().contains(needle))
                    .collect(Collectors.toList());
            root.setChildren(matchingChildren.isEmpty() ? null : matchingChildren);
        }
        return tree;
    }

    /**
     * Assembles a tree from an arbitrary flat org list (also the seam for tests and
     * future scoped queries).
     *
     * @param orgs flat org rows
     * @return the root orgs with their nested children
     */
    public List<OrgInfo> build(List<OrgInfo> orgs) {
        return TreeUtils.buildTree(orgs,
                OrgInfo::getId, OrgInfo::getUpId, 0,
                OrgInfo::getChildren, OrgInfo::setChildren);
    }
}

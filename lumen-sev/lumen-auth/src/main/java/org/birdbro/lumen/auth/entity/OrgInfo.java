package org.birdbro.lumen.auth.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.common.core.base.EntityBase;

/**
 * Org-tree node ({@code org_info}, auth-rbac-foundation task 2.3, design D2): hospital
 * roots and their sub-orgs form the hierarchical org tree. {@code upId = 0} marks a
 * root (the {@code TreeUtils} rootParentValue convention); the schema deliberately
 * carries NO self-FK - tree integrity is service-level ({@code OrgTreeService}).
 *
 * <p>Extends the copied {@link EntityBase} ({@code deleted}/{@code createBy}/
 * {@code createTime}/{@code updateBy}/{@code updateTime}) - audit fields are NOT
 * redeclared. The entity itself is the tree node: {@code children} is a query-time
 * field only ({@code exist = false}).</p>
 *
 * @author birdbro
 */
@TableName("org_info")
@Data
@EqualsAndHashCode(callSuper = true)
public class OrgInfo extends EntityBase {

    /** Primary key, database auto-increment (design D1). */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Parent org id: 0 = root (TreeUtils rootParentValue convention). */
    private Integer upId;

    /** Depth in the org tree: the hospital root sits at level 1. */
    private Integer level;

    /** Org type (lumen-owned): 0 = hospital root. */
    private Integer type;

    /** Display name. */
    private String name;

    /** Children in the assembled tree - NOT a column (org_info has no self-FK). */
    @TableField(exist = false)
    private List<OrgInfo> children;
}

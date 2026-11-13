package org.birdbro.lumen.manage.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.birdbro.common.core.base.EntityBase;

/**
 * Controlled org-tree node ({@code org_info}, org-management-redesign): one hospital
 * root plus campus children. {@code upId = 0} marks the hospital root (the
 * {@code TreeUtils} rootParentValue convention); the schema deliberately carries no
 * self-FK - tree integrity is service-level ({@code OrgService}).
 *
 * <p>Extends the copied {@link EntityBase} ({@code deleted}/{@code createBy}/
 * {@code createTime}/{@code updateBy}/{@code updateTime}) - audit fields are NOT
 * redeclared. {@code children} is a query-time field only ({@code exist = false}).</p>
 *
 * @author birdbro
 */
@TableName("org_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrgInfo extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Parent org id: 0 = hospital root (TreeUtils rootParentValue convention). */
    private Integer upId;

    /** Depth in the controlled tree: the hospital root sits at level 1. */
    private Integer level;

    /** Org type: 0 = hospital root, 1 = campus. */
    private Integer type;

    /** Stable organization code, unique among live sibling org nodes. */
    private String code;

    /** Display name. */
    private String name;

    /** Abbreviated display name. */
    private String shortName;

    /** Lifecycle status: 1 = enabled, 0 = disabled. */
    private Integer status;

    /** Province administrative-division code. */
    private String provinceCode;

    /** City administrative-division code. */
    private String cityCode;

    /** District administrative-division code. */
    private String districtCode;

    /** Street address. */
    private String address;

    /** Contact person. */
    private String contact;

    /** Contact phone. */
    private String phone;

    /** Contact email. */
    private String email;

    /** Optional logo URL (no upload endpoint in this change). */
    private String logoUrl;

    /** Console display order. */
    private Integer sort;

    /** Marks the migrated default campus; {@code 1 = default}. */
    private Integer isDefault;

    /** Campuses in the assembled tree - NOT a column (org_info has no self-FK). */
    @TableField(exist = false)
    private List<OrgInfo> children;
}

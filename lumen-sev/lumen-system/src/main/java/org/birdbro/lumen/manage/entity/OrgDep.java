package org.birdbro.lumen.manage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.birdbro.common.core.base.EntityBase;

/**
 * OrgDep ({@code org_dep}, org-management-redesign): a medical department anchored to a
 * live, enabled campus. {@code code} remains the stable integration identifier and is
 * immutable after creation; it is unique within the owning campus among live rows and may
 * repeat across campuses.
 *
 * @author birdbro
 */
@TableName("org_dep")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrgDep extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Owning campus (org_info.id, type = 1). */
    private Integer orgId;

    /** Stable department code (radiology/us/endo...), immutable after creation. */
    private String code;

    /** Display name. */
    private String name;

    /** Lifecycle status: 1 = enabled, 0 = disabled. */
    private Integer status;
}

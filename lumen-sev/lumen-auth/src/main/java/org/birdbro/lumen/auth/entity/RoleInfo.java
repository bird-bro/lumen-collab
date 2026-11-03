package org.birdbro.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.common.core.base.EntityBase;

/**
 * RoleInfo ({@code role}, auth-rbac-foundation task 2.3). {@code name} is unique among live
 * rows (partial unique index). {@code type} follows the copied {@code RoleConstants}
 * lineage: 10 system / 11 business.
 *
 * @author birdbro
 */
@TableName("role_info")
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleInfo extends EntityBase {

    /** Primary key, database auto-increment (design D1). */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** RoleInfo name, unique among live rows. */
    private String name;

    /** RoleInfo type (copied RoleConstants lineage): 10 system / 11 business. */
    private Integer type;

    /** Free-text remark. */
    private String remark;
}

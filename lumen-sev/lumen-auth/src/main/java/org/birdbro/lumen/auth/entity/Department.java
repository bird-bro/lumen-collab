package org.birdbro.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.common.core.base.EntityBase;

/**
 * Department ({@code department}, auth-rbac-foundation task 2.3, design D2): medical
 * workflows anchor to the org tree through {@code unitId}, keeping medical attributes
 * out of the org tree. {@code code} (radiology / us / endo) is the stable identifier
 * lumen-integration routes DICOM/HL7 by - unique among live rows (partial unique index,
 * so a logically deleted department does not block re-creating its code).
 *
 * @author birdbro
 */
@TableName("department")
@Data
@EqualsAndHashCode(callSuper = true)
public class Department extends EntityBase {

    /** Primary key, database auto-increment (design D1). */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Owning org-tree node (FK unit_info.id). */
    private Integer unitId;

    /** Stable department code (radiology/us/endo), unique among live rows. */
    private String code;

    /** Display name. */
    private String name;
}

package org.birdbro.lumen.auth.entity;

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
 * User-role binding ({@code user_role}, auth-rbac-foundation task 2.3). The pair
 * ({@code userId}, {@code roleId}) is unique among live rows (partial unique index).
 *
 * @author birdbro
 */
@TableName("user_role")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRole extends EntityBase {

    /** Primary key, database auto-increment (design D1). */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Bound user (FK user_info.id). */
    private Integer userId;

    /** Granted role (FK role.id). */
    private Integer roleId;
}

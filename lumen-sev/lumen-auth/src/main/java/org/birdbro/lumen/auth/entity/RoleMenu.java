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
 * Role-menu binding ({@code role_menu}, auth-rbac-foundation task 2.3): the page/button
 * permission grants. The pair ({@code roleId}, {@code menuId}) is unique among live rows
 * (partial unique index).
 *
 * @author birdbro
 */
@TableName("role_menu")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoleMenu extends EntityBase {

    /** Primary key, database auto-increment (design D1). */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Granting role (FK role.id). */
    private Integer roleId;

    /** Granted menu node (FK menu_info.id). */
    private Integer menuId;
}

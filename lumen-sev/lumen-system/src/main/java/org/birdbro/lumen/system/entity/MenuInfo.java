package org.birdbro.lumen.system.entity;

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
 * Menu node ({@code menu_info}, auth-rbac-foundation task 2.3, design D5): an
 * {@code upId} tree of directories / menus / buttons (the copied {@code MenuTree} POJO
 * lineage). Column set trimmed to lumen needs (design D5 resolution): no
 * query/redirect/app/breadcrumb/action/is_cache/is_frame columns.
 *
 * @author birdbro
 */
@TableName("menu_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MenuInfo extends EntityBase {

    /** Primary key, database auto-increment (design D1). */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Parent menu id: 0 = top level (TreeUtils rootParentValue convention). */
    private Integer upId;

    /** Node type: M directory / C menu / F button (MenuTree POJO lineage). */
    private String type;

    /** Display title. */
    private String title;

    /** Permission string (e.g. system:user:list); null for pure navigation nodes. */
    private String perms;

    /** Frontend route path. */
    private String path;

    /** Frontend component. */
    private String component;

    /** Icon. */
    private String icon;

    /** Sort order among siblings. */
    private Integer sort;

    /** Visible flag: 1 = visible (default), 0 = hidden. */
    private Integer visible;
}

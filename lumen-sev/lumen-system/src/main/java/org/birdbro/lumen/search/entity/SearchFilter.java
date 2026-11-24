package org.birdbro.lumen.search.entity;

import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.birdbro.common.core.base.EntityBase;
import org.birdbro.lumen.search.mapper.SystemJsonbTypeHandler;

/**
 * One saved search filter (us-registration-redesign-api tasks 5.1-5.2, design D3;
 * 拍板 07-A): the workbench-agnostic persisted search - the owning uid, the page
 * discriminator, a display name, and the query jsonb whose SHAPE belongs to each
 * workbench (passed through verbatim, never interpreted here). The default flag is
 * the per-page auto-apply marker - ONE default per (uid, page_key) among live rows
 * (the partial unique index backstops the endpoint's full-replace).
 *
 * @author birdbro
 */
@TableName(value = "search_filter", autoResultMap = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SearchFilter extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 归属用户 (user_info.uid 逻辑引用, Integer - the house id ruling) - the row is
     * invisible to every other uid. */
    private Integer uid;

    /** 工作台页区分键 (e.g. us-registration - one namespace per page). */
    private String pageKey;

    /** 筛选项名称. */
    private String name;

    /** 过滤条件原文 (jsonb 透传 - the shape contract belongs to each workbench). */
    @TableField(typeHandler = SystemJsonbTypeHandler.class)
    private Map<String, Object> query;

    /** 页面默认筛选 (0/1；每 uid+page_key 至多一个 live). */
    private Integer isDefault;
}

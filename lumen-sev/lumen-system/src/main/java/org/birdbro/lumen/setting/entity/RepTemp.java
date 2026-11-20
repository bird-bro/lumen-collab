package org.birdbro.lumen.setting.entity;

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
import org.birdbro.lumen.setting.mapper.RepTempContentJsonbTypeHandler;

/**
 * RepTemp ({@code rep_temp}, exam-setting-api): one node of the report-template
 * knowledge base - the RCP single-tree model (flag 0 目录 / 1 模板) scoped per
 * 检查类型. {@code type} 1 公共 / 2 个人 splits the scopes: public rows carry
 * {@code owner_uid = NULL} and are perms-gated; private rows bind {@code owner_uid}
 * (server-side, ignoring the request body) and only the owner may update/delete them.
 * Templates (flag=1) carry the 影像学表现/影像学意见 phrase pair the report writer
 * consumes; directories (flag=0) stay content-free. A template row is EITHER prose
 * ({@code content} NULL - the phrase pair) OR structured ({@code content} non-null -
 * the D2 section model; the two prose columns are stored NULL and not read, D1
 * exclusivity enforced service-side). Scope 字段留位: a future 科室
 * scope adds a type value without a migration (coordination ruling 5).
 *
 * @author birdbro
 */
@TableName(value = "rep_temp", autoResultMap = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RepTemp extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 可见范围 (1 公共 / 2 个人; room left for a future 科室 scope). */
    private Integer type;

    /** 归属人 (user_info.id; NULL for public rows, the creating user for private). */
    private Integer ownerUid;

    /** 检查类型 (dict 1024 value). */
    private String modality;

    /** 节点类型 (0 目录 / 1 模板). */
    private Integer flag;

    /** 上级节点 (rep_temp.id, nullable at the tree root). */
    private Integer upId;

    /** 名称（目录名或模板名）. */
    private String name;

    /** 影像学表现（模板内容，目录行保持空）. */
    private String finding;

    /** 影像学意见（模板内容，目录行保持空）. */
    private String impression;

    /** 结构化分节内容（jsonb；D1 互斥——content 非空 = 分节模板，finding/impression 存 NULL 不读；目录行保持空）. */
    @TableField(typeHandler = RepTempContentJsonbTypeHandler.class)
    private RepTempContent content;
}

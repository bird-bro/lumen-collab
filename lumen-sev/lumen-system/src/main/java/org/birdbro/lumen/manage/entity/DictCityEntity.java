package org.birdbro.lumen.manage.entity;

import java.io.Serializable;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * Administrative-divisions reference node ({@code public.dict_city},
 * dict-city-reference task 1.2, design D2): the 13-column 随访 lineage shape seeded by
 * V4 - 中国 root (level 0), provinces (35), cities (373), districts (3342). {@code
 * upId = 0} marks the root (the {@code TreeUtils} rootParentValue convention); the
 * schema deliberately carries NO self-FK - tree integrity is service-level
 * ({@code DictCityService}).
 *
 * <p>Static reference data with NO write path (design D3): the entity deliberately
 * does NOT extend {@code EntityBase} - the lineage table carries no audit block, and
 * updates arrive only as later seed migrations (V5+). The {@code Long} id/upId keep
 * the lineage identity (the recorded deviation from the Integer convention) and let
 * {@code TreeUtils} match parent to child in one key type.</p>
 *
 * @author birdbro
 */
@TableName("dict_city")
@Data
public class DictCityEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Primary key, lineage id (e.g. 100000 中国) - the recorded Long deviation. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** Parent id: 0 = the 中国 root (TreeUtils rootParentValue convention). */
    private Long upId;

    /** Lineage division code (6-digit GB codes; the root carries '0'). */
    private String code;

    /** Display name (e.g. 北京市 / 东城区). */
    private String name;

    /** Short display name (e.g. 北京 / 东城). */
    private String abbreviation;

    /** Depth: 0 root / 1 province / 2 city / 3 district. */
    private Integer level;

    /** Area phone code (nullable above city level). */
    private String cityCode;

    /** Zip code (nullable above district level). */
    private String zipCode;

    /** Full path of ancestor names, comma-joined (e.g. 中国,北京,北京市,东城区). */
    private String merger;

    /** Lineage pinyin. */
    private String pinyin;

    /** Lineage longitude (kept verbatim from the lineage export). */
    private String lng;

    /** Lineage latitude (kept verbatim from the lineage export). */
    private String lat;

    /** Lineage initial letter (reserved, NULL in the current snapshot). */
    private String initial;

    /** Children in the assembled tree - NOT a column (dict_city has no self-FK). */
    @TableField(exist = false)
    private List<DictCityEntity> children;
}

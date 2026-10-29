package org.birdbro.common.Constants;

import java.util.List;

/**
 * 机构模块常量
 *
 * @author birdbro
 * @since 2026-08-27
 */
public class OrgConstants {

    /**
     * 平台机构 OID：该机构用户的数据范围为全部机构（与前端 151000「查全部」约定一致）
     */
    public static final Integer PLATFORM_OID = 151000;

    /**
     * OX 开放接口（{@code getPageOx}）固定排除的机构类型（字典值，OX 侧约定）
     */
    public static final List<Integer> OX_EXCLUDED_TYPES = List.of(100710, 100705);

    /**
     * OX 开放接口（{@code getPageOx}）type 入参约定值（字典值，OX 侧约定）：
     * 传 {@link #OX_TYPE_SELF}(100711) = 仅查 type=100711 的机构；
     * 传 {@link #OX_TYPE_OTHERS}(100700) = 查 type!=100711 的机构；不传 = 不过滤
     */
    public static final Integer OX_TYPE_SELF = 100711;
    public static final Integer OX_TYPE_OTHERS = 100700;
}

package org.birdbro.lumen.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.birdbro.lumen.auth.entity.MenuInfo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * MyBatis-Plus mapper for {@code menu_info} (auth-rbac-foundation task 2.3). Declared
 * with {@code @Mapper} to match the copied framework's {@code LogInfoMapper} precedent;
 * AuthApplication's {@code @MapperScan("org.birdbro.**.mapper")} covers it regardless.
 *
 * @author birdbro
 */
@Mapper
public interface MenuInfoMapper extends BaseMapper<MenuInfo> {

    /**
     * The live menu rows reachable through the user's roles - the same
     * {@code user_role -> role_menu -> menu_info} walk as the perms query
     * (auth-rbac-foundation task 3.1), feeding {@code MenuTreeService#build}. {@code
     * deleted = 0} is enforced on EVERY joined table (Batch-2 review handoff).
     *
     * @param userId the user to collect menus for
     * @return the distinct live menus granted through the user's roles
     */
    @Select("SELECT DISTINCT m.* FROM user_role ur "
            + "JOIN role_menu rm ON rm.role_id = ur.role_id AND rm.deleted = 0 "
            + "JOIN menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
            + "WHERE ur.user_id = #{userId} AND ur.deleted = 0")
    List<MenuInfo> selectMenusByUserId(@Param("userId") Integer userId);
}

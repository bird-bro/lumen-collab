package org.birdbro.lumen.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.birdbro.lumen.system.entity.RoleMenu;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * MyBatis-Plus mapper for {@code role_menu} (auth-rbac-foundation task 2.3). Declared
 * with {@code @Mapper} to match the copied framework's {@code LogInfoMapper} precedent;
 * AuthApplication's {@code @MapperScan("org.birdbro.**.mapper")} covers it regardless.
 *
 * @author birdbro
 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenu> {

    /**
     * The DISTINCT permission strings of the user's roles - the
     * {@code user_role -> role_menu -> menu_info} walk (auth-rbac-foundation task 3.1).
     * {@code deleted = 0} is enforced on EVERY joined table (Batch-2 review handoff: the
     * V2 seed rows carry the default); blank perms never leave the database.
     *
     * @param userId the user to collect permissions for
     * @return the distinct non-blank perms strings (empty list when the user owns no menus)
     */
    @Select("SELECT DISTINCT m.perms FROM user_role ur "
            + "JOIN role_menu rm ON rm.role_id = ur.role_id AND rm.deleted = 0 "
            + "JOIN menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
            + "WHERE ur.user_id = #{userId} AND ur.deleted = 0 "
            + "AND m.perms IS NOT NULL AND m.perms <> ''")
    List<String> selectPermsByUserId(@Param("userId") Integer userId);
}

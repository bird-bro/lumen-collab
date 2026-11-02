package org.birdbro.lumen.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.birdbro.lumen.auth.entity.Role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * MyBatis-Plus mapper for {@code role} (auth-rbac-foundation task 2.3). Declared with
 * {@code @Mapper} to match the copied framework's {@code LogInfoMapper} precedent;
 * AuthApplication's {@code @MapperScan("org.birdbro.**.mapper")} covers it regardless.
 *
 * @author birdbro
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * The DISTINCT live role names of the user - the {@code user_role -> role} walk for
     * the profile DTO (auth-rbac-foundation task 3.1). {@code deleted = 0} is enforced
     * on BOTH joined tables (Batch-2 review handoff).
     *
     * @param userId the user to collect role names for
     * @return the distinct live role names (empty list when the user owns no roles)
     */
    @Select("SELECT DISTINCT r.name FROM user_role ur "
            + "JOIN role r ON r.id = ur.role_id AND r.deleted = 0 "
            + "WHERE ur.user_id = #{userId} AND ur.deleted = 0")
    List<String> selectRoleNamesByUserId(@Param("userId") Integer userId);
}

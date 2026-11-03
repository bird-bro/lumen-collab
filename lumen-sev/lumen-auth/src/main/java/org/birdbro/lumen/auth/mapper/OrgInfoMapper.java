package org.birdbro.lumen.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.auth.entity.OrgInfo;

/**
 * MyBatis-Plus mapper for {@code org_info} (auth-rbac-foundation task 2.3). Declared
 * with {@code @Mapper} to match the copied framework's {@code LogInfoMapper} precedent;
 * AuthApplication's {@code @MapperScan("org.birdbro.**.mapper")} covers it regardless.
 *
 * @author birdbro
 */
@Mapper
public interface OrgInfoMapper extends BaseMapper<OrgInfo> {
}

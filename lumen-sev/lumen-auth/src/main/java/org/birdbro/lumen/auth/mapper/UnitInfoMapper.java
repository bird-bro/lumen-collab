package org.birdbro.lumen.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.auth.entity.UnitInfo;

/**
 * MyBatis-Plus mapper for {@code unit_info} (auth-rbac-foundation task 2.3). Declared
 * with {@code @Mapper} to match the copied framework's {@code LogInfoMapper} precedent;
 * AuthApplication's {@code @MapperScan("org.birdbro.**.mapper")} covers it regardless.
 *
 * @author birdbro
 */
@Mapper
public interface UnitInfoMapper extends BaseMapper<UnitInfo> {
}

package org.birdbro.lumen.us.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.us.entity.UsRegistrationExt;

/**
 * MyBatis-Plus mapper for {@code us_registration_ext} (us-station-registration-sev task
 * 3.2, design D7): the hook writes it inside the registration transaction; the us query
 * path reads it 1:1 against the order ids. No custom SQL - the aggregate columns are the
 * whole surface.
 *
 * @author birdbro
 */
@Mapper
public interface UsRegistrationExtMapper extends BaseMapper<UsRegistrationExt> {
}

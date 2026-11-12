package org.birdbro.lumen.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.system.entity.OrgDevice;

/**
 * MyBatis-Plus mapper for {@code org_device} (org-device-room-api task 1.2). Plain
 * BaseMapper - the service-layer duplicate/referential checks run on wrappers, and the
 * registry carries no cross-table read of its own (the JOIN lives on the room side).
 *
 * @author birdbro
 */
@Mapper
public interface OrgDeviceMapper extends BaseMapper<OrgDevice> {
}

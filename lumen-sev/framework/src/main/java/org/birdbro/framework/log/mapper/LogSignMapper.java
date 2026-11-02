package org.birdbro.framework.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.birdbro.framework.log.entity.LogSignEntity;
import org.birdbro.framework.log.request.LogRequest;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author birdbro
 * @since 2024-11-01
 */
@Mapper
public interface LogSignMapper extends BaseMapper<LogSignEntity> {


    IPage<LogSignEntity> getLogSignPage(IPage<LogSignEntity> page, @Param("req") LogRequest req);



    void insertSign(@Param("entity") LogSignEntity entity);
}

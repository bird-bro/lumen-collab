package org.birdbro.framework.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.request.LogRequest;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author birdbro
 * @since 2024-05-07
 */
@Mapper
public interface LogInfoMapper extends BaseMapper<LogInfoEntity> {


    IPage<LogInfoEntity> getLogInfoPage(IPage<LogInfoEntity> page, @Param("req") LogRequest req);


    void insertInfo (@Param("entity") LogInfoEntity entity);
}

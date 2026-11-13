package org.birdbro.lumen.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import org.birdbro.lumen.manage.entity.DictCityEntity;

/**
 * Read-only mapper for the administrative-divisions reference table
 * ({@code public.dict_city}, dict-city-reference task 1.2). No insert/update/delete
 * methods are added - the table's only writers are the Flyway seed migrations
 * (design D3).
 *
 * @author birdbro
 */
@Mapper
public interface DictCityMapper extends BaseMapper<DictCityEntity> {

}

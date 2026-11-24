package org.birdbro.lumen.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.birdbro.lumen.search.entity.SearchFilter;

/**
 * MyBatis-Plus mapper for the saved-search table (us-registration-redesign-api task
 * 5.2). BASE-MAPPER ONLY - every access is uid-scoped at the service layer; the
 * partial unique default index is the one-default backstop.
 *
 * @author birdbro
 */
public interface SearchFilterMapper extends BaseMapper<SearchFilter> {
}

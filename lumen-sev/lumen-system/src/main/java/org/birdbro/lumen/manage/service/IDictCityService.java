package org.birdbro.lumen.manage.service;

import java.util.List;

import org.birdbro.lumen.manage.entity.DictCityEntity;

/**
 * Read-only queries over the administrative-divisions reference table
 * ({@code public.dict_city}, dict-city-reference tasks 1.2, design D3): the tree
 * assembly plus the flat level/parent list. NO mutation methods - the seed migrations
 * are the data source.
 *
 * @author birdbro
 */
public interface IDictCityService {

    /**
     * Loads every division and assembles the hierarchical tree (roots = the 中国 rows
     * with {@code upId = 0}, children nested by {@code upId}).
     *
     * @return the root divisions with their nested children
     */
    List<DictCityEntity> tree();

    /**
     * Flat division list with both filters optional (null = skip the filter).
     *
     * @param level depth filter (0 root / 1 province / 2 city / 3 district), or null
     * @param upId  parent filter (e.g. 110100 = 北京市's districts), or null
     * @return the matching divisions ordered by lineage id
     */
    List<DictCityEntity> list(Integer level, Integer upId);
}

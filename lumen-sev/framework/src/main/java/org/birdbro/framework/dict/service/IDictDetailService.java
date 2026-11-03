package org.birdbro.framework.dict.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.birdbro.common.core.pojo.IdList;
import org.birdbro.framework.dict.entity.DictDetailEntity;
import org.birdbro.framework.dict.pojo.request.DetailQuery;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 字典数据 服务类
 * </p>
 *
 * @author birdbro
 * @since 2024-06-04
 */
public interface IDictDetailService extends IService<DictDetailEntity> {


    /**
     * 修改字典状态
     *
     * @author: birdbro
     * @since: 2023-03-16 16:46
     * @param: id：字典ID；enable：状态码
     * @return:
     **/
    boolean modify(Integer id, Integer enable);


    /**
     * 获取字典MAP
     *
     * @author: birdbro
     * @since: 2023-03-28 13:17
     * @param: ids
     * @return: map
     **/
    Map<Integer, List<DictDetailEntity>> mapByCatalIds(IdList query);


    /**
     * 目录下启用字典项的 code→value 映射（文案实时读字典，替代硬编码枚举）；
     * enable=启用，@TableLogic 滤已删，sort 升序；重复 code 保留首个
     *
     * @author: birdbro
     * @since: 2026-10-23
     * @param: catalId 目录ID（dict_catal.id）
     * @return: code → value
     **/
    Map<Integer, String> valueMapByCatalId(Integer catalId);


    /**
     * 查询字典
     *
     * @author: birdbro
     * @since: 2024-08-12 8:59
     * @param:
     * @return:
     **/
    IPage<DictDetailEntity> getPage(DetailQuery query);
}

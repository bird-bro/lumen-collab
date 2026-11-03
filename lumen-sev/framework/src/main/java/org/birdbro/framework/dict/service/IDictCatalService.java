package org.birdbro.framework.dict.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.birdbro.framework.dict.entity.DictCatalEntity;
import org.birdbro.framework.dict.pojo.request.CatalQuery;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author birdbro
 * @since 2024-05-07
 */
public interface IDictCatalService extends IService<DictCatalEntity> {


    /**
     * 修改目录状态
     *
     * @author: birdbro
     * @since: 2023-03-16 16:46
     * @param: id：目录ID；enable：状态码
     * @return:
     **/
    boolean modify(Integer id, Integer enable);


    /**
     * 查询字典目录
     *
     * @author: birdbro
     * @since: 2024-08-12 8:59
     * @param:
     * @return:
     **/
    IPage<DictCatalEntity> getPage(CatalQuery query);

}

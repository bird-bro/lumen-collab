package org.birdbro.framework.log.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.entity.LogSignEntity;
import org.birdbro.framework.log.mapper.LogSignMapper;
import org.birdbro.framework.log.request.LogRequest;
import org.birdbro.framework.log.service.ILogSignService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author birdbro
 * @since 2024-11-01
 */
@Slf4j
@Service
public class LogSignServiceImpl extends ServiceImpl<LogSignMapper, LogSignEntity> implements ILogSignService {


    @Override
    public boolean insert(LogSign sign) {
        LogSignEntity entity = LogSignEntity.builder()
                .dateYm(Integer.parseInt(DateUtils.dateTimeNow("yyyyMM")))
                .app(Constants.APP_CODE.toString())
                .build();
        BeanUtil.copyProperties(sign, entity,true);
        baseMapper.insertSign(entity);
        return true;
    }

    @Override
    public IPage<LogSignEntity> getLogSignPage(LogRequest req) {
        IPage<LogSignEntity> page = new Page<>(req.getPageNum(),req.getPageSize());
        return baseMapper.getLogSignPage(page,req);
    }


    /**
     * 实体无 @TableLogic（拷贝件只在 XML 里裸 SQL 过滤），此处用同款 deleted = 0 原文条件；
     * account 过滤用 StrUtil.isNotBlank（空串视同未传）。select 排除 token 列（设计 D7：
     * 凭证材料不出服务层）——查询结果里 token 恒为 null，实体 token 字段配
     * {@code @JsonInclude(NON_NULL)} 后响应 JSON 不再出现该字段；拷贝 XML 路径
     * （getLogSignPage）的非空 token 序列化不受影响。
     */
    @Override
    public IPage<LogSignEntity> getPage(Integer pageNum, Integer pageSize, String account, Integer uid, Integer dateYm) {
        Page<LogSignEntity> page = new Page<>(
                pageNum == null ? 1 : Math.max(1, pageNum),
                pageSize == null ? 20 : Math.max(1, pageSize));
        return this.page(page, Wrappers.<LogSignEntity>lambdaQuery()
                .select(LogSignEntity.class, field -> !"token".equals(field.getColumn()))
                .eq(StrUtil.isNotBlank(account), LogSignEntity::getAccount, account)
                .eq(uid != null, LogSignEntity::getUid, uid)
                .eq(dateYm != null, LogSignEntity::getDateYm, dateYm)
                .apply("deleted = 0")
                .orderByDesc(LogSignEntity::getActionTime)
                .orderByDesc(LogSignEntity::getId)
        );
    }
}

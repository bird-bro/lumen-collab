package org.birdbro.framework.log.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.Constants.LogTypes;
import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.mapper.LogInfoMapper;
import org.birdbro.framework.log.request.LogRequest;
import org.birdbro.framework.log.service.ILogInfoService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author birdbro
 * @since 2024-05-07
 */
@Slf4j
@Service
public class LogInfoServiceImpl extends ServiceImpl<LogInfoMapper, LogInfoEntity> implements ILogInfoService {


    /**
     * The ONE operation-log write point (audit-log-unify-api design D3): the
     * {@code @Log} String label rides the transport POJO, but {@code log_info.type}
     * is the Integer code at rest (design D2) — the copy EXCLUDES {@code type}
     * (hutool 5.8.20 converts String→Integer eagerly and THROWS on a Chinese label,
     * probed 2026-11-10: the boolean overload is ignoreCase, not ignoreError), and
     * {@link LogTypes#codeOf(String)} afterwards is the ONLY type assignment path —
     * a known label maps to its code, an unknown/blank label degrades to {@code null}
     * with the row still written.
     */
    @Override
    public boolean insert(LogInfo info) {
        LogInfoEntity entity = LogInfoEntity.builder()
                .dateYm(Integer.parseInt(DateUtils.dateTimeNow("yyyyMM")))
                .app(Constants.APP_CODE.toString())
                .build();
        BeanUtil.copyProperties(info, entity, CopyOptions.create()
                .setIgnoreCase(true).setIgnoreProperties("type"));
        entity.setType(LogTypes.codeOf(info.getType()));
        baseMapper.insertInfo(entity);
        return true;
    }

    @Override
    public IPage<LogInfoEntity> getLogInfoPage(LogRequest req) {
        IPage<LogInfoEntity> page = new Page<>(req.getPageNum(),req.getPageSize());
        return baseMapper.getLogInfoPage(page,req);
    }


    /**
     * 实体无 @TableLogic（拷贝件只在 XML 里裸 SQL 过滤），此处用同款 deleted = 0 原文条件；
     * type 过滤用 StrUtil.isNotBlank（空串视同未传）。
     */
    @Override
    public IPage<LogInfoEntity> getPage(Integer pageNum, Integer pageSize, String type, Integer uid, Integer dateYm) {
        Page<LogInfoEntity> page = new Page<>(
                pageNum == null ? 1 : Math.max(1, pageNum),
                pageSize == null ? 20 : Math.max(1, pageSize));
        return this.page(page, Wrappers.<LogInfoEntity>lambdaQuery()
                .eq(StrUtil.isNotBlank(type), LogInfoEntity::getType, type)
                .eq(uid != null, LogInfoEntity::getUid, uid)
                .eq(dateYm != null, LogInfoEntity::getDateYm, dateYm)
                .apply("deleted = 0")
                .orderByDesc(LogInfoEntity::getActionTime)
                .orderByDesc(LogInfoEntity::getId)
        );
    }

    /**
     * 审计日志统一查询（audit-log-unify-api design D5/D8；b3）：Integer type / uid /
     * account / dateYm 全部可选过滤，ordering 与 deleted=0 同款；select 排除 session
     * 列（D5：登录事件的凭证锚点只存不出——取代 sign-log-query D7 的 token 列排除，
     * 同保证换新列）。account 过滤用 StrUtil.isNotBlank（空串视同未传）。
     */
    @Override
    public IPage<LogInfoEntity> getPage(Integer pageNum, Integer pageSize, Integer type, Integer uid, String account, Integer dateYm) {
        Page<LogInfoEntity> page = new Page<>(
                pageNum == null ? 1 : Math.max(1, pageNum),
                pageSize == null ? 20 : Math.max(1, pageSize));
        return this.page(page, Wrappers.<LogInfoEntity>lambdaQuery()
                .select(LogInfoEntity.class, field -> !"session".equals(field.getColumn()))
                .eq(type != null, LogInfoEntity::getType, type)
                .eq(uid != null, LogInfoEntity::getUid, uid)
                .eq(StrUtil.isNotBlank(account), LogInfoEntity::getAccount, account)
                .eq(dateYm != null, LogInfoEntity::getDateYm, dateYm)
                .apply("deleted = 0")
                .orderByDesc(LogInfoEntity::getActionTime)
                .orderByDesc(LogInfoEntity::getId)
        );
    }

    /**
     * 日期段版审计查询（log-query-date-range task 1.1；加法式重载，前序重载不动）：
     * 前序重载的全部条件之上叠加 actionTime 半开区间 [beginInclusive, endExclusive)
     * 与 {@link #monthKeys(LocalDateTime, LocalDateTime)} 推导的 date_ym 落月键 IN
     * 约束（RANGE(date_ym) 分区裁剪；月键为空 = 不加 IN，行为不受影响）。hutool
     * DateUtil.date 做 LocalDateTime→Date 换算（实体列保持 java.util.Date）。
     */
    @Override
    public IPage<LogInfoEntity> getPage(Integer pageNum, Integer pageSize, Integer type, Integer uid, String account, Integer dateYm,
            LocalDateTime beginInclusive, LocalDateTime endExclusive) {
        Page<LogInfoEntity> page = new Page<>(
                pageNum == null ? 1 : Math.max(1, pageNum),
                pageSize == null ? 20 : Math.max(1, pageSize));
        List<Integer> monthKeyList = monthKeys(beginInclusive, endExclusive);
        return this.page(page, Wrappers.<LogInfoEntity>lambdaQuery()
                .select(LogInfoEntity.class, field -> !"session".equals(field.getColumn()))
                .eq(type != null, LogInfoEntity::getType, type)
                .eq(uid != null, LogInfoEntity::getUid, uid)
                .eq(StrUtil.isNotBlank(account), LogInfoEntity::getAccount, account)
                .eq(dateYm != null, LogInfoEntity::getDateYm, dateYm)
                .in(CollUtil.isNotEmpty(monthKeyList), LogInfoEntity::getDateYm, monthKeyList)
                .ge(beginInclusive != null, LogInfoEntity::getActionTime, DateUtil.date(beginInclusive))
                .lt(endExclusive != null, LogInfoEntity::getActionTime, DateUtil.date(endExclusive))
                .apply("deleted = 0")
                .orderByDesc(LogInfoEntity::getActionTime)
                .orderByDesc(LogInfoEntity::getId)
        );
    }

    /**
     * 日期段 → date_ym 落月键推导（log-query-date-range design D4；包内可见供审阅）。
     * 双界齐全 → 起月..止月（排他上界减 1 纳秒落回止日所在月）；只有下界 → 起月..当前月
     * （行以写入时的当前月落 date_ym，无未来月）；只有上界或双界皆空 → 无法/无需枚举
     * 历史月份，返回空表（调用方跳过 IN）。跨度超 12 个月同样返回空表——IN 列表收益
     * 消失，action_time 半开区间单独保证正确性。
     */
    static List<Integer> monthKeys(LocalDateTime beginInclusive, LocalDateTime endExclusive) {
        if (beginInclusive == null) {
            return Collections.emptyList();
        }
        YearMonth now = YearMonth.now();
        YearMonth start = YearMonth.from(beginInclusive);
        YearMonth end = endExclusive == null ? now : YearMonth.from(endExclusive.minusNanos(1));
        if (start.isAfter(now)) {
            start = now;
        }
        if (end.isAfter(now)) {
            end = now;
        }
        if (start.isAfter(end) || ChronoUnit.MONTHS.between(start, end) >= 12) {
            return Collections.emptyList();
        }
        List<Integer> keys = new ArrayList<>((int) ChronoUnit.MONTHS.between(start, end) + 1);
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            keys.add(month.getYear() * 100 + month.getMonthValue());
        }
        return keys;
    }
}

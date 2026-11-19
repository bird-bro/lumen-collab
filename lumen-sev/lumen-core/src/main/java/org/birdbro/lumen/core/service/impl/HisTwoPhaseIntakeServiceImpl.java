package org.birdbro.lumen.core.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cn.hutool.core.collection.CollUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamApplyCache;
import org.birdbro.lumen.core.entity.ExamApplyCacheStatus;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.mapper.ExamApplyCacheMapper;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.birdbro.lumen.core.service.HisTwoPhaseIntakeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HIS two-phase intake implementation (lumen-core-registration-kernel task 6.1, design
 * D7; EIS 1.4 semantics). Recorded decisions:
 *
 * <ol>
 *   <li>The unmatched-item drop rule is STRUCTURAL (the kernel owns the rule, the
 *       adapter owns the lookup): mapped items without a catalog reference (null
 *       itemId) are dropped at cache time and at correction time - the persisted
 *       target carries only catalog-referenced items with their name snapshots.</li>
 *   <li>NO auto-commit: {@code cacheApply} only stores the row (status CACHED); an
 *       order comes into existence ONLY through {@code confirmApply} (spec: No
 *       auto-commit from the cache stage).</li>
 *   <li>{@code confirmApply} is ONE transaction: identity via the port, the formal
 *       order at the REGISTERED entry (the HIS confirm entry state), applyNo/hisId and
 *       the cached items carried onto it, then the cache row marked COMMITTED. Any
 *       failure rolls the whole phase back - the row stays CACHED and no order
 *       survives half-confirm.</li>
 *   <li>{@code correctApply} re-maps the target only while CACHED; a COMMITTED row
 *       refuses (spec: Cached apply corrected before commit).</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class HisTwoPhaseIntakeServiceImpl implements HisTwoPhaseIntakeService {

    private final ExamApplyCacheMapper examApplyCacheMapper;
    private final ExamOrderRegistrationService examOrderRegistrationService;
    private final org.birdbro.lumen.core.patient.PatientRegistryPort patientRegistryPort;

    public HisTwoPhaseIntakeServiceImpl(ExamApplyCacheMapper examApplyCacheMapper,
            ExamOrderRegistrationService examOrderRegistrationService,
            org.birdbro.lumen.core.patient.PatientRegistryPort patientRegistryPort) {
        this.examApplyCacheMapper = examApplyCacheMapper;
        this.examOrderRegistrationService = examOrderRegistrationService;
        this.patientRegistryPort = patientRegistryPort;
    }

    @Override
    @Transactional
    public Integer cacheApply(String hisId, String applyNo, Map<String, Object> source,
            List<ExamItemRef> mappedItems, Map<String, Object> patientHint) {
        ExamApplyCache cache = ExamApplyCache.builder()
                .hisId(hisId)
                .applyNo(applyNo)
                .status(ExamApplyCacheStatus.CACHED)
                .source(source)
                .target(dropUnmatched(mappedItems))
                .patientHint(patientHint)
                .build();
        examApplyCacheMapper.insert(cache);
        return cache.getId();
    }

    @Override
    @Transactional
    public void correctApply(Integer cacheId, List<ExamItemRef> mappedItems,
            Map<String, Object> patientHint) {
        ExamApplyCache cache = requireCached(cacheId);
        ExamApplyCache patch = new ExamApplyCache();
        patch.setId(cache.getId());
        patch.setTarget(dropUnmatched(mappedItems));
        if (patientHint != null) {
            patch.setPatientHint(patientHint);
        }
        // null-ignoring update touches ONLY the target (and the hint when corrected)
        examApplyCacheMapper.updateById(patch);
    }

    @Override
    @Transactional
    public Integer confirmApply(Integer cacheId, PatientRegistrationRequest patientRequest) {
        ExamApplyCache cache = requireCached(cacheId);

        ExamOrder order = ExamOrder.builder()
                .status(ExamOrderStatus.REGISTERED)
                .applyNo(cache.getApplyNo())
                .hisId(cache.getHisId())
                .examItems(cache.getTarget())
                .build();
        Integer orderId = examOrderRegistrationService.registerOrder(
                // the port resolves/registers the identity (the fake stands in for tests)
                patientRegistryPort.resolveOrRegister(patientRequest),
                order, ExamExecution.builder().build(), ExamReport.builder().build());

        ExamApplyCache commit = new ExamApplyCache();
        commit.setId(cache.getId());
        commit.setStatus(ExamApplyCacheStatus.COMMITTED);
        examApplyCacheMapper.updateById(commit);
        return orderId;
    }

    private ExamApplyCache requireCached(Integer cacheId) {
        ExamApplyCache cache = examApplyCacheMapper.selectById(cacheId);
        if (cache == null || Integer.valueOf(1).equals(cache.getDeleted())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "入站缓存单不存在: " + cacheId);
        }
        if (cache.getStatus() == ExamApplyCacheStatus.COMMITTED) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "缓存单已确认转单，不可再次确认或修正: " + cacheId);
        }
        return cache;
    }

    /** The drop rule: entries without a catalog reference never reach the target. */
    private static List<ExamItemRef> dropUnmatched(List<ExamItemRef> mappedItems) {
        List<ExamItemRef> matched = new ArrayList<>();
        if (CollUtil.isNotEmpty(mappedItems)) {
            for (ExamItemRef item : mappedItems) {
                if (item != null && item.getItemId() != null) {
                    matched.add(item);
                }
            }
        }
        return matched;
    }
}

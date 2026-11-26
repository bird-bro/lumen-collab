package org.birdbro.lumen.core.service.impl;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.ExamReportStatus;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.mapper.ExamReportMapper;
import org.birdbro.lumen.core.service.ExamOrderStateMachineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Document state machine implementation (lumen-core-registration-kernel task 3.1; spec
 * exam-workflow-kernel "Document State Machine"). Recorded decisions:
 *
 * <ol>
 *   <li>The transition table is a static enum-level map (registration-order-status-dict
 *       design D1): each source state maps to its set of legal targets - the six-state
 *       machine, user ruling 2026-11-26 (BOOKED/BOOKING_CANCELLED removed, the former
 *       IN_PROGRESS split into IN_EXAM/IN_DIAGNOSIS, PRINTED added):
 *       REGISTERED &rarr; {IN_EXAM, IN_DIAGNOSIS, COMPLETED, CANCELLED};
 *       IN_EXAM &rarr; {IN_DIAGNOSIS, COMPLETED, CANCELLED};
 *       IN_DIAGNOSIS &rarr; {COMPLETED, CANCELLED}; COMPLETED &rarr; {PRINTED}.
 *       PRINTED and CANCELLED are terminal. Forward edges stay permissive (every later
 *       stage fires from each earlier live state - orders cannot strand while a stage
 *       caller is absent); backward moves and terminal exits do not exist.</li>
 *   <li>This service is the status column's SINGLE writer (interface contract): the
 *       update patches the status (and the cancel reason on the cancel branch) with a
 *       null-ignoring {@code updateById}, refusing BEFORE any write when the edge is
 *       illegal - the stored status stays unchanged in every refusal case.</li>
 *   <li>The cancel guard reads the order's report member: a report already ISSUED (已出)
 *       refuses the cancel branch (spec: cancelling an order whose report is issued
 *       SHALL be refused) - the DB-backed medical document survives the order-stage
 *       cancel.</li>
 *   <li>Rejections are A0400 + descriptive msg for now (design D4: dedicated
 *       machine-actionable codes defer to the first consumer change).</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class ExamOrderStateMachineServiceImpl implements ExamOrderStateMachineService {

    /** The legal edge table: source state &rarr; its legal targets. */
    private static final Map<ExamOrderStatus, Set<ExamOrderStatus>> EDGES;

    static {
        Map<ExamOrderStatus, Set<ExamOrderStatus>> edges = new EnumMap<>(ExamOrderStatus.class);
        edges.put(ExamOrderStatus.REGISTERED,
                Set.of(ExamOrderStatus.IN_EXAM, ExamOrderStatus.IN_DIAGNOSIS,
                        ExamOrderStatus.COMPLETED, ExamOrderStatus.CANCELLED));
        edges.put(ExamOrderStatus.IN_EXAM,
                Set.of(ExamOrderStatus.IN_DIAGNOSIS, ExamOrderStatus.COMPLETED,
                        ExamOrderStatus.CANCELLED));
        edges.put(ExamOrderStatus.IN_DIAGNOSIS,
                Set.of(ExamOrderStatus.COMPLETED, ExamOrderStatus.CANCELLED));
        edges.put(ExamOrderStatus.COMPLETED, Set.of(ExamOrderStatus.PRINTED));
        // PRINTED / CANCELLED are terminal - absent from the map
        EDGES = edges;
    }

    private final ExamOrderMapper examOrderMapper;
    private final ExamReportMapper examReportMapper;

    public ExamOrderStateMachineServiceImpl(ExamOrderMapper examOrderMapper,
            ExamReportMapper examReportMapper) {
        this.examOrderMapper = examOrderMapper;
        this.examReportMapper = examReportMapper;
    }

    @Override
    @Transactional
    public ExamOrderStatus transition(Integer orderId, ExamOrderStatus target, String reason) {
        ExamOrder order = examOrderMapper.selectById(orderId);
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "检查单不存在: " + orderId);
        }
        ExamOrderStatus current = order.getStatus();
        Set<ExamOrderStatus> legalTargets = EDGES.get(current);
        if (legalTargets == null || !legalTargets.contains(target)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "非法状态迁移: " + current + " → " + target);
        }
        if (isCancelBranch(target) && reportIssued(orderId)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "报告已出的检查单不可取消（医疗文书存续，请走冲销流程）");
        }

        ExamOrder patch = new ExamOrder();
        patch.setId(orderId);
        patch.setStatus(target);
        if (isCancelBranch(target)) {
            patch.setCancelReason(reason);
        }
        // null-ignoring update touches ONLY the status (and the cancel reason)
        examOrderMapper.updateById(patch);
        return target;
    }

    private static boolean isCancelBranch(ExamOrderStatus target) {
        return target == ExamOrderStatus.CANCELLED;
    }

    private boolean reportIssued(Integer orderId) {
        ExamReport report = examReportMapper.selectOne(new LambdaQueryWrapper<ExamReport>()
                .eq(ExamReport::getOrderId, orderId));
        return report != null && report.getStatus() == ExamReportStatus.ISSUED;
    }
}

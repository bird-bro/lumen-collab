package org.birdbro.lumen.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.core.base.EntityBase;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamChannel;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.ExamReportStatus;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.entity.ReportCompletionSource;
import org.birdbro.lumen.core.mapper.ExamExecutionMapper;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.mapper.ExamReportMapper;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate persistence implementation (lumen-core-registration-kernel task 2.2; spec
 * exam-workflow-kernel "Exam-Order Aggregate"). Recorded decisions:
 *
 * <ol>
 *   <li>{@code insertWithMembers} is ONE transaction (design D3): the four members
 *       live or die together - the order inserts first, the execution and report
 *       members link to its id and insert inside the same tx.</li>
 *   <li>The patient reference is validated BEFORE any insert (decision #3: patient_uid
 *       NOT NULL, the 占位患者行 exists from the order's birth) - a missing reference is
 *       an A0400 with no row written.</li>
 *   <li>Defaults are set kernel-side when the caller omits them: channel = LOCAL,
 *       emgFlag = 0, report status = DRAFTING, completion source = LOCAL - the DB
 *       defaults stay as the backstop.</li>
 *   <li>{@code refreshPatientSnapshot} refuses when the order's report is ISSUED (the
 *       已出 guard): the refusal happens BEFORE the update, so the stored jsonb stays
 *       byte-identical (spec: no silent medical-document change). The consumer decides
 *       the follow-up (e.g. a manual re-issue prompt).</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class ExamOrderAggregateServiceImpl implements ExamOrderAggregateService {

    private final ExamOrderMapper examOrderMapper;
    private final ExamExecutionMapper examExecutionMapper;
    private final ExamReportMapper examReportMapper;

    public ExamOrderAggregateServiceImpl(ExamOrderMapper examOrderMapper,
            ExamExecutionMapper examExecutionMapper, ExamReportMapper examReportMapper) {
        this.examOrderMapper = examOrderMapper;
        this.examExecutionMapper = examExecutionMapper;
        this.examReportMapper = examReportMapper;
    }

    @Override
    @Transactional
    public Integer insertWithMembers(ExamOrder order, ExamExecution execution, ExamReport report) {
        if (order.getPatientUid() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "检查单必须携带患者引用 patient_uid（占位患者行也须先落患者域）");
        }
        if (order.getStatus() == null) {
            // the entry state belongs to the creation entry (booking 预约=预登记;
            // walk-in/HIS confirm -> REGISTERED) - a stateless document never persists
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "检查单必须携带入口状态（REGISTERED，由创建入口决定）");
        }
        if (order.getChannel() == null) {
            order.setChannel(ExamChannel.LOCAL);
        }
        if (order.getEmgFlag() == null) {
            order.setEmgFlag(0);
        }
        examOrderMapper.insert(order);

        execution.setOrderId(order.getId());
        examExecutionMapper.insert(execution);

        if (report.getStatus() == null) {
            report.setStatus(ExamReportStatus.DRAFTING);
        }
        if (report.getCompletionSource() == null) {
            report.setCompletionSource(ReportCompletionSource.LOCAL);
        }
        report.setOrderId(order.getId());
        examReportMapper.insert(report);

        return order.getId();
    }

    @Override
    @Transactional
    public void refreshPatientSnapshot(Integer orderId, PatientSnapshot snapshot) {
        ExamReport report = examReportMapper.selectOne(new LambdaQueryWrapper<ExamReport>()
                .eq(ExamReport::getOrderId, orderId));
        if (report != null && report.getStatus() == ExamReportStatus.ISSUED) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "报告已出，患者快照不再自动刷新（医疗文书不静默改，请人工重出报告）");
        }
        ExamOrder patch = new ExamOrder();
        patch.setId(orderId);
        patch.setPatientSnapshot(snapshot);
        // null-ignoring update touches ONLY the snapshot column
        examOrderMapper.updateById(patch);
    }
}

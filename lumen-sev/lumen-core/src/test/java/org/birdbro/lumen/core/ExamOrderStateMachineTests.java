package org.birdbro.lumen.core;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.ExamReportStatus;
import org.birdbro.lumen.core.mapper.ExamReportMapper;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;
import org.birdbro.lumen.core.service.ExamOrderStateMachineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Document state machine proof (registration-order-status-dict-sev task 1.1; spec
 * exam-workflow-kernel "Document State Machine" - the six-state machine, user ruling
 * 2026-11-26). Maps the five delta scenarios: booking/walk-in entry at REGISTERED,
 * the four-step happy path with forward-permissive edges, the single cancel landing
 * (CANCELLED from the three live states, refused on an issued report), and the illegal
 * rejections. The transition service is the status column's single writer; every
 * refusal leaves the stored status unchanged. The removed 已预约 vocabulary
 * (BOOKED / BOOKING_CANCELLED / IN_PROGRESS) is proven gone at the compile level - the
 * test source cannot reference it.
 *
 * @author birdbro
 */
class ExamOrderStateMachineTests extends KernelIntegrationTestBase {

    @Autowired
    private ExamOrderAggregateService aggregateService;

    @Autowired
    private ExamOrderStateMachineService stateMachineService;

    @Autowired
    private ExamReportMapper examReportMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Saves one REGISTERED-order aggregate (the 预约=预登记 entry shape) and returns its id. */
    private Integer registeredOrder(int patientUid) {
        ExamOrder order = new ExamOrder();
        order.setPatientUid(patientUid);
        order.setAccessionNo("AC" + System.nanoTime());
        order.setStatus(ExamOrderStatus.REGISTERED);
        return aggregateService.insertWithMembers(
                order, ExamExecution.builder().build(), ExamReport.builder().build());
    }

    private ExamOrderStatus storedStatus(Integer orderId) {
        return ExamOrderStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM exam_order WHERE id = ?", String.class, orderId));
    }

    /** Marks the order's report ISSUED (the cancel-guard trigger). */
    private void issueReport(Integer orderId) {
        ExamReport report = examReportMapper.selectOne(new LambdaQueryWrapper<ExamReport>()
                .eq(ExamReport::getOrderId, orderId));
        report.setStatus(ExamReportStatus.ISSUED);
        examReportMapper.updateById(report);
    }

    @Test
    @Transactional
    void bookingEntersAtRegistered() {
        // the booking-created aggregate (预约=预登记) is stored in the REGISTERED entry
        // state; the machine defines no BOOKED state at all
        Integer orderId = registeredOrder(990201);
        assertEquals(ExamOrderStatus.REGISTERED, storedStatus(orderId),
                "a booking-created order is stored in the REGISTERED entry state");
    }

    @Test
    @Transactional
    void walkInAndHisConfirmEnterAtRegistered() {
        // a walk-in registration commits without a booking: entry state REGISTERED
        ExamOrder walkIn = new ExamOrder();
        walkIn.setPatientUid(990202);
        walkIn.setAccessionNo("AC" + System.nanoTime());
        walkIn.setStatus(ExamOrderStatus.REGISTERED);
        Integer orderId = aggregateService.insertWithMembers(
                walkIn, ExamExecution.builder().build(), ExamReport.builder().build());
        assertEquals(ExamOrderStatus.REGISTERED, storedStatus(orderId),
                "a walk-in registration enters REGISTERED directly");
    }

    @Test
    @Transactional
    void happyPathAdvancesThroughEachStage() {
        Integer orderId = registeredOrder(990203);
        assertEquals(ExamOrderStatus.IN_EXAM,
                stateMachineService.transition(orderId, ExamOrderStatus.IN_EXAM, null),
                "检查开始 advances REGISTERED to IN_EXAM");
        assertEquals(ExamOrderStatus.IN_DIAGNOSIS,
                stateMachineService.transition(orderId, ExamOrderStatus.IN_DIAGNOSIS, null),
                "诊断抢占 advances IN_EXAM to IN_DIAGNOSIS");
        assertEquals(ExamOrderStatus.COMPLETED,
                stateMachineService.transition(orderId, ExamOrderStatus.COMPLETED, null),
                "完成报告 advances IN_DIAGNOSIS to COMPLETED");
        assertEquals(ExamOrderStatus.PRINTED,
                stateMachineService.transition(orderId, ExamOrderStatus.PRINTED, null),
                "打印 advances COMPLETED to PRINTED");
        assertEquals(ExamOrderStatus.PRINTED, storedStatus(orderId),
                "each step persisted through the single-writer service");
    }

    @Test
    @Transactional
    void forwardEdgesStayPermissive() {
        // forward-permissive: every later stage fires from each earlier live state
        Integer skippedExam = registeredOrder(990208);
        assertEquals(ExamOrderStatus.IN_DIAGNOSIS,
                stateMachineService.transition(skippedExam, ExamOrderStatus.IN_DIAGNOSIS, null),
                "诊断抢占 fires directly from REGISTERED (no MPPS yet)");
        Integer skippedBoth = registeredOrder(990209);
        assertEquals(ExamOrderStatus.COMPLETED,
                stateMachineService.transition(skippedBoth, ExamOrderStatus.COMPLETED, null),
                "完成报告 fires directly from REGISTERED");
        Integer skippedDiag = registeredOrder(990210);
        stateMachineService.transition(skippedDiag, ExamOrderStatus.IN_EXAM, null);
        assertEquals(ExamOrderStatus.COMPLETED,
                stateMachineService.transition(skippedDiag, ExamOrderStatus.COMPLETED, null),
                "完成报告 fires directly from IN_EXAM");
    }

    @Test
    @Transactional
    void cancellationBranchesSplitByStage() {
        // cancel from each of the three live states lands CANCELLED (the single landing)
        Integer registeredId = registeredOrder(990204);
        assertEquals(ExamOrderStatus.CANCELLED,
                stateMachineService.transition(registeredId, ExamOrderStatus.CANCELLED, "改期"),
                "cancel from REGISTERED lands in CANCELLED");

        Integer examId = registeredOrder(990205);
        stateMachineService.transition(examId, ExamOrderStatus.IN_EXAM, null);
        assertEquals(ExamOrderStatus.CANCELLED,
                stateMachineService.transition(examId, ExamOrderStatus.CANCELLED, "改期"),
                "cancel from IN_EXAM lands in CANCELLED");

        Integer diagId = registeredOrder(990206);
        stateMachineService.transition(diagId, ExamOrderStatus.IN_DIAGNOSIS, null);
        assertEquals(ExamOrderStatus.CANCELLED,
                stateMachineService.transition(diagId, ExamOrderStatus.CANCELLED, "改期"),
                "cancel from IN_DIAGNOSIS lands in CANCELLED");

        // an order whose report is ISSUED: cancel refused, status unchanged
        Integer issuedId = registeredOrder(990211);
        stateMachineService.transition(issuedId, ExamOrderStatus.COMPLETED, null);
        issueReport(issuedId);
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(issuedId, ExamOrderStatus.CANCELLED, " late"),
                "cancel with an ISSUED report is refused");
        // COMPLETED is not cancelable either (absent from the cancel edges)
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(issuedId, ExamOrderStatus.CANCELLED, null),
                "COMPLETED cannot be cancelled");
        assertEquals(ExamOrderStatus.COMPLETED, storedStatus(issuedId),
                "the refused cancel leaves the stored status unchanged");
    }

    @Test
    @Transactional
    void illegalTransitionRejected() {
        Integer orderId = registeredOrder(990207);

        // a backward move is refused
        stateMachineService.transition(orderId, ExamOrderStatus.IN_EXAM, null);
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(orderId, ExamOrderStatus.REGISTERED, null),
                "IN_EXAM back to REGISTERED is refused");
        assertEquals(ExamOrderStatus.IN_EXAM, storedStatus(orderId),
                "the refused transition leaves the stored status unchanged");

        // advance to PRINTED (terminal): any move out is refused
        stateMachineService.transition(orderId, ExamOrderStatus.IN_DIAGNOSIS, null);
        stateMachineService.transition(orderId, ExamOrderStatus.COMPLETED, null);
        stateMachineService.transition(orderId, ExamOrderStatus.PRINTED, null);
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(orderId, ExamOrderStatus.CANCELLED, null),
                "PRINTED is terminal - no move out is accepted");
        assertEquals(ExamOrderStatus.PRINTED, storedStatus(orderId),
                "the terminal state holds");
    }
}

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
 * Document state machine proof (lumen-core-registration-kernel task 3.1; spec
 * exam-workflow-kernel "Document State Machine" - all five scenarios). The transition
 * service is the status column's single writer; every refusal leaves the stored status
 * unchanged.
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

    /** Saves one BOOKED-order aggregate and returns its id. */
    private Integer bookedOrder(int patientUid) {
        ExamOrder order = new ExamOrder();
        order.setPatientUid(patientUid);
        order.setAccessionNo("AC" + System.nanoTime());
        order.setStatus(ExamOrderStatus.BOOKED);
        return aggregateService.insertWithMembers(
                order, ExamExecution.builder().build(), ExamReport.builder().build());
    }

    private ExamOrderStatus storedStatus(Integer orderId) {
        return ExamOrderStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM exam_order WHERE id = ?", String.class, orderId));
    }

    @Test
    @Transactional
    void bookingEntersAtBooked() {
        Integer orderId = bookedOrder(990201);
        assertEquals(ExamOrderStatus.BOOKED, storedStatus(orderId),
                "a booking-created order is stored in the BOOKED entry state");
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
        Integer orderId = bookedOrder(990203);
        assertEquals(ExamOrderStatus.REGISTERED,
                stateMachineService.transition(orderId, ExamOrderStatus.REGISTERED, null),
                "签到 advances BOOKED to REGISTERED");
        assertEquals(ExamOrderStatus.IN_PROGRESS,
                stateMachineService.transition(orderId, ExamOrderStatus.IN_PROGRESS, null),
                "处理中 advances REGISTERED to IN_PROGRESS");
        assertEquals(ExamOrderStatus.COMPLETED,
                stateMachineService.transition(orderId, ExamOrderStatus.COMPLETED, null),
                "完成 advances IN_PROGRESS to COMPLETED");
        assertEquals(ExamOrderStatus.COMPLETED, storedStatus(orderId),
                "each step persisted through the single-writer service");
    }

    @Test
    @Transactional
    void cancellationBranchesSplitByStage() {
        // from BOOKED: the 取消预约 branch
        Integer bookingId = bookedOrder(990204);
        assertEquals(ExamOrderStatus.BOOKING_CANCELLED,
                stateMachineService.transition(bookingId, ExamOrderStatus.BOOKING_CANCELLED, "患者取消"),
                "cancel from BOOKED lands in BOOKING_CANCELLED");

        // from REGISTERED (report not issued): the 已取消 branch
        Integer registeredId = bookedOrder(990205);
        stateMachineService.transition(registeredId, ExamOrderStatus.REGISTERED, null);
        assertEquals(ExamOrderStatus.CANCELLED,
                stateMachineService.transition(registeredId, ExamOrderStatus.CANCELLED, "改期"),
                "cancel from REGISTERED lands in CANCELLED");

        // an order whose report is ISSUED: cancel refused, status unchanged
        Integer issuedId = bookedOrder(990206);
        ExamReport report = examReportMapper.selectOne(new LambdaQueryWrapper<ExamReport>()
                .eq(ExamReport::getOrderId, issuedId));
        report.setStatus(ExamReportStatus.ISSUED);
        examReportMapper.updateById(report);
        stateMachineService.transition(issuedId, ExamOrderStatus.REGISTERED, null);
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(issuedId, ExamOrderStatus.CANCELLED, " Late"),
                "cancel with an ISSUED report is refused");
        assertEquals(ExamOrderStatus.REGISTERED, storedStatus(issuedId),
                "the refused cancel leaves the stored status unchanged");
    }

    @Test
    @Transactional
    void illegalTransitionRejected() {
        Integer orderId = bookedOrder(990207);

        // a non-edge from BOOKED: straight to IN_PROGRESS
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(orderId, ExamOrderStatus.IN_PROGRESS, null),
                "BOOKED to IN_PROGRESS skips a stage and is refused");
        assertEquals(ExamOrderStatus.BOOKED, storedStatus(orderId),
                "the refused transition leaves the stored status unchanged");

        // advance to COMPLETED, then any move out of the terminal state is refused
        stateMachineService.transition(orderId, ExamOrderStatus.REGISTERED, null);
        stateMachineService.transition(orderId, ExamOrderStatus.IN_PROGRESS, null);
        stateMachineService.transition(orderId, ExamOrderStatus.COMPLETED, null);
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(orderId, ExamOrderStatus.REGISTERED, null),
                "COMPLETED is terminal - no move out is accepted");
        assertThrows(BusinessException.class,
                () -> stateMachineService.transition(orderId, ExamOrderStatus.CANCELLED, null),
                "COMPLETED cannot be cancelled either");
        assertEquals(ExamOrderStatus.COMPLETED, storedStatus(orderId),
                "the terminal state holds");
    }
}

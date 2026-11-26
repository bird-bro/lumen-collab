package org.birdbro.lumen.core;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.ExamReportStatus;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.mapper.ExamReportMapper;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Patient-snapshot refresh guard (lumen-core-registration-kernel task 2.3; spec
 * exam-workflow-kernel "Exam-Order Aggregate" - Snapshot refresh guarded by report
 * state). The refresh updates the jsonb snapshot while the report is not issued and
 * REFUSES once it is - medical documents never change silently.
 *
 * @author birdbro
 */
class SnapshotRefreshGuardTests extends KernelIntegrationTestBase {

    @Autowired
    private ExamOrderAggregateService aggregateService;

    @Autowired
    private ExamOrderMapper examOrderMapper;

    @Autowired
    private ExamReportMapper examReportMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Saves one aggregate and returns its order id. */
    private Integer saveOrder(int patientUid) {
        ExamOrder order = new ExamOrder();
        order.setPatientUid(patientUid);
        order.setAccessionNo("AC" + System.nanoTime());
        order.setStatus(org.birdbro.lumen.core.entity.ExamOrderStatus.REGISTERED);
        order.setPatientSnapshot(PatientSnapshot.builder()
                .patId("P000" + patientUid).name("旧名").age("30").build());
        return aggregateService.insertWithMembers(
                order, ExamExecution.builder().build(), ExamReport.builder().build());
    }

    @Test
    @Transactional
    void snapshotRefreshGuardedByReportState() {
        // refresh while the report is NOT issued: the snapshot updates
        Integer orderId = saveOrder(990101);
        PatientSnapshot updated = PatientSnapshot.builder()
                .patId("P000990101").name("新名").age("31").build();
        aggregateService.refreshPatientSnapshot(orderId, updated);
        String json = jdbcTemplate.queryForObject(
                "SELECT patient_snapshot::text FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals(true, json.contains("新名"), "the refreshed snapshot persists: " + json);

        // flip the report to ISSUED, then refresh: refused, snapshot byte-identical
        ExamReport report = examReportMapper.selectOne(new LambdaQueryWrapper<ExamReport>()
                .eq(ExamReport::getOrderId, orderId));
        report.setStatus(ExamReportStatus.ISSUED);
        examReportMapper.updateById(report);
        String before = jdbcTemplate.queryForObject(
                "SELECT patient_snapshot::text FROM exam_order WHERE id = ?",
                String.class, orderId);

        PatientSnapshot tampered = PatientSnapshot.builder()
                .patId("P000990101").name("越权名").age("99").build();
        assertThrows(BusinessException.class,
                () -> aggregateService.refreshPatientSnapshot(orderId, tampered),
                "the refresh refuses once the report is issued");
        String after = jdbcTemplate.queryForObject(
                "SELECT patient_snapshot::text FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals(before, after, "the stored snapshot is unchanged after the refusal");
        assertNotEquals("越权名", after, "no silent medical-document change");
    }
}

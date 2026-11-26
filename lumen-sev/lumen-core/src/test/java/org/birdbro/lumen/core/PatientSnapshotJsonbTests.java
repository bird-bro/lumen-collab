package org.birdbro.lumen.core;

import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The patient-snapshot jsonb shape guard (us-registration-redesign-api task 1.3): the
 * additive {@code cardNo} key widens the snapshot (the ExamItemRef.modality precedent -
 * additive jsonb key, old rows read null, writes stay Jackson-serialized) so the US
 * workbench's card-no query condition can read it. The round-trip pins BOTH shapes:
 * a snapshot written WITH the key reads it back equal; a pre-widening row (raw jsonb
 * without the key) reads null and never breaks the mapper.
 *
 * @author birdbro
 */
class PatientSnapshotJsonbTests extends KernelIntegrationTestBase {

    @Autowired
    private ExamOrderAggregateService aggregateService;

    @Autowired
    private ExamOrderMapper examOrderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer saveOrder(int patientUid, PatientSnapshot snapshot) {
        ExamOrder order = new ExamOrder();
        order.setPatientUid(patientUid);
        order.setAccessionNo("AC" + System.nanoTime());
        order.setStatus(ExamOrderStatus.REGISTERED);
        order.setPatientSnapshot(snapshot);
        return aggregateService.insertWithMembers(
                order, ExamExecution.builder().build(), ExamReport.builder().build());
    }

    @Test
    @Transactional
    void snapshotCardNoRoundTripsThroughTheJsonbHandler() {
        PatientSnapshot snapshot = PatientSnapshot.builder()
                .patId("P000990401").name("卡号受试者").cardNo("CARD-" + System.nanoTime())
                .build();
        Integer orderId = saveOrder(990401, snapshot);

        ExamOrder read = examOrderMapper.selectById(orderId);
        assertEquals(snapshot.getCardNo(), read.getPatientSnapshot().getCardNo(),
                "the cardNo key round-trips through KernelJsonbTypeHandler");
    }

    @Test
    @Transactional
    void preWideningRowsReadNullCardNo() {
        // a pre-widening shape: raw jsonb WITHOUT the cardNo key (the historical rows)
        ExamOrder order = new ExamOrder();
        order.setPatientUid(990402);
        order.setAccessionNo("AC" + System.nanoTime());
        order.setStatus(ExamOrderStatus.REGISTERED);
        examOrderMapper.insert(order);
        jdbcTemplate.update("UPDATE exam_order SET patient_snapshot = ?::jsonb WHERE id = ?",
                "{\"patId\":\"POLD\",\"name\":\"旧形状\"}", order.getId());

        ExamOrder read = examOrderMapper.selectById(order.getId());
        assertNull(read.getPatientSnapshot().getCardNo(),
                "a pre-widening jsonb row reads cardNo null - the additive key never breaks the mapper");
        assertEquals("POLD", read.getPatientSnapshot().getPatId(),
                "the pre-widening keys keep reading");
    }
}

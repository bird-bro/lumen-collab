package org.birdbro.lumen.core;

import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamChannel;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.ExamReportStatus;
import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aggregate persistence proof (lumen-core-registration-kernel task 2.2; spec
 * exam-workflow-kernel "Exam-Order Aggregate" - Aggregate persists as four linked
 * members / Channel reserves the remote value). Runs against the canonical kernel DDL
 * on the test-only core schema. The modality round-trips (number-rule-modality-scope
 * task 2.2, design D5) pin the exam_items jsonb widening: the token rides the stored
 * array and pre-widening rows (no key) read back without fabricating a scope.
 *
 * @author birdbro
 */
class ExamOrderAggregateTests extends KernelIntegrationTestBase {

    @Autowired
    private ExamOrderAggregateService aggregateService;

    @Autowired
    private ExamOrderMapper examOrderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** A fully-formed member triple (order without patient identity). */
    private ExamOrder order(Integer patientUid) {
        ExamOrder order = new ExamOrder();
        order.setPatientUid(patientUid);
        order.setAccessionNo("AC" + System.nanoTime());
        // the entry state belongs to the creation entry; the tests stand in for booking
        order.setStatus(ExamOrderStatus.BOOKED);
        order.setExamItems(Collections.singletonList(
                ExamItemRef.builder().itemId(101).itemName("腹部超声").build()));
        return order;
    }

    @Test
    void aggregatePersistsAsFourLinkedMembers() {
        PatientSnapshot snapshot = PatientSnapshot.builder()
                .patId("P2611180001").name("张三").gender(1).age("34")
                .patientSource("门诊").build();

        ExamOrder order = order(990001);
        order.setPatientSnapshot(snapshot);
        ExamExecution execution = ExamExecution.builder().remark("首检").build();
        ExamReport report = ExamReport.builder().build();

        Integer orderId = aggregateService.insertWithMembers(order, execution, report);
        assertNotNull(orderId, "the aggregate save returns the order id");

        // the order row: patient reference NOT NULL + snapshot jsonb round-trips
        Integer patientUid = jdbcTemplate.queryForObject(
                "SELECT patient_uid FROM exam_order WHERE id = ?", Integer.class, orderId);
        assertEquals(990001, patientUid, "the patient reference persists NOT NULL");
        String snapshotJson = jdbcTemplate.queryForObject(
                "SELECT patient_snapshot::text FROM exam_order WHERE id = ?", String.class, orderId);
        assertNotNull(snapshotJson, "the jsonb snapshot persists as REAL jsonb");
        assertTrue(snapshotJson.contains("张三"),
                "the snapshot jsonb carries the demographics: " + snapshotJson);

        // the execution and report rows link to the order id (1:1)
        Integer executionLink = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_execution WHERE order_id = ?", Integer.class, orderId);
        assertEquals(1, executionLink, "the execution member links to the order");
        Integer reportLink = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_report WHERE order_id = ?", Integer.class, orderId);
        assertEquals(1, reportLink, "the report member links to the order");
    }

    @Test
    void channelReservesTheRemoteValue() {
        ExamOrder order = order(990002);
        ExamReport report = ExamReport.builder().build();
        Integer orderId = aggregateService.insertWithMembers(
                order, ExamExecution.builder().build(), report);

        String channel = jdbcTemplate.queryForObject(
                "SELECT channel FROM exam_order WHERE id = ?", String.class, orderId);
        assertEquals("LOCAL", channel, "channel defaults to the local value (enum-name storage)");

        // the value set carries the reserved, not-yet-enabled remote value
        assertEquals(2, ExamChannel.values().length, "local + the reserved remote value");
        assertNotNull(ExamChannel.valueOf("REMOTE"),
                "远程申请 stays defined in the value set (patient decision, remote readiness)");
    }

    @Test
    void orderWithoutPatientIdentityRefuses() {
        ExamOrder order = order(null);
        ExamExecution execution = ExamExecution.builder().build();
        ExamReport report = ExamReport.builder().build();
        assertThrows(BusinessException.class,
                () -> aggregateService.insertWithMembers(order, execution, report),
                "order creation without a patient reference is refused");
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE accession_no = ?",
                Integer.class, order.getAccessionNo());
        assertEquals(0, rows, "a refused aggregate leaves NO order row behind");
    }

    // ------------- exam_items modality widening (number-rule-modality-scope task 2.2)

    @Test
    @Transactional
    void itemModalityWideningPersistsInTheExamItemsJsonb() {
        ExamOrder order = order(990003);
        order.setExamItems(Arrays.asList(
                ExamItemRef.builder().itemId(101).itemName("腹部超声").modality("US").build(),
                ExamItemRef.builder().itemId(102).itemName("超声造影").build()));
        Integer orderId = aggregateService.insertWithMembers(
                order, ExamExecution.builder().build(), ExamReport.builder().build());

        String first = jdbcTemplate.queryForObject(
                "SELECT exam_items->0->>'modality' FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals("US", first,
                "the carried token persists as the jsonb element's modality key");
        String second = jdbcTemplate.queryForObject(
                "SELECT exam_items->1->>'modality' FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertNull(second,
                "a ref built without a token reads back NULL modality - absent key and "
                + "explicit \"modality\":null are indistinguishable through the ->> "
                + "projection (jsonb widening, never a fabricated default)");
    }

    @Test
    @Transactional
    void legacyExamItemsRowWithoutModalityKeyReadsAsNullModality() throws Exception {
        // a PRE-WIDENING row shape: the jsonb array simply lacks the key
        String accession = "ACLEGACY" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO exam_order (accession_no, status, patient_uid, exam_items) "
                        + "VALUES (?, 'BOOKED', 990004, ?::jsonb)",
                accession, "[{\"itemId\":101,\"itemName\":\"腹部超声\"}]");
        Integer orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM exam_order WHERE accession_no = ?", Integer.class, accession);

        // the mapper read path decodes TYPED refs (the ExamItemRefListJsonbTypeHandler
        // pins the element type - the raw List.class parse handed back maps) and the
        // key-less legacy element reads as a NULL token
        ExamOrder read = examOrderMapper.selectById(orderId);
        assertNotNull(read.getExamItems(), "the legacy array reads back");
        ExamItemRef first = read.getExamItems().get(0);
        assertEquals("腹部超声", first.getItemName(),
                "the pre-widening fields decode unchanged through the mapper read");
        assertNull(first.getModality(),
                "a pre-widening row reads as null modality (the global-scope fallback), "
                + "never a fabricated default token");
        String roundTripped = new ObjectMapper().writeValueAsString(read.getExamItems());
        assertFalse(roundTripped.contains("\"modality\":\""),
                "the decoded token is NULL, never a default string: " + roundTripped);

        // and the raw ExamItemRef shape (the controller-deserialization shape) reads
        // the absent key as a null token too
        ExamItemRef[] refs = new ObjectMapper().readValue(
                "[{\"itemId\":101,\"itemName\":\"腹部超声\"}]", ExamItemRef[].class);
        assertNull(refs[0].getModality(),
                "the typed shape reads the absent key as a null token");
    }
}

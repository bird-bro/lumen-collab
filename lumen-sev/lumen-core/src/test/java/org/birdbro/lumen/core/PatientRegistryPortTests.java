package org.birdbro.lumen.core;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.patient.PatientRegistryPort;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patient-registry port proof (lumen-core-registration-kernel task 5.1; spec
 * exam-workflow-kernel "Patient Registry Port" - both scenarios). The fake port (test
 * sources) stands in for the consumer's adapter; the kernel side is what this change
 * ships: order creation consumes the port and refuses an incomplete result.
 *
 * @author birdbro
 */
class PatientRegistryPortTests extends KernelIntegrationTestBase {

    @Autowired
    private PatientRegistryPort patientRegistryPort;

    @Autowired
    private ExamOrderRegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** A walk-in-shaped order document (REGISTERED entry, no accession yet). */
    private ExamOrder walkInOrder() {
        ExamOrder order = new ExamOrder();
        order.setStatus(ExamOrderStatus.REGISTERED);
        return order;
    }

    @Test
    @Transactional
    void orderCreationConsumesThePort() {
        PatientIdentity identity = patientRegistryPort.resolveOrRegister(
                PatientRegistrationRequest.builder()
                        .name("李四").gender(2).age("28").patientSource("门诊").build());
        assertNotNull(identity.getPatUid(), "the port result carries the linkage key");

        Integer orderId = registrationService.registerOrder(
                identity, walkInOrder(), ExamExecution.builder().build(),
                ExamReport.builder().build());

        Integer patientUid = jdbcTemplate.queryForObject(
                "SELECT patient_uid FROM exam_order WHERE id = ?", Integer.class, orderId);
        assertEquals(identity.getPatUid(), patientUid,
                "the order links the PORT-RESOLVED patient reference");
        String accession = jdbcTemplate.queryForObject(
                "SELECT accession_no FROM exam_order WHERE id = ?", String.class, orderId);
        assertTrue(accession != null && !accession.isBlank(),
                "the accession number is issued through the exam-side issuer when absent");
        String snapshot = jdbcTemplate.queryForObject(
                "SELECT patient_snapshot::text FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertTrue(snapshot.contains("李四"),
                "the identity's snapshot payload becomes the order snapshot: " + snapshot);
    }

    @Test
    @Transactional
    void incompletePortResultRefused() {
        ExamOrder order = walkInOrder();
        order.setAccessionNo("AC-BROUGHT-1"); // caller-brought accession survives nothing - refused first

        assertThrows(BusinessException.class,
                () -> registrationService.registerOrder(
                        PatientIdentity.builder().patUid(null).patId("P-NO-UID").build(),
                        order, ExamExecution.builder().build(), ExamReport.builder().build()),
                "a port result without pat_uid is refused");
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE accession_no = ?",
                Integer.class, "AC-BROUGHT-1");
        assertEquals(0, rows, "a refused registration leaves NO order row behind");
    }
}

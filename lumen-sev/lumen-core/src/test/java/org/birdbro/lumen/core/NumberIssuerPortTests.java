package org.birdbro.lumen.core;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Number-issuer port proof (lumen-core-registration-kernel task 4.1 after the
 * 2026-11-18 rewind; spec exam-workflow-kernel "Number Issuer Port" - both scenarios).
 * The fake port stands in for the consumer's Feign adapter; the kernel side under test
 * is the verbatim consumption and the fail-fast refusal. The issuer MECHANISM tests
 * (format / day rollover / concurrency) relocated to the platform suite with
 * `number-rule-unification`.
 *
 * @author birdbro
 */
class NumberIssuerPortTests extends KernelIntegrationTestBase {

    @Autowired
    private PatientRegistryPort patientRegistryPort;

    @Autowired
    private ExamOrderRegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void registrationIssuesAccessionThroughThePort() {
        ExamOrder order = new ExamOrder();
        order.setStatus(ExamOrderStatus.REGISTERED);
        Integer orderId = registrationService.registerOrder(
                patientRegistryPort.resolveOrRegister(
                        PatientRegistrationRequest.builder().name("发号测试").build()),
                order, ExamExecution.builder().build(), ExamReport.builder().build());

        String stored = jdbcTemplate.queryForObject(
                "SELECT accession_no FROM exam_order WHERE id = ?", String.class, orderId);
        assertEquals(FakeNumberIssuerPort.LAST_ISSUED, stored,
                "the stored accession is the port's result VERBATIM (no reformatting, "
                + "no kernel-side minting)");
    }

    @Test
    // DELIBERATELY no @Transactional: the rollback under test must be the SERVICE's own
    // transaction - inside a test-managed transaction the doomed row stays visible
    // until commit time (the RegistrationHookTests lesson)
    void issuerFailureRefusesTheRegistration() {
        FakeNumberIssuerPort.FAILING = true;
        try {
            ExamOrder order = new ExamOrder();
            order.setStatus(ExamOrderStatus.REGISTERED);
            assertThrows(IllegalStateException.class,
                    () -> registrationService.registerOrder(
                            patientRegistryPort.resolveOrRegister(
                                    PatientRegistrationRequest.builder().name("发号失败").build()),
                            order, ExamExecution.builder().build(), ExamReport.builder().build()),
                    "an issuer failure refuses the registration (fail-fast, no fabricated "
                    + "number fallback)");
        } finally {
            FakeNumberIssuerPort.FAILING = false;
        }

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE accession_no IS NULL AND deleted = 0",
                Integer.class);
        assertEquals(0, rows, "no order row survives the issuer failure");
    }
}

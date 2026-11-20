package org.birdbro.lumen.core;

import java.util.Arrays;
import java.util.Collections;

import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.number.NumberIssuerPort;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.patient.PatientRegistryPort;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private NumberIssuerPort numberIssuerPort;

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
        assertNull(FakeNumberIssuerPort.LAST_ISSUED_MODALITY,
                "an item-less order issues the family GLOBAL (null modality reaches the "
                + "port - the legacy single-arg contract preserved end to end)");
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

    // --------------------- modality-scoped issuance (number-rule-modality-scope task 2.2)

    @Test
    @Transactional
    void singleArgIssueDeliversNullModalityOnThePortWire() {
        String issued = numberIssuerPort.issue("登记号");
        assertEquals(FakeNumberIssuerPort.LAST_ISSUED, issued,
                "the single-arg call still burns the fake's sequential number");
        assertNull(FakeNumberIssuerPort.LAST_ISSUED_MODALITY,
                "the single-arg port call is the null-modality GLOBAL contract - legacy "
                + "callers (patient 影像号 et al.) must never gain a scope by accident");
    }

    @Test
    @Transactional
    void registrationIssuesTheAccessionUnderTheFirstItemsModality() {
        ExamOrder order = new ExamOrder();
        order.setStatus(ExamOrderStatus.REGISTERED);
        order.setExamItems(Collections.singletonList(
                ExamItemRef.builder().itemId(1024).itemName("腹部超声").modality("US").build()));
        Integer orderId = registrationService.registerOrder(
                patientRegistryPort.resolveOrRegister(
                        PatientRegistrationRequest.builder().name("发号模态测试").build()),
                order, ExamExecution.builder().build(), ExamReport.builder().build());

        assertEquals("US", FakeNumberIssuerPort.LAST_ISSUED_MODALITY,
                "the kernel derives the issue modality from the FIRST exam item and hands "
                + "the family + the token to the port together");
        String stored = jdbcTemplate.queryForObject(
                "SELECT exam_items->0->>'modality' FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals("US", stored,
                "the item reference persists in the exam_items jsonb WITH its modality "
                + "intact (the widening rides the create path)");
    }

    @Test
    @Transactional
    void multiItemOrderIssuesWithTheFirstItemsModalityOnly() {
        ExamOrder order = new ExamOrder();
        order.setStatus(ExamOrderStatus.REGISTERED);
        order.setExamItems(Arrays.asList(
                ExamItemRef.builder().itemId(1024).itemName("腹部超声").modality("US").build(),
                ExamItemRef.builder().itemId(1025).itemName("妇科超声").modality("CT").build()));
        registrationService.registerOrder(
                patientRegistryPort.resolveOrRegister(
                        PatientRegistrationRequest.builder().name("发号多项目").build()),
                order, ExamExecution.builder().build(), ExamReport.builder().build());

        assertEquals("US", FakeNumberIssuerPort.LAST_ISSUED_MODALITY,
                "a multi-item order numbers by the FIRST item's scope only (design D5 - "
                + "mixed-modality orders are outside v1; the kernel never scans later items)");
    }

    @Test
    @Transactional
    void blankFirstItemModalityFallsBackToTheGlobalNullContract() {
        ExamOrder order = new ExamOrder();
        order.setStatus(ExamOrderStatus.REGISTERED);
        order.setExamItems(Collections.singletonList(
                ExamItemRef.builder().itemId(1024).itemName("腹部超声").modality("  ").build()));
        registrationService.registerOrder(
                patientRegistryPort.resolveOrRegister(
                        PatientRegistrationRequest.builder().name("发号空模态").build()),
                order, ExamExecution.builder().build(), ExamReport.builder().build());

        assertNull(FakeNumberIssuerPort.LAST_ISSUED_MODALITY,
                "a blank first-item token is NOT a scope - the issue falls back to the "
                + "family global (null modality on the port wire)");
    }
}

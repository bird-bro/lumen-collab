package org.birdbro.lumen.core;

import java.util.ArrayList;
import java.util.List;

import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.hooks.RegistrationContext;
import org.birdbro.lumen.core.hooks.RegistrationHook;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registration extension-hook proof (lumen-core-registration-kernel task 7.1; spec
 * exam-workflow-kernel "Registration Extension Hooks" - both scenarios). Recording
 * hook beans arrive through a per-class {@code @TestConfiguration} (its own cached
 * context): the kernel fires them in {@code order()} sequence INSIDE the registration
 * transaction, and a throwing hook leaves NO order row behind.
 *
 * <p>The hook invocations and the throwing flag ride STATIC state ({@code
 * RecordingHooks}) because Spring caches the context across tests in the class.</p>
 *
 * @author birdbro
 */
class RegistrationHookTests extends KernelIntegrationTestBase {

    /** Static recording shared by the hook beans of the test configuration. */
    static class RecordingHooks {
        static final List<String> INVOCATIONS = new ArrayList<>();
        static boolean throwInHookB = false;
    }

    @TestConfiguration
    static class HookBeans {
        @Bean
        RegistrationHook hookA() {
            return new RegistrationHook() {
                @Override
                public int order() {
                    return 10;
                }

                @Override
                public void execute(RegistrationContext context) {
                    RecordingHooks.INVOCATIONS.add("A:" + context.getOrderId());
                }
            };
        }

        @Bean
        RegistrationHook hookB() {
            return new RegistrationHook() {
                @Override
                public int order() {
                    return 20;
                }

                @Override
                public void execute(RegistrationContext context) {
                    if (RecordingHooks.throwInHookB) {
                        throw new IllegalStateException("hook B refuses the registration");
                    }
                    RecordingHooks.INVOCATIONS.add("B:" + context.getOrderId());
                }
            };
        }
    }

    @Autowired
    private ExamOrderRegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRecording() {
        RecordingHooks.INVOCATIONS.clear();
        RecordingHooks.throwInHookB = false;
    }

    private ExamOrder bookedOrder() {
        ExamOrder order = new ExamOrder();
        order.setStatus(ExamOrderStatus.REGISTERED);
        return order;
    }

    @Test
    @Transactional
    void hooksFireInRegisteredOrder() {
        Integer orderId = registrationService.registerOrder(
                FakeIdentityHolder.identity(), bookedOrder(),
                ExamExecution.builder().build(), org.birdbro.lumen.core.entity.ExamReport.builder().build());

        assertEquals(2, RecordingHooks.INVOCATIONS.size(),
                "every registered hook fired exactly once: " + RecordingHooks.INVOCATIONS);
        assertTrue(RecordingHooks.INVOCATIONS.get(0).startsWith("A:")
                        && RecordingHooks.INVOCATIONS.get(1).startsWith("B:"),
                "hooks fire in order() sequence (10 before 20): " + RecordingHooks.INVOCATIONS);
        assertTrue(RecordingHooks.INVOCATIONS.get(0).contains(String.valueOf(orderId)),
                "the hooks receive the freshly persisted order id");
    }

    @Test
    // DELIBERATELY no @Transactional: the rollback under test must be the SERVICE's own
    // transaction - inside a test-managed transaction the doomed row stays visible
    // until commit time, so the assertion could not observe the rollback
    void hookFailureRollsBackTheRegistration() {
        RecordingHooks.throwInHookB = true;
        assertThrows(IllegalStateException.class,
                () -> registrationService.registerOrder(
                        FakeIdentityHolder.identity(), bookedOrder(),
                        ExamExecution.builder().build(),
                        org.birdbro.lumen.core.entity.ExamReport.builder().build()),
                "the hook exception propagates out of the registration");

        // A fired (added to the recording) but the transaction rolled back - no row
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE patient_uid = ?",
                Integer.class, FakeIdentityHolder.LAST_UID);
        assertEquals(0, rows, "no order row survives the hook failure");
        assertTrue(!RecordingHooks.INVOCATIONS.contains("B:")
                        || RecordingHooks.INVOCATIONS.stream().noneMatch(s -> s.startsWith("B:")),
                "the failing hook B never completed its invocation record");
    }

    /** Deterministic identity without re-resolving through the fake port each time. */
    static class FakeIdentityHolder {
        static Integer LAST_UID;

        static org.birdbro.lumen.core.patient.PatientIdentity identity() {
            org.birdbro.lumen.core.patient.PatientIdentity identity =
                    org.birdbro.lumen.core.patient.PatientIdentity.builder()
                            .patUid(991001)
                            .patId("P991001")
                            .resolved(true)
                            .snapshot(org.birdbro.lumen.core.entity.PatientSnapshot.builder()
                                    .patId("P991001").name("钩子测试").build())
                            .build();
            LAST_UID = identity.getPatUid();
            return identity;
        }
    }
}

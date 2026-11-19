package org.birdbro.lumen.manage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cn.hutool.core.date.DateUtil;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.manage.entity.NumberRule;
import org.birdbro.lumen.manage.mapper.NumberRuleMapper;
import org.birdbro.lumen.manage.service.NumberIssuerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platform number-issuer mechanism proofs (number-rule-unification-sev task 2.1; spec
 * number-rule "Platform Number Issuance" - the relocated patient-domain semantics).
 * The REQUIRES_NEW burned-serial commit is exercised through the
 * {@link IssuanceThenFailureHarness} - the caller transaction rolls back AFTER the
 * issuance and the platform-side increment must have committed regardless.
 *
 * <p>Fixture discipline (the patient-domain precedent, user ruling 2026-11-07):
 * dedicated {@code nr_}-prefixed rule types only - the seeded family rows (影像号 /
 * 登记号 / 预约号, V44) are NEVER touched. Hard DELETE sweep before each test and
 * once after the class.</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NumberIssuerServiceTests extends AuthIntegrationTestBase {

    private static final String STAMP_PATTERN = "yyMMdd";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NumberRuleMapper numberRuleMapper;

    @Autowired
    private NumberIssuerService numberIssuerService;

    @Autowired
    private IssuanceThenFailureHarness issuanceThenFailureHarness;

    @BeforeAll
    void sweepFixtureFamiliesBeforeAll() {
        sweepFixtureFamilies();
    }

    @BeforeEach
    void sweepFixtureFamiliesBeforeTest() {
        sweepFixtureFamilies();
    }

    @AfterAll
    void sweepFixtureFamiliesAfterClass() {
        sweepFixtureFamilies();
    }

    /** Hard DELETE of the {@code nr_} rule fixture family this class creates. */
    private void sweepFixtureFamilies() {
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type LIKE 'nr\\_%'");
    }

    private NumberRule.NumberRuleBuilder newRule(String type) {
        return NumberRule.builder().ruleType(type);
    }

    private Integer ruleIdOf(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM number_rule WHERE rule_type = ? AND active = 1",
                Integer.class, type);
    }

    @Test
    void concurrentIssuanceDoesNotDuplicate() throws Exception {
        examRule("nr_race", "NRR-");

        // two concurrent issuances queue on the FOR UPDATE row lock
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return numberIssuerService.issue("nr_race");
            }));
        }
        ready.await();
        start.countDown();
        String first = futures.get(0).get();
        String second = futures.get(1).get();
        pool.shutdown();

        assertNotEquals(first, second,
                "concurrent issuances must produce DISTINCT serials: " + first + " vs " + second);
        String today = DateUtil.format(new Date(), STAMP_PATTERN);
        assertEquals(java.util.Set.of(today + "0001", today + "0002"),
                java.util.Set.of(first.substring(4), second.substring(4)),
                "the row lock serializes the two issuances into consecutive serials");
    }

    @Test
    void stampRuleResetsByDay() {
        String today = DateUtil.format(new Date(), STAMP_PATTERN);
        // a PAST stamp token + yesterday's serial: the new day must restart at 1
        examRule("nr_reset", "NRS-");
        jdbcTemplate.update(
                "UPDATE number_rule SET stamp_value = '991231', serial_cur = 57 WHERE rule_type = ?",
                "nr_reset");

        String number = numberIssuerService.issue("nr_reset");
        assertEquals("NRS-" + today + "0001", number,
                "a new stamp day must restart the serial at 1");

        NumberRule after = numberRuleMapper.selectById(ruleIdOf("nr_reset"));
        assertEquals(Integer.valueOf(1), after.getSerialCur(), "serial_cur must reset with the day");
        assertEquals(today, after.getStampValue(), "stamp_value must move to the current token");
    }

    @Test
    void burnedSerialSurvivesCallerRollback() {
        examRule("nr_burn", "NRB-");

        // the harness's @Transactional caller starts a transaction, issues, then throws:
        // the REQUIRES_NEW increment has already committed - the number is burned
        assertThrows(IllegalStateException.class,
                () -> issuanceThenFailureHarness.issueThenFail("nr_burn"),
                "the harness fails after issuing");

        NumberRule after = numberRuleMapper.selectById(ruleIdOf("nr_burn"));
        assertEquals(Integer.valueOf(1), after.getSerialCur(),
                "the increment must have committed despite the outer rollback (burned serial)");
        assertEquals("NRB-" + DateUtil.format(new Date(), STAMP_PATTERN) + "0002",
                numberIssuerService.issue("nr_burn"),
                "the burned serial (0001) is never reused - the next issuance takes the "
                + "NEXT serial (0002)");
    }

    @Test
    void noActiveRuleRejectedB0001() {
        BusinessException refused = assertThrows(BusinessException.class,
                () -> numberIssuerService.issue("nr_missing"),
                "a family with no active rule is a system-state error");
        assertTrue(String.valueOf(refused.getMessage()).contains("nr_missing"),
                "the rejection names the missing family");
    }

    private void examRule(String type, String prefix) {
        numberRuleMapper.insert(newRule(type)
                .prefix(prefix).serialLength(4)
                .isStamp(1).stamp(STAMP_PATTERN).build());
    }

    /**
     * Test-only harness (the patient-domain IssuanceThenFailure precedent): its
     * {@code @Transactional} method starts the caller transaction; the rollback after
     * issuance must NOT revert the platform-side REQUIRES_NEW increment. Registered
     * through the nested {@code @TestConfiguration} (the patient harness pattern - a
     * bare {@code @Service} nested class is NOT picked up by the app component scan).
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class HarnessConfig {
        @org.springframework.context.annotation.Bean
        IssuanceThenFailureHarness issuanceThenFailureHarness(NumberIssuerService service) {
            return new IssuanceThenFailureHarness(service);
        }
    }

    static class IssuanceThenFailureHarness {
        private final NumberIssuerService numberIssuerService;

        IssuanceThenFailureHarness(NumberIssuerService numberIssuerService) {
            this.numberIssuerService = numberIssuerService;
        }

        @Transactional
        public void issueThenFail(String ruleType) {
            numberIssuerService.issue(ruleType);
            throw new IllegalStateException("simulated caller failure after issuance");
        }
    }
}

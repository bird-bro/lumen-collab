package org.birdbro.lumen.patient;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import cn.hutool.core.date.DateUtil;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.patient.entity.PatientNumberRule;
import org.birdbro.lumen.patient.mapper.PatientNumberRuleMapper;
import org.birdbro.lumen.patient.service.PatientNumberService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Number-issuance proof (patient-domain tasks 2.2 + 2.3): rule resolution (active per
 * {@code rule_type}), the {@code FOR UPDATE} row-locked {@code serial_cur} increment,
 * the stamp-day reset branch, number formatting, the 8x5 concurrency requirement, and
 * the burned-serial semantics - the coordinator-pinned REQUIRES_NEW transaction
 * semantic (brief decision 1): the counter increment commits INDEPENDENTLY of the
 * caller's transaction, so a failed archive creation after issuance burns the number
 * and the platform never reissues it (spec: "numbers are never reused").
 *
 * <p>Fixture discipline (brief decision 9): dedicated {@code b2_}-prefixed rule types
 * only - the seeded {@code pat_id} rule row is NEVER touched. Hard DELETE sweep before
 * each test (deterministic serial_cur = 0 starts; a crashed prior JVM must not collide
 * with {@code uk_patient_number_rule_active}) and once after the class (user ruling
 * 2026-11-07: residue is always hard deleted).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientNumberIssuanceTests extends PatientIntegrationTestBase {

    private static final String STAMP_PATTERN = "yyMMdd";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientNumberRuleMapper patientNumberRuleMapper;

    @Autowired
    private PatientNumberService patientNumberService;

    @Autowired
    private IssuanceThenFailureHarness issuanceThenFailureHarness;

    @BeforeEach
    void cleanFixtureFamiliesBeforeTest() {
        sweepFixtureFamilies();
    }

    @AfterAll
    void sweepFixtureFamiliesAfterClass() {
        sweepFixtureFamilies();
    }

    /** Hard-DELETE of the {@code b2_} rule fixture family this class creates. */
    private void sweepFixtureFamilies() {
        jdbcTemplate.update("DELETE FROM patient_number_rule WHERE rule_type LIKE 'b2\\_%'");
    }

    @Test
    void formatWithoutStampIsPrefixPaddedSerialSuffix() {
        patientNumberRuleMapper.insert(newRule("b2_fmt")
                .prefix("B2F-").suffix("-END").serialLength(4).build());

        assertEquals("B2F-0001-END", patientNumberService.issue("b2_fmt"),
                "without a stamp the number is prefix + zero-padded serial + suffix");
        Integer serialCur = serialCurOf("b2_fmt");
        assertEquals(Integer.valueOf(1), serialCur, "the increment must have committed");
    }

    @Test
    void formatWithStampComposesPrefixStampPaddedSerial() {
        String today = DateUtil.format(new Date(), STAMP_PATTERN);
        patientNumberRuleMapper.insert(newRule("b2_stamp")
                .prefix("B2S-").serialLength(6)
                .isStamp(1).stamp(STAMP_PATTERN).build()); // stamp_value NULL: first issuance

        assertEquals("B2S-" + today + "000001", patientNumberService.issue("b2_stamp"),
                "with a stamp the number is prefix + stamp token + zero-padded serial + suffix");
        PatientNumberRule after = patientNumberRuleMapper.selectById(ruleIdOf("b2_stamp"));
        assertNotNull(after);
        assertEquals(today, after.getStampValue(),
                "the first issuance must write the stamp token into stamp_value");
        assertEquals(Integer.valueOf(1), after.getSerialCur());
    }

    @Test
    void stampDayResetRestartsSerialAtOne() {
        String today = DateUtil.format(new Date(), STAMP_PATTERN);
        patientNumberRuleMapper.insert(newRule("b2_reset")
                .prefix("B2R-").serialLength(4)
                .isStamp(1).stamp(STAMP_PATTERN)
                .stampValue("991231")   // a PAST stamp token: yesterday's state
                .serialCur(57)          // yesterday's last serial
                .build());

        String number = patientNumberService.issue("b2_reset");
        assertEquals("B2R-" + today + "0001", number,
                "a new stamp day must restart the serial at 1");

        PatientNumberRule after = patientNumberRuleMapper.selectById(ruleIdOf("b2_reset"));
        assertNotNull(after);
        assertEquals(Integer.valueOf(1), after.getSerialCur(), "serial_cur must reset with the day");
        assertEquals(today, after.getStampValue(), "stamp_value must move to the current token");
    }

    @Test
    void sameStampDayContinuesSerial() {
        String today = DateUtil.format(new Date(), STAMP_PATTERN);
        patientNumberRuleMapper.insert(newRule("b2_cont")
                .prefix("B2K-").serialLength(4)
                .isStamp(1).stamp(STAMP_PATTERN)
                .stampValue(today)      // the CURRENT stamp token: same day
                .serialCur(5)
                .build());

        assertEquals("B2K-" + today + "0006", patientNumberService.issue("b2_cont"),
                "the same stamp day must continue serial_cur");

        PatientNumberRule after = patientNumberRuleMapper.selectById(ruleIdOf("b2_cont"));
        assertNotNull(after);
        assertEquals(Integer.valueOf(6), after.getSerialCur());
        assertEquals(today, after.getStampValue());
    }

    @Test
    void activeRuleResolutionUsesTheSingleActiveRuleOfTheType() {
        patientNumberRuleMapper.insert(newRule("b2_resolve")
                .prefix("B2RA-").serialLength(3).build()); // active (DB default 1)
        patientNumberRuleMapper.insert(newRule("b2_resolve")
                .prefix("B2RB-").serialLength(3)
                .active(0).serialCur(999).build());        // inactive decoy

        assertEquals("B2RA-001", patientNumberService.issue("b2_resolve"),
                "issuance must resolve the ACTIVE rule of the type, never the inactive one");

        PatientNumberRule inactive = patientNumberRuleMapper.selectById(inactiveRuleIdOf("b2_resolve"));
        assertNotNull(inactive);
        assertEquals(Integer.valueOf(999), inactive.getSerialCur(),
                "the inactive decoy rule must stay untouched");
    }

    @Test
    void missingActiveRuleIsRejectedWithB0001() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> patientNumberService.issue("b2_missing"));
        assertEquals("B0001", exception.getCode(),
                "a system-state error (no admin configured the rule) is B-class");
        assertTrue(exception.getMsg().contains("未配置启用的影像号规则: b2_missing"),
                "the message must name the unresolved rule type: " + exception.getMsg());
    }

    @Test
    void concurrentIssuanceYieldsDistinctNumbersUnderRowLock() throws Exception {
        patientNumberRuleMapper.insert(newRule("b2_conc")
                .prefix("B2C-").serialLength(4).build());

        int threads = 8;
        int perThread = 5;
        int total = threads * perThread;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> patientNumberService.issue("b2_conc")));
            }
            Set<String> numbers = new HashSet<>();
            for (Future<String> future : futures) {
                numbers.add(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(total, numbers.size(),
                    "row-locked issuance must hand out 40 DISTINCT numbers (40 unique pat_ids)");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(Integer.valueOf(total), serialCurOf("b2_conc"),
                "every concurrent issuance must have committed its increment");
        assertEquals("B2C-0041", patientNumberService.issue("b2_conc"),
                "the next issuance continues after the 40 concurrent ones");
    }

    @Test
    void burnedSerialIsNeverReusedAfterCallerRollback() {
        patientNumberRuleMapper.insert(newRule("b2_burn")
                .prefix("B2B-").serialLength(4).build());
        String first = patientNumberService.issue("b2_burn");
        assertEquals("B2B-0001", first);

        // Task 2.3: archive creation issues a number, then FAILS - the caller's
        // transaction rolls back while the REQUIRES_NEW counter increment has already
        // committed. The number B2B-0002 is BURNED.
        assertThrows(IllegalStateException.class,
                () -> issuanceThenFailureHarness.issueThenFail("b2_burn"),
                "the simulated archive-creation failure must propagate out of the caller tx");

        assertEquals(Integer.valueOf(2), serialCurOf("b2_burn"),
                "the increment must have committed despite the outer rollback (REQUIRES_NEW, decision 1)");

        String next = patientNumberService.issue("b2_burn");
        assertEquals("B2B-0003", next, "the next issuance must skip the burned serial entirely");
        assertNotEquals(first, next, "no number may ever be handed out twice");
        assertEquals(Integer.valueOf(3), serialCurOf("b2_burn"));
    }

    /**
     * Test-owned outer-transaction harness (task 2.3): a caller service method that
     * issues a number and then fails - the archive-creation failure AFTER issuance.
     * Its {@code @Transactional} method starts the caller transaction; the rollback on
     * the thrown exception must NOT revert the committed REQUIRES_NEW increment.
     */
    public static class IssuanceThenFailureHarness {

        private final PatientNumberService patientNumberService;

        public IssuanceThenFailureHarness(PatientNumberService patientNumberService) {
            this.patientNumberService = patientNumberService;
        }

        @Transactional
        public void issueThenFail(String ruleType) {
            patientNumberService.issue(ruleType);
            // b2-review premise assert: the throw below must roll back an ACTUAL outer
            // transaction, or the burn proof would be vacuous (nothing to roll back).
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "harness premise broken: issueThenFail must run inside a real transaction");
            throw new IllegalStateException("simulated archive-creation failure after issuance");
        }
    }

    /** Registers the harness for this test class only (static nested = auto-detected). */
    @TestConfiguration
    static class HarnessConfiguration {

        @Bean
        IssuanceThenFailureHarness issuanceThenFailureHarness(PatientNumberService patientNumberService) {
            return new IssuanceThenFailureHarness(patientNumberService);
        }
    }

    /** A fresh {@code b2_} rule builder with the common fixture defaults. */
    private static PatientNumberRule.PatientNumberRuleBuilder newRule(String ruleType) {
        return PatientNumberRule.builder()
                .ruleType(ruleType)
                .prefix("B2-")
                .suffix("")
                .serialLength(4)
                .serialCur(0)
                .isStamp(0)
                .active(1);
    }

    private Integer serialCurOf(String ruleType) {
        return jdbcTemplate.queryForObject(
                "SELECT serial_cur FROM patient_number_rule WHERE rule_type = ?",
                Integer.class, ruleType);
    }

    private Integer ruleIdOf(String ruleType) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM patient_number_rule WHERE rule_type = ? AND active = 1 AND deleted = 0",
                Integer.class, ruleType);
    }

    private Integer inactiveRuleIdOf(String ruleType) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM patient_number_rule WHERE rule_type = ? AND active = 0 AND deleted = 0",
                Integer.class, ruleType);
    }
}

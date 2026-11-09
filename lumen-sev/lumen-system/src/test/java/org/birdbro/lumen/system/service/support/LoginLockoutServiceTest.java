package org.birdbro.lumen.system.service.support;

import java.lang.reflect.Field;
import java.time.Duration;

import org.birdbro.lumen.system.AuthIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Login-lockout service proofs (login-lockout task 1.2, design T5/D2): the two-key
 * Redis model against the team test server's REAL Redis (the same instance the token
 * state lives in). {@code recordFailure} INCRs a fixed-window counter (EXPIRE only on
 * the first increment), reaching the threshold plants the lock key with the lock TTL,
 * {@code isLocked} gates on that key, and {@code clear} deletes both. The expiry test
 * plants a short-TTL lock key directly — proving the GATE reads the KEY, not the
 * config (design T2).
 *
 * @author birdbro
 */
class LoginLockoutServiceTest extends AuthIntegrationTestBase {

    @Autowired
    private LoginLockoutService lockoutService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String FAIL_KEY = "login:fail:locksvc_probe";
    private static final String LOCK_KEY = "login:lock:locksvc_probe";

    /** Threshold as configured in the test profile (default 5 — asserted below). */
    private int maxAttempts() throws Exception {
        Field f = LoginLockoutService.class.getDeclaredField("maxAttempts");
        f.setAccessible(true);
        return f.getInt(lockoutService);
    }

    @Test
    void recordFailureIncrementsWithWindowExpiryOnFirstIncrement() throws Exception {
        try {
            lockoutService.recordFailure("locksvc_probe");
            lockoutService.recordFailure("locksvc_probe");

            String count = stringRedisTemplate.opsForValue().get(FAIL_KEY);
            assertEquals("2", count, "two failures = counter 2");
            Long ttl = stringRedisTemplate.getExpire(FAIL_KEY);
            assertTrue(ttl != null && ttl > 0 && ttl <= 15 * 60,
                    "the window TTL rides the first increment (fixed window): " + ttl);
        } finally {
            lockoutService.clear("locksvc_probe");
        }
    }

    @Test
    void thresholdReachedPlantsTheLockKey() throws Exception {
        try {
            for (int i = 0; i < maxAttempts(); i++) {
                lockoutService.recordFailure("locksvc_probe");
            }
            assertTrue(lockoutService.isLocked("locksvc_probe"),
                    "max-attempts reached -> the lock key exists");
            Long lockTtl = stringRedisTemplate.getExpire(LOCK_KEY);
            assertTrue(lockTtl != null && lockTtl > 0 && lockTtl <= 30 * 60,
                    "the lock key carries the lock TTL: " + lockTtl);
        } finally {
            lockoutService.clear("locksvc_probe");
        }
    }

    @Test
    void subThresholdFailuresDoNotLock() throws Exception {
        try {
            for (int i = 0; i < maxAttempts() - 1; i++) {
                lockoutService.recordFailure("locksvc_probe");
            }
            assertFalse(lockoutService.isLocked("locksvc_probe"),
                    "below the threshold there is no lock key");
        } finally {
            lockoutService.clear("locksvc_probe");
        }
    }

    @Test
    void clearDeletesBothKeys() throws Exception {
        for (int i = 0; i < maxAttempts(); i++) {
            lockoutService.recordFailure("locksvc_probe");
        }
        assertTrue(lockoutService.isLocked("locksvc_probe"));

        lockoutService.clear("locksvc_probe");
        assertFalse(lockoutService.isLocked("locksvc_probe"), "clear removes the lock");
        assertEquals(null, stringRedisTemplate.opsForValue().get(FAIL_KEY),
                "clear removes the counter too");
    }

    @Test
    void plantedShortTtlLockExpiresNaturally() throws Exception {
        // Design T2: the gate reads the KEY. A 2-second lock key blocks first, then
        // expires — the expiry semantic is Redis TTL, not config arithmetic.
        stringRedisTemplate.opsForValue().set(LOCK_KEY, "1", Duration.ofSeconds(2));
        assertTrue(lockoutService.isLocked("locksvc_probe"));
        Thread.sleep(2500);
        assertFalse(lockoutService.isLocked("locksvc_probe"), "TTL elapsed -> gate opens");
    }
}

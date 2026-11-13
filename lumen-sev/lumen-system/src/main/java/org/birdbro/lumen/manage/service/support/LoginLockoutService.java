package org.birdbro.lumen.manage.service.support;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-account login failure lockout (login-lockout task 1.3, coordination-free
 * single-stack change; user rulings 2026-11-09). The WIRING of dormant OPS parts:
 * the {@code A0211 用户输入密码错误次数超限} error code (copied
 * {@code ExceptionEnum}, thrown by the login service, not here), and the Redis
 * infrastructure the login loop already depends on. Strictly additive — zero
 * {@code common}/{@code framework} edits.
 *
 * <p>Two-key model per username (design D2): {@code login:fail:<username>} is the
 * failure counter ({@code INCR} with the fixed-window {@code EXPIRE} riding the
 * FIRST increment — same shape as the copied {@code limitScript}), and
 * {@code login:lock:<username>} is the lock marker planted when the counter reaches
 * {@code max-attempts}, carrying the lock TTL. Only wrong-password attempts
 * ({@code A0210}) reach {@link #recordFailure} — transport failures ({@code A0400})
 * and unknown accounts ({@code A0201}) never touch these keys, and an unknown
 * username can therefore never be locked (the login service checks the lock AFTER
 * the account lookup).</p>
 *
 * <p>Parameters are committed NON-sensitive config (design D1): defaults
 * {@code 5 / 15 / 30} in {@code application.yml}; the state lives entirely in Redis
 * alongside the token state, so a Redis outage fails the login loop the same way it
 * already fails for token state (fail-closed by dependency, design D5) — nothing is
 * swallowed here.</p>
 *
 * @author birdbro
 */
@Component
public class LoginLockoutService {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration window;
    private final Duration lock;

    public LoginLockoutService(StringRedisTemplate redisTemplate,
            @Value("${lumen.security.login-lockout.max-attempts:5}") int maxAttempts,
            @Value("${lumen.security.login-lockout.window-minutes:15}") int windowMinutes,
            @Value("${lumen.security.login-lockout.lock-minutes:30}") int lockMinutes) {
        this.redis = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lock = Duration.ofMinutes(lockMinutes);
    }

    /** The configured failure threshold (visible to tests asserting the boundary). */
    int getMaxAttempts() {
        return this.maxAttempts;
    }

    /**
     * {@code true} while the lock key exists — the login service gates here BEFORE
     * the BCrypt round (a locked account costs one Redis GET, not one slow hash).
     */
    public boolean isLocked(String username) {
        Boolean locked = this.redis.hasKey(LOCK_KEY_PREFIX + username);
        return Boolean.TRUE.equals(locked);
    }

    /**
     * Records one wrong-password attempt ({@code A0210} path only). When the counter
     * reaches {@code max-attempts} the lock key is planted in the same step; the
     * CURRENT attempt still answers {@code A0210} — the lock starts biting from the
     * NEXT attempt (spec: the threshold attempt is a wrong-try answer).
     */
    public void recordFailure(String username) {
        String failKey = FAIL_KEY_PREFIX + username;
        Long count = this.redis.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            this.redis.expire(failKey, this.window);
        }
        if (count != null && count >= this.maxAttempts) {
            this.redis.opsForValue().set(LOCK_KEY_PREFIX + username, "1", this.lock);
        }
    }

    /**
     * Deletes both keys — the success path (earlier failures never accumulate) and
     * the admin password-reset emergency unlock channel share this.
     */
    public void clear(String username) {
        this.redis.delete(FAIL_KEY_PREFIX + username);
        this.redis.delete(LOCK_KEY_PREFIX + username);
    }
}

package org.birdbro.lumen.us;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;

import org.birdbro.common.core.pojo.user.UserEntry;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;
import org.birdbro.framework.security.TokenService;

/**
 * Abstract base for the us API-surface suites (us-station-registration-sev task 3.2,
 * design D8) - {@link UsIntegrationTestBase} plus the lumen-system run-identity
 * machinery (the {@code PatientApiTestBase}/{@code
 * AuthIntegrationTestBase.ensureRunAdminIdentity} pattern, coordinator-pinned decision
 * 8). The B1 suites stay on the untouched minimal base; only the HTTP suites need an
 * authenticated identity and its residue sweep.
 *
 * <p><b>Run identity (decision 8):</b> lumen-system is NOT running, so no login
 * endpoint exists - the base JDBC-clones the seeded {@code admin} (same bcrypt hash,
 * bound to role 1) into a run-scoped {@code itus3_<epoch-millis>} row in {@code
 * system.user_info} - a DISTINCT family prefix from the patient {@code itadmin_} clones
 * (the shared test server runs several suites; the sweep must never delete another
 * module's run identity), and suites mint JWTs for that identity with the context's
 * real framework {@link TokenService} - the same {@code token.secret} + the shared
 * test-server Redis write the login state the JWT filter validates.</p>
 *
 * <p><b>Permissions:</b> the minted {@code UserLogin#getAuthorities()} carries EXACTLY
 * the permissions the suite mints (the {@code hasAuthority} chain sees the same shapes
 * production does) - a suite pins {@code us:order:*} by minting WITH it and exercises a
 * refusal by minting WITHOUT it.</p>
 *
 * <p><b>Shared-database hygiene (user rulings 2026-11-05 / 2026-11-07):</b> {@link
 * #sweepApiRunResidue} ({@code @AfterAll}) PHYSICALLY deletes the run identity and its
 * {@code public.log_info} rows; the us-side fixtures (orders/ext/execution/report rows)
 * are each suite's own inline cleanup keyed on the {@link #STUB_UID_BASE} band + the
 * {@code us3}/{@code US3-} markers, which also recover a crashed prior JVM's rows.</p>
 *
 * @author birdbro
 */
public abstract class UsApiTestBase extends UsIntegrationTestBase {

    /**
     * Run-scoped administrative identity used by every API suite that needs a token:
     * {@code itus3_<epoch-millis>}, cloned from the seeded {@code admin} (same bcrypt
     * hash, bound to role 1 - the same 系统管理员 grant set). Distinct from the patient
     * {@code itadmin_} family on the shared server.
     */
    public static final String RUN_ACCOUNT = "itus3_" + System.currentTimeMillis();

    /**
     * Distinctive per-JVM issuer-org id minted into the run session's
     * {@code UserSign.entry.oid} - a band (885000+) that never collides with a real org
     * id nor the patient run-identity band (880000+).
     */
    public static final Integer RUN_OID = 885000 + (int) (System.currentTimeMillis() % 100000);

    /** Id of the run-admin user row; null until the identity is ensured. */
    private static volatile Long runUserId;

    @Autowired
    protected TokenService tokenService;

    /**
     * Per-class entry: ensures the run-scoped admin identity exists BEFORE the tests
     * mint tokens. Idempotent and self-healing - a live {@link #RUN_ACCOUNT} row is
     * reused, and a mid-JVM sweep of an earlier class deletes it, so a later class
     * re-clones it. Best effort HERE only: the first class in a JVM may call this
     * before the context (and Flyway) exists - a deferred attempt is retried at first
     * use ({@link #runUserId()} / {@link #mintToken}, both strictly post-refresh).
     */
    @BeforeAll
    static void ensureRunAdminIdentity() {
        try {
            doEnsureRunAdminIdentity();
        } catch (IllegalStateException deferred) {
            System.out.println("[us-api-test-hook] run-admin ensure deferred ("
                    + deferred.getMessage() + ") - retried at first use");
        }
    }

    /**
     * Per-class exit: one JDBC transaction that PHYSICALLY deletes the run identity and
     * its operation-log rows from the shared database (user ruling 2026-11-07: hard
     * DELETE, never soft). Us-side fixtures are NOT swept here - suites delete their own
     * inline. Bindings before owners (user_role before user_info - the V1 FK). A failure
     * fails the class EXPLICITLY.
     */
    @AfterAll
    static void sweepApiRunResidue() {
        try {
            doSweepResidue();
        } catch (SQLException e) {
            throw new IllegalStateException("the shared-database run-identity sweep failed: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Id of the run-scoped admin user. Ensures the identity first, so the value is
     * never stale-null at call time.
     */
    protected static Long runUserId() {
        doEnsureRunAdminIdentity();
        return runUserId;
    }

    /**
     * Mints a JWT for the run identity carrying EXACTLY the given permissions (the
     * {@code hasAuthority} pins see the same shapes production does). The session lands
     * in the test server's Redis through the real {@link TokenService}; the identity's
     * {@code UserSign.entry.oid} carries {@link #RUN_OID}.
     *
     * <p>{@code createToken} resolves request-scoped state ({@code ServletUtils} for
     * the User-Agent block), so minting binds a {@code MockHttpServletRequest} to the
     * test thread first (the {@code SettingIntegrationTestBase} minting precedent).</p>
     */
    protected String mintToken(String... permissions) {
        Long uid = runUserId();
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                == null) {
            org.springframework.mock.web.MockHttpServletRequest mockRequest =
                    new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("User-Agent", "us-integration-tests/1.0");
            mockRequest.setRemoteAddr("127.0.0.1");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(
                            mockRequest));
        }
        UserEntry entry = new UserEntry();
        entry.setOid(RUN_OID);
        UserSign sign = new UserSign();
        sign.setUid(uid.intValue());
        sign.setAccount(RUN_ACCOUNT);
        sign.setName("it-us3 run fixture");
        sign.setEnable(1);
        sign.setEntry(entry);
        Set<String> perms = new LinkedHashSet<>();
        for (String permission : permissions) {
            perms.add(permission);
        }
        UserLogin login = new UserLogin(uid.intValue(), sign, perms);
        return tokenService.createToken(login);
    }

    /** Token carrying the full us:order family (the all-granted operator shape). */
    protected String fullPermToken() {
        return mintToken("us:order:list", "us:order:create",
                "us:order:update", "us:order:cancel");
    }

    /** Token carrying NO permissions (every perm-gated path must refuse it). */
    protected String noPermToken() {
        return mintToken();
    }

    // ---------------------------------------------------------------- hooks

    /**
     * Reuses a live {@link #RUN_ACCOUNT} row when one exists; clones the seeded admin
     * otherwise. Clones fail EXPLICITLY when no live seeded {@code admin} row exists to
     * copy the hash from.
     */
    private static void doEnsureRunAdminIdentity() {
        try (Connection connection = openTestServerConnection()) {
            Long existing = querySingleId(connection,
                    "SELECT id FROM system.user_info WHERE username = ? AND deleted = 0",
                    RUN_ACCOUNT);
            if (existing != null) {
                runUserId = existing;
                return;
            }

            // The clone needs the seeded admin row to copy the password hash from.
            Long adminId = querySingleId(connection,
                    "SELECT id FROM system.user_info WHERE username = 'admin' AND deleted = 0");
            if (adminId == null) {
                throw new IllegalStateException("no live seeded 'admin' row in system.user_info "
                        + "- the run-admin clone cannot be created (was the system V2 seed applied?)");
            }

            try (PreparedStatement clone = connection.prepareStatement(
                    "INSERT INTO system.user_info (username, password, name) "
                    + "SELECT ?, password, 'it-us3 run fixture' FROM system.user_info "
                    + "WHERE username = 'admin' AND deleted = 0")) {
                clone.setString(1, RUN_ACCOUNT);
                if (clone.executeUpdate() != 1) {
                    throw new IllegalStateException("the run-admin clone did not insert exactly one row");
                }
            }
            // role 1 = the seeded 系统管理员 role: the same grant set as the real admin.
            try (PreparedStatement bind = connection.prepareStatement(
                    "INSERT INTO system.user_role (user_id, role_id) "
                    + "VALUES ((SELECT id FROM system.user_info WHERE username = ? AND deleted = 0), 1)")) {
                bind.setString(1, RUN_ACCOUNT);
                if (bind.executeUpdate() != 1) {
                    throw new IllegalStateException("the run-admin role binding did not insert exactly one row");
                }
            }

            runUserId = querySingleId(connection,
                    "SELECT id FROM system.user_info WHERE username = ? AND deleted = 0",
                    RUN_ACCOUNT);
            if (runUserId == null) {
                throw new IllegalStateException("the run-admin row vanished right after the clone");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("the run-scoped test admin identity could not be "
                    + "ensured: " + e.getMessage(), e);
        }
    }

    /** The sweep itself: hard DELETEs, one transaction, bindings before owners. */
    private static void doSweepResidue() throws SQLException {
        try (Connection connection = openTestServerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // 1. Operation-log rows attributed to the run account (the @Log aspect
                //    writes public.log_info). No audit block - plain DELETE.
                int logRows = statement.executeUpdate(
                        "DELETE FROM public.log_info WHERE account = '" + RUN_ACCOUNT + "'");
                // 2. user_role binding before the user row (V1 FK).
                int roleBindingRows = statement.executeUpdate(
                        "DELETE FROM system.user_role WHERE user_id IN (SELECT id FROM "
                                + "system.user_info WHERE username = '" + RUN_ACCOUNT + "')");
                // 3. The run user itself.
                int userRows = statement.executeUpdate(
                        "DELETE FROM system.user_info WHERE username = '" + RUN_ACCOUNT + "'");
                connection.commit();
                System.out.println("[us-api-test-hook] hard-swept run residue - "
                        + "log_info=" + logRows + " user_role=" + roleBindingRows
                        + " user_info=" + userRows);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Plain-JDBC connection to the team test server's database - the same resolution
     * as {@code teamTestServerProperties} ({@code currentSchema=us,public}); every
     * system/public table is qualified explicitly. Connects OUTSIDE Spring on purpose.
     */
    private static Connection openTestServerConnection() throws SQLException {
        UsTestEnv env = UsTestEnv.load();
        Map<String, String> required = env.require(
                UsTestEnv.PG_HOST, UsTestEnv.PG_PORT, UsTestEnv.PG_USER, UsTestEnv.PG_PASSWORD);
        String database = env.optional(UsTestEnv.DB, UsTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(UsTestEnv.PG_HOST) + ":"
                + required.get(UsTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=us,public";
        return DriverManager.getConnection(url, required.get(UsTestEnv.PG_USER),
                required.get(UsTestEnv.PG_PASSWORD));
    }

    /** First id of a query, or {@code null} when no row matched. */
    private static Long querySingleId(Connection connection, String sql, Object... params)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                query.setObject(i + 1, params[i]);
            }
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getLong(1) : null;
            }
        }
    }
}

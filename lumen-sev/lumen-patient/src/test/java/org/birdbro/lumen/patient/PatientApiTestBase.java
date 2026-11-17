package org.birdbro.lumen.patient;

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
 * Abstract base for the patient API-surface suites (patient-domain batch 4, tasks
 * 4.1/4.2) - {@link PatientIntegrationTestBase} plus the lumen-system run-identity
 * machinery (the {@code AuthIntegrationTestBase.ensureRunAdminIdentity}/
 * {@code sweepRunResidue} pattern, coordinator-pinned decision 8). The b1-b3 suites
 * stay on the untouched minimal base; only the HTTP suites need an authenticated
 * identity and its residue sweep.
 *
 * <p><b>Run identity (decision 8):</b> lumen-system is NOT running, so no login
 * endpoint exists - the base JDBC-clones the seeded {@code admin} (same bcrypt hash,
 * bound to role 1) into a run-scoped {@code itadmin_<epoch-millis>} row in
 * {@code system.user_info} (broad test creds may cross schemas), and suites mint JWTs
 * for that identity with the context's real framework {@link TokenService} - the same
 * {@code token.secret} + the shared test-server Redis write the login state the JWT
 * filter validates. The minted session carries {@link #RUN_OID} in
 * {@code UserSign.entry.oid} (a distinctive per-JVM band), so the issuer-org wiring
 * (b4 amendment 1: {@code patient_master.oid} = the principal's entry.oid) is
 * provable against a value no other row can carry. Audit attribution
 * ({@code create_by} via the {@code UserHeaderBridgeFilter} + {@code MetaObjectConfig})
 * lands on the run account, never on the real admin.</p>
 *
 * <p><b>Permissions:</b> the minted {@code UserLogin#getAuthorities()} carries EXACTLY
 * the permissions the suite mints (the {@code hasAuthority} chain sees the same shapes
 * production does) - a suite pins {@code patient:rule:*} by minting WITH it and
 * exercises a refusal by minting WITHOUT it. Archive endpoints are AUTHENTICATION-ONLY
 * in v1 (auth-rbac b4 precedent: no archive menu nodes exist yet, so no archive perm
 * strings are invented) - any authenticated user passes, tokenless requests answer the
 * entry point's "203".</p>
 *
 * <p><b>Shared-database hygiene (user rulings 2026-11-05 / 2026-11-07):</b>
 * {@link #sweepApiRunResidue} ({@code @AfterAll}) PHYSICALLY deletes the run identity
 * and its {@code public.log_info} rows; the patient-side fixtures (archives, rules)
 * are each suite's own inline cleanup through {@code PatientB4Fixtures}, whose
 * name/type markers also recover a crashed prior JVM's rows.</p>
 *
 * @author birdbro
 */
public abstract class PatientApiTestBase extends PatientIntegrationTestBase {

    /**
     * Run-scoped administrative identity used by every API suite that needs a token:
     * {@code itadmin_<epoch-millis>}, cloned from the seeded {@code admin} (same bcrypt
     * hash, bound to role 1 - the same 系统管理员 grant set). One value per JVM
     * (surefire's single fork); the underscore + timestamp shape follows the
     * fixture-username convention.
     */
    public static final String RUN_ACCOUNT = "itadmin_" + System.currentTimeMillis();

    /**
     * Distinctive per-JVM issuer-org id minted into the run session's
     * {@code UserSign.entry.oid}: the authenticated archive create must persist THIS
     * value into {@code patient_master.oid} (b4 amendment 1's proof), and the band
     * (880000+) never collides with a real org id.
     */
    public static final Integer RUN_OID = 880000 + (int) (System.currentTimeMillis() % 100000);

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
            System.out.println("[patient-api-test-hook] run-admin ensure deferred ("
                    + deferred.getMessage() + ") - retried at first use");
        }
    }

    /**
     * Per-class exit: one JDBC transaction that PHYSICALLY deletes the run identity
     * and its operation-log rows from the shared database (user ruling 2026-11-07:
     * hard DELETE, never soft). Patient-side fixtures are NOT swept here - suites
     * delete their own inline ({@code PatientB4Fixtures}). Bindings before owners
     * (user_role before user_info - the V1 FK). A failure fails the class EXPLICITLY.
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
     * Id of the run-scoped admin user. Audit-attribution assertions compare against
     * this instead of the seeded admin's id 1. Ensures the identity first, so the
     * value is never stale-null at call time.
     */
    protected static Long runUserId() {
        doEnsureRunAdminIdentity();
        return runUserId;
    }

    /**
     * Mints a JWT for the run identity carrying EXACTLY the given permissions (the
     * {@code hasAuthority} pins see the same shapes production does). The session
     * lands in the test server's Redis through the real {@link TokenService}; the
     * identity's {@code UserSign.entry.oid} carries {@link #RUN_OID} for the
     * issuer-org wiring proof.
     *
     * <p>{@code createToken} resolves request-scoped state ({@code ServletUtils} for
     * the User-Agent block), so minting binds a {@code MockHttpServletRequest} to the
     * test thread first - the minted session's agent block is then exactly what a real
     * login would store (the {@code SettingIntegrationTestBase} minting precedent).</p>
     */
    protected String mintToken(String... permissions) {
        Long uid = runUserId();
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                == null) {
            org.springframework.mock.web.MockHttpServletRequest mockRequest =
                    new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("User-Agent", "patient-integration-tests/1.0");
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
        sign.setName("it-patient run fixture");
        sign.setEnable(1);
        sign.setEntry(entry);
        Set<String> perms = new LinkedHashSet<>();
        for (String permission : permissions) {
            perms.add(permission);
        }
        UserLogin login = new UserLogin(uid.intValue(), sign, perms);
        return tokenService.createToken(login);
    }

    /** Token carrying every rule perm (the all-granted administrator shape). */
    protected String fullPermToken() {
        return mintToken("patient:rule:list", "patient:rule:create",
                "patient:rule:update", "patient:rule:delete");
    }

    /** Token carrying NO permissions (every perm-gated path must refuse it). */
    protected String noPermToken() {
        return mintToken();
    }

    // ---------------------------------------------------------------- hooks

    /**
     * Reuses a live {@link #RUN_ACCOUNT} row when one exists (an earlier class in this
     * JVM may have cloned one already); clones the seeded admin otherwise. Clones fail
     * EXPLICITLY when no live seeded {@code admin} row exists to copy the hash from.
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
                    + "SELECT ?, password, 'it-patient run fixture' FROM system.user_info "
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
                //    writes public.log_info; no logins happen here, so log_sign is
                //    never touched). No audit block - plain DELETE.
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
                System.out.println("[patient-api-test-hook] hard-swept run residue - "
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
     * as {@code teamTestServerProperties} ({@code currentSchema=patient,public}); every
     * system/public table is qualified explicitly. Connects OUTSIDE Spring on purpose.
     */
    private static Connection openTestServerConnection() throws SQLException {
        PatientTestEnv env = PatientTestEnv.load();
        Map<String, String> required = env.require(
                PatientTestEnv.PG_HOST, PatientTestEnv.PG_PORT,
                PatientTestEnv.PG_USER, PatientTestEnv.PG_PASSWORD);
        String database = env.optional(PatientTestEnv.DB, PatientTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(PatientTestEnv.PG_HOST) + ":"
                + required.get(PatientTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=patient,public";
        return DriverManager.getConnection(url, required.get(PatientTestEnv.PG_USER),
                required.get(PatientTestEnv.PG_PASSWORD));
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

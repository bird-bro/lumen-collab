package org.birdbro.lumen.system;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Abstract base for lumen-system integration tests against the TEAM TEST SERVER (design D3,
 * auth-rbac-foundation tasks 1.2-1.3). PostgreSQL + Redis are externally provisioned - NO
 * Testcontainers (design D3 rework: team server, not containers). The base:
 *
 * <ol>
 *   <li>resolves the {@code AUTH_TEST_*} connection variables from the gitignored
 *       {@code lumen-sev/.env-test.local} (see {@link AuthTestEnv}) - missing required
 *       variables fail the context EXPLICITLY, never a silent skip;</li>
 *   <li>bootstraps the server at most once per JVM: {@code CREATE DATABASE} guarded by a
 *       {@code pg_database} existence check, then {@code CREATE SCHEMA IF NOT EXISTS system}
 *       ({@link AuthTestServerBootstrap});</li>
 *   <li>injects the application datasource ({@code currentSchema=system,public}), Redis, and
 *       Flyway properties via {@code @DynamicPropertySource} - so Flyway executes its
 *       migrations against the real database and the token state lands in the server's
 *       Redis.</li>
 * </ol>
 *
 * <p>{@code @ActiveProfiles("test")} overrides the dev default of application.yml, so
 * the gitignored application-dev.yml is not loaded in tests - the dynamic properties
 * below are the ONLY datasource source of truth.</p>
 *
 * <p>Shared-database hygiene (user ruling 2026-11-05, post-archive follow-up to
 * sign-log-query): the team test server's database is LONG-LIVED and shared, so a suite
 * run must stop polluting it. The base owns two hooks around every test class:</p>
 *
 * <ol>
 *   <li>{@code @BeforeAll ensureRunAdminIdentity} - clones the seeded {@code admin} into
 *       a run-scoped {@code itadmin_<epoch-millis>} account (same bcrypt hash, bound to
 *       role 1 = the same 系统管理员 grant set), so suites authenticate as the RUN
 *       identity through {@link #runAdminLoginBody()} instead of the real admin: sign
 *       rows, {@code @Log} rows, and audit columns attribute to {@link #RUN_ACCOUNT},
 *       never to {@code admin}.</li>
 *   <li>{@code @AfterAll sweepRunResidue} - one JDBC transaction that logically deletes
 *       everything the run and its fixtures left behind: the run account's and the
 *       fixture families' log/audit rows, leaked fixture users (with their
 *       {@code user_role} bindings), and leaked fixture roles (with their
 *       {@code role_menu} bindings).</li>
 * </ol>
 *
 * <p>Test-owned namespace - REAL users/roles must NEVER take these names, the sweep
 * deletes live rows matching them: fixture accounts are the {@link #ACCOUNT_PREFIXES}
 * families plus the {@code ua-probe} probe account; fixture roles are the
 * {@link #ROLE_NAME_PREFIXES} families. {@code AuthLoginTests} deliberately stays on the
 * REAL admin (it tests the seeded-admin login contract itself) and is the one accepted
 * residue - roughly two {@code public.log_sign} rows per run under {@code admin}.</p>
 *
 * <p>Ordering note (probe-verified 2026-11-05): a static {@code @BeforeAll} runs BEFORE
 * the Spring context refreshes, so the FIRST test class in a JVM may reach the ensure
 * before Flyway has applied (fresh-database case). The ensure is therefore IDEMPOTENT
 * and re-attempted lazily at first use ({@link #runAdminLoginBody()} /
 * {@link #runUserId()}, both strictly post-refresh) - deferred, never silently skipped:
 * on the lazy path a failure surfaces as an explicit {@link IllegalStateException}.</p>
 *
 * @author birdbro
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AuthIntegrationTestBase {

    /**
     * Registers the test-server properties. Runs during context creation, BEFORE the
     * context refreshes - so an unconfigured or unreachable server fails the context
     * right here, with the explicit signal.
     */
    @DynamicPropertySource
    static void teamTestServerProperties(DynamicPropertyRegistry registry) {
        AuthTestEnv env = AuthTestEnv.load();
        Map<String, String> required = env.require(
                AuthTestEnv.PG_HOST, AuthTestEnv.PG_PORT, AuthTestEnv.PG_USER,
                AuthTestEnv.PG_PASSWORD, AuthTestEnv.REDIS_HOST, AuthTestEnv.REDIS_PORT);
        String database = env.optional(AuthTestEnv.DB, AuthTestEnv.DEFAULT_DB);
        String redisPassword = env.optional(AuthTestEnv.REDIS_PASSWORD, null);

        String pgHost = required.get(AuthTestEnv.PG_HOST);
        int pgPort = parseIntPort(AuthTestEnv.PG_PORT, required.get(AuthTestEnv.PG_PORT));
        String pgUser = required.get(AuthTestEnv.PG_USER);
        String pgPassword = required.get(AuthTestEnv.PG_PASSWORD);
        String redisHost = required.get(AuthTestEnv.REDIS_HOST);
        int redisPort = parseIntPort(AuthTestEnv.REDIS_PORT, required.get(AuthTestEnv.REDIS_PORT));

        AuthTestServerBootstrap.ensureDatabaseAndSchema(pgHost, pgPort, pgUser, pgPassword, database);

        // Application datasource: the lumen database through the system schema first,
        // then the shared public schema (design D1 - the four public system tables
        // resolve behind the service's own schema).
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":"
                + pgPort + "/" + database + "?currentSchema=system,public");
        registry.add("spring.datasource.username", () -> pgUser);
        registry.add("spring.datasource.password", () -> pgPassword);

        // Redis on the test server: TokenService token state is real in tests.
        registry.add("spring.redis.host", () -> redisHost);
        registry.add("spring.redis.port", () -> String.valueOf(redisPort));
        // Set ONLY when non-empty (empty/optional per the env contract).
        if (redisPassword != null) {
            registry.add("spring.redis.password", () -> redisPassword);
        }

        // Flyway migrates the system schema of the test server's database. Flyway 7.x
        // (the Boot-managed train) requires default-schema to be a member of schemas.
        registry.add("spring.flyway.schemas", () -> AuthTestServerBootstrap.SYSTEM_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> AuthTestServerBootstrap.SYSTEM_SCHEMA);
        // Boot 2.5's check-location default rejects an EMPTY db/migration (production
        // guard against typo'd locations). The test base must also hold BEFORE Batch 2's
        // first migration lands, so the location check is relaxed here; the tests
        // themselves assert Flyway actually ran (history table queryable).
        registry.add("spring.flyway.check-location", () -> "false");
    }

    private static int parseIntPort(String name, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be an integer, but was: '"
                    + raw + "'", e);
        }
    }

    // ===================== run-scoped admin + residue sweep =====================
    // (shared-team-test-DB cleanup hook, user ruling 2026-11-05 - the migration
    // precedent is the manual V5-style sweep ordered the same day.)

    /**
     * Run-scoped administrative identity used by every suite that needs an administrator
     * token: {@code itadmin_<epoch-millis>}, cloned from the seeded {@code admin} (same
     * bcrypt hash, bound to role 1 - the same 系统管理员 grant set). One value per JVM
     * (surefire's single fork), so log/audit rows attribute to the run instead of the
     * real admin; the underscore + timestamp shape follows the fixture-username
     * convention. Constants.APP_CODE is a compile-time constant and stays untouched.
     */
    public static final String RUN_ACCOUNT = "itadmin_" + System.currentTimeMillis();

    /**
     * The documented test credential of the seeded admin (V2 seed note) - also the
     * run-admin's password, whose password column is the SAME hash by construction.
     */
    static final String ADMIN_TEST_PASSWORD = "admin123";

    /**
     * Fixture-account namespace owned by the tests, sweep-targetable. REAL users must
     * NEVER take these prefixes - the {@code @AfterAll} sweep logically deletes live rows
     * matching them. {@code b2*} covers the b2-prefixed sign/log fixture accounts;
     * {@code no.such.user.} is AuthLoginTests' unknown-account probe (its failure rows
     * are swept even though that suite stays on the real admin); {@code ua-probe} is
     * AuthUserAgentResilienceTests' probe account (its rows are swept, not left in place).
     */
    static final String[] ACCOUNT_PREFIXES = { "b2", "dup_u_", "pw_u_", "en_u_", "replace_u_",
            "role_del_u_", "audit_u_", "single_u_", "noperm_u_", "city_u_", "no.such.user." };

    /**
     * Fixture-role namespace owned by the tests, sweep-targetable (the Chinese literals
     * match the fixture role names in AuthRoleMenuTests/AuthPermEnforcementTests). REAL
     * roles must NEVER take these prefixes - the sweep logically deletes live roles
     * matching them, together with their role_menu bindings; bound fixture users go with
     * the account sweep.
     */
    static final String[] ROLE_NAME_PREFIXES = { "报告审核员_", "单页角色_", "无权角色_", "denied_role_" };

    /** Id of the run-admin user row; null until the identity is ensured. */
    private static volatile Long runUserId;

    /**
     * Per-class entry: ensures the run-scoped admin identity exists BEFORE the tests
     * authenticate. Idempotent and self-healing - a live {@link #RUN_ACCOUNT} row is
     * reused, and a mid-JVM sweep of an earlier class soft-deletes the run account, so a
     * later class re-clones it (the partial unique index allows the fresh row). Best
     * effort HERE only: the first class in a JVM may call this before the context (and
     * Flyway) exists - a deferred attempt is retried at first use (see the class
     * ordering note).
     */
    @BeforeAll
    static void ensureRunAdminIdentity() {
        try {
            doEnsureRunAdminIdentity();
        } catch (IllegalStateException deferred) {
            System.out.println("[system-test-hook] run-admin ensure deferred ("
                    + deferred.getMessage() + ") - retried at first use");
        }
    }

    /**
     * Per-class exit: one JDBC transaction that logically deletes the run's residue from
     * the shared database, in dependency-safe order (bindings before owners):
     * <ol>
     *   <li>{@code public.log_sign} / {@code public.log_info}: the run account's, the
     *       fixture families', and {@code ua-probe}'s live rows ({@code deleted = 1};
     *       no {@code update_time} touch - the log tables have no audit block);</li>
     *   <li>{@code system.user_role} bindings of the run/fixture users;</li>
     *   <li>{@code system.user_info}: the run and fixture users themselves;</li>
     *   <li>{@code system.role_menu} bindings of the fixture roles;</li>
     *   <li>{@code system.role_info}: the fixture roles themselves.</li>
     * </ol>
     * A failure fails the class EXPLICITLY (never a silent skip) - leftover residue is
     * exactly the shared-database pollution this hook exists to prevent, and the next
     * class's sweep re-cleans it.
     */
    @AfterAll
    static void sweepRunResidue() {
        try {
            doSweepResidue();
        } catch (SQLException e) {
            throw new IllegalStateException("the shared-database residue sweep failed: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Login request body for the run-scoped admin - the drop-in replacement for the
     * former {@code {"username":"admin","password":"admin123"}} bodies, so the login
     * loop's sign row attributes to {@link #RUN_ACCOUNT} instead of the real admin.
     */
    static String runAdminLoginBody() {
        doEnsureRunAdminIdentity();
        return "{\"username\":\"" + RUN_ACCOUNT + "\",\"password\":\"" + ADMIN_TEST_PASSWORD + "\"}";
    }

    /**
     * Id of the run-scoped admin user. Audit-column and operator-identity assertions
     * compare against this instead of the seeded admin's id 1. Ensures the identity
     * first, so the value is never stale-null at call time (assertions run strictly
     * after the context refresh, unlike the {@code @BeforeAll} attempt).
     */
    public static Long runUserId() {
        doEnsureRunAdminIdentity();
        return runUserId;
    }

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
                        + "- the run-admin clone cannot be created (was the V2 seed applied?)");
            }

            try (PreparedStatement clone = connection.prepareStatement(
                    "INSERT INTO system.user_info (username, password, name) "
                    + "SELECT ?, password, 'it-admin run fixture' FROM system.user_info "
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

    /** The sweep itself: five statements, one transaction, dependency-safe order. */
    private static void doSweepResidue() throws SQLException {
        try (Connection connection = openTestServerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // 1. Log/audit rows (the log tables carry `deleted` but no update_time).
                int signRows = statement.executeUpdate(
                        "UPDATE public.log_sign SET deleted = 1 WHERE deleted = 0 AND ("
                                + accountResiduePredicate("account") + ")");
                int infoRows = statement.executeUpdate(
                        "UPDATE public.log_info SET deleted = 1 WHERE deleted = 0 AND ("
                                + accountResiduePredicate("account") + ")");
                // 2. Bindings of the run/fixture users (the inner select has no deleted
                //    filter on purpose: bindings to already-dead fixture users go too).
                int userRoleRows = statement.executeUpdate(
                        "UPDATE system.user_role SET deleted = 1, update_time = CURRENT_TIMESTAMP "
                        + "WHERE deleted = 0 AND user_id IN (SELECT id FROM system.user_info WHERE "
                        + accountResiduePredicate("username") + ")");
                // 3. The run and fixture users themselves.
                int userRows = statement.executeUpdate(
                        "UPDATE system.user_info SET deleted = 1, update_time = CURRENT_TIMESTAMP "
                        + "WHERE deleted = 0 AND (" + accountResiduePredicate("username") + ")");
                // 4. Bindings of the fixture roles.
                int roleMenuRows = statement.executeUpdate(
                        "UPDATE system.role_menu SET deleted = 1, update_time = CURRENT_TIMESTAMP "
                        + "WHERE deleted = 0 AND role_id IN (SELECT id FROM system.role_info "
                        + "WHERE deleted = 0 AND (" + roleResiduePredicate("name") + "))");
                // 5. The fixture roles themselves.
                int roleRows = statement.executeUpdate(
                        "UPDATE system.role_info SET deleted = 1, update_time = CURRENT_TIMESTAMP "
                        + "WHERE deleted = 0 AND (" + roleResiduePredicate("name") + ")");
                connection.commit();
                System.out.println("[system-test-hook] swept run residue - log_sign=" + signRows
                        + " log_info=" + infoRows + " user_role=" + userRoleRows
                        + " user_info=" + userRows + " role_menu=" + roleMenuRows
                        + " role_info=" + roleRows);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * SQL fragment over one column (log tables' {@code account} / user tables'
     * {@code username}): the run account, every fixture-account family prefix, and the
     * {@code ua-probe} probe account. Underscores are escaped so the LIKE patterns match
     * exactly the family shapes, never {@code x_u_y} accidents.
     */
    private static String accountResiduePredicate(String column) {
        StringBuilder predicate = new StringBuilder();
        predicate.append(column).append(" = '").append(RUN_ACCOUNT).append("'");
        for (String prefix : ACCOUNT_PREFIXES) {
            predicate.append(" OR ").append(column).append(" LIKE '")
                    .append(likePrefixPattern(prefix)).append("%' ESCAPE '\\'");
        }
        predicate.append(" OR ").append(column).append(" = 'ua-probe'");
        return predicate.toString();
    }

    /** SQL fragment matching the fixture-role namespace (LIKE prefixes, escaped). */
    private static String roleResiduePredicate(String column) {
        StringBuilder predicate = new StringBuilder();
        for (int i = 0; i < ROLE_NAME_PREFIXES.length; i++) {
            if (i > 0) {
                predicate.append(" OR ");
            }
            predicate.append(column).append(" LIKE '")
                    .append(likePrefixPattern(ROLE_NAME_PREFIXES[i])).append("%' ESCAPE '\\'");
        }
        return predicate.toString();
    }

    /** LIKE prefix pattern for a fixture prefix (backslash is PostgreSQL's escape). */
    private static String likePrefixPattern(String prefix) {
        return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Plain-JDBC connection to the team test server's database, the same resolution as
     * {@code teamTestServerProperties} (AuthTestEnv; {@code currentSchema=system,public}).
     * The hooks connect OUTSIDE Spring on purpose - they must work even when a class's
     * context never refreshed.
     */
    private static Connection openTestServerConnection() throws SQLException {
        AuthTestEnv env = AuthTestEnv.load();
        Map<String, String> required = env.require(
                AuthTestEnv.PG_HOST, AuthTestEnv.PG_PORT, AuthTestEnv.PG_USER, AuthTestEnv.PG_PASSWORD);
        String database = env.optional(AuthTestEnv.DB, AuthTestEnv.DEFAULT_DB);
        String host = required.get(AuthTestEnv.PG_HOST);
        int port = parseIntPort(AuthTestEnv.PG_PORT, required.get(AuthTestEnv.PG_PORT));
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?currentSchema=system,public";
        return DriverManager.getConnection(url,
                required.get(AuthTestEnv.PG_USER), required.get(AuthTestEnv.PG_PASSWORD));
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

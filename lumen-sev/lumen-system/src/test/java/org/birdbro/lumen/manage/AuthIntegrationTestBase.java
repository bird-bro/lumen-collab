package org.birdbro.lumen.manage;

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
 *   <li>{@code @AfterAll sweepRunResidue} - one JDBC transaction that PHYSICALLY deletes
 *       (hard {@code DELETE}, user ruling 2026-11-07 - the shared database must not
 *       accumulate soft-deleted test rows, they poison bug triage) everything the run
 *       and its fixtures left behind: the run account's and the fixture families'
 *       log/audit rows, leaked fixture users (with their {@code user_role} bindings),
 *       leaked fixture roles (with their {@code role_menu} bindings), leaked fixture
 *       menus, leaked fixture dict catalogs/details, and leaked fixture orgs and
 *       departments (bindings before owners, so the V1 FKs never trip).</li>
 * </ol>
 *
 * <p>Test-owned namespace - REAL users/roles/orgs/dicts/menus must NEVER take these
 * names, the sweep hard-deletes every row matching them (live or soft-deleted):
 * fixture accounts are the {@link #ACCOUNT_PREFIXES} families plus the {@code ua-probe}
 * probe account; fixture roles are the {@link #ROLE_NAME_PREFIXES} families; fixture
 * orgs/departments/dicts/menus are the {@link #ORG_NAME_PREFIXES}/{@link #ORG_CODE_PREFIXES}
 * /{@link #DEP_CODE_PREFIXES}/{@link #CATAL_NAME_PREFIXES}/{@link #MENU_TITLE_PREFIXES}
 * families. {@code AuthLoginTests} deliberately stays on the REAL admin (it tests the
 * seeded-admin login contract itself) and is the one accepted residue - roughly two
 * {@code public.log_sign} rows per run under {@code admin}.</p>
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

    // ===================== password transport encryption (task 2.2) =====================
    // The clean cutover (password-transport-encryption-api) rejects plaintext password
    // fields with A0400, so EVERY request body a suite sends must carry SM2 ciphertext.
    // These statics mirror the frontend flow exactly: sm-crypto doEncrypt(msg, pub, 1)
    // output + the "04" point marker = the wire format (coordination D4); the values
    // match application-test.yml and PasswordCipherHelperTest's fixed vector.

    /** The fixed test scalar D - identical to application-test.yml (TEST ONLY). */
    static final String TEST_SM2_PRIVATE_D =
            "56d66802243a734aba690c6f2553ca69c87e8e78f9b282a19686d7fe1683cd83";

    /** The derived public point of TEST_SM2_PRIVATE_D (04||x||y, 130 hex chars). */
    static final String TEST_SM2_PUBLIC_POINT =
            "0440025ec765361018ae5c5e603f9e55354e5aa2e1fcab3d541d7e6f560b40039f7d"
                    + "a1972abc804eb41b0332bb04f348b3baa7009091d150c4a77763efdfd8d553";

    /**
     * Encrypts a raw password into the WIRE format the backend's
     * {@code PasswordCipherHelper} accepts (SM2 C1C3C2 hex, standard 04-prefixed point
     * encoding) under the fixed test keypair. Hutool emits the 04 marker itself - no
     * manual prepend needed here (unlike sm-crypto, whose native output is markerless).
     */
    static String encryptTestPassword(String raw) {
        return cn.hutool.crypto.SmUtil.sm2(TEST_SM2_PRIVATE_D, TEST_SM2_PUBLIC_POINT)
                .encryptHex(raw, cn.hutool.crypto.asymmetric.KeyType.PublicKey).toLowerCase();
    }

    /**
     * Encrypted login request body for ANY account - the drop-in replacement for the
     * former hand-built {@code {"username":..,"password":<plaintext>}} literals.
     */
    static String loginBody(String username, String rawPassword) {
        return "{\"username\":\"" + username + "\",\"password\":\""
                + encryptTestPassword(rawPassword) + "\"}";
    }

    /**
     * Fixture-account namespace owned by the tests, sweep-targetable. REAL users must
     * NEVER take these prefixes - the {@code @AfterAll} sweep hard-deletes every row
     * matching them, live or soft-deleted (user ruling 2026-11-07). {@code b2*} covers
     * the b2-prefixed sign/log fixture accounts; {@code no.such.user.} is
     * AuthLoginTests' unknown-account probe (its failure rows are swept even though that
     * suite stays on the real admin); {@code ua-probe} is AuthUserAgentResilienceTests'
     * probe account. When a suite adds a new fixture-account family its prefix lands
     * HERE - the sweep is the SAFETY NET for interrupted runs, never the primary
     * cleanup (suites delete their own fixtures inline). 2026-11-12 backfill:
     * {@code delete_u_} (user-edit-delete-profile suites), {@code smoke_profile_}
     * (profile smoke fixtures) and {@code b3q.} (log-query b3 log-fixture accounts)
     * had leaked unregistered rows into the shared DB - the missed-registration
     * lesson repeating; swept manually and registered after the fact.
     */
    static final String[] ACCOUNT_PREFIXES = { "b2", "dup_u_", "pw_u_", "en_u_", "replace_u_",
            "role_del_u_", "audit_u_", "single_u_", "noperm_u_", "city_u_", "menuperm_u_",
            "btnmatrix_", "no.such.user.", "mgmt_u_", "audit_user_", "wsdel_u_", "wsgrant_u_",
            "wsboot_u_", "wstarget_u_", "wsnoperm_u_", "wsdual_u_", "mp.smoke.", "itadmin_",
            "delete_u_", "smoke_profile_", "b3q." };

    /**
     * Fixture-role namespace owned by the tests, sweep-targetable (the Chinese literals
     * match the fixture role names in AuthRoleMenuTests/AuthPermEnforcementTests; the
     * three sign-log role families stay LISTED after AuthSignLogQueryTests' deletion
     * (audit-log-unify-api b4) so the sweep still purges their historical rows from
     * the shared DB). REAL roles must NEVER take these prefixes - the sweep
     * hard-deletes live roles matching them, together with their role_menu bindings;
     * bound fixture users go with the account sweep.
     */
    static final String[] ROLE_NAME_PREFIXES = { "报告审核员_", "单页角色_", "无权角色_",
            "无权菜单角色_", "denied_role_", "临时角色", "全量替换角色_", "按钮矩阵", "矩阵目标用户",
            "仅日志角色_", "登录日志查询角色_", "仅登录日志角色_", "无权城市角色_",
            "回显角色", "名单过滤_", "启停角色_" };

    /**
     * Fixture-org namespace (hospital campuses + throwaway probe orgs), sweep-targetable.
     * REAL orgs must NEVER take these prefixes.
     */
    static final String[] ORG_NAME_PREFIXES = { "守卫院区_", "院区子机构_", "东院区", "占用院区_",
            "状态院区_", "非法状态院区_", "第二根_", "科室编码A院区_", "科室编码B院区_",
            "分页A院区_", "分页B院区_", "科室删除院区_", "停用院区_", "迁出院区_",
            "编码不可变", "按钮矩阵院区", "按钮矩阵机构", "按钮矩阵科室", "日志机构_",
            "UA探针机构_", "ut.smoke.", "设备院区_", "诊室院区_" };

    /**
     * Fixture-org CODE namespace (the V16 codes are set explicitly by some fixtures;
     * generated CAMPUS- codes of swept orgs die with their rows, no prefix needed).
     */
    static final String[] ORG_CODE_PREFIXES = { "UT-SMOKE-", "CAMPUS_T_" };

    /**
     * Fixture-department namespace (codes; the display names ride the owning campus).
     */
    static final String[] DEP_CODE_PREFIXES = { "ut_mtx_dep_", "dep_guard_", "dep_shared_",
            "dep_immutable_", "dep_page_", "dep_delete_", "dep_disabled_", "dep_move_",
            "dep_hospital_", "ut_dev_dep_", "ut_room_dep_" };

    /**
     * Fixture-device namespace (codes; org-device-room-api suites). REAL devices must
     * NEVER take these prefixes - the sweep hard-deletes every live or soft-deleted row.
     */
    static final String[] DEVICE_CODE_PREFIXES = { "ut_dev_" };

    /**
     * Fixture-room namespace (names; org-device-room-api suites - the room code is an
     * optional label, the NAME is the operational key the suites timestamp).
     */
    static final String[] ROOM_NAME_PREFIXES = { "ut_room_" };

    /**
     * Fixture-dict namespace (catalog display names; details ride their catalog via
     * ca_id, swept with it).
     */
    static final String[] CATAL_NAME_PREFIXES = { "数据层往返目录_", "明细目录_", "复用目录_",
            "批量二目录_", "按钮矩阵目录" };

    /**
     * Fixture-menu namespace (titles and perms strings; button perms carry the
     * {@code ut.} needle the API denies through).
     */
    static final String[] MENU_TITLE_PREFIXES = { "ut", "按钮矩阵", "ut矩阵", "登录日志_b2probe_" };
    static final String[] MENU_PERMS_PREFIXES = { "ut.", "ut.mtx:" };

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
     * Per-class exit: one JDBC transaction that PHYSICALLY deletes the run's residue
     * from the shared database (user ruling 2026-11-07: soft-deleted sweep rows pile up
     * and poison bug triage - the sweep is a hard {@code DELETE}), in dependency-safe
     * order (bindings before owners, so the V1 FKs never trip):
     * <ol>
     *   <li>{@code public.log_sign} / {@code public.log_info}: the run account's, the
     *       fixture families', and {@code ua-probe}'s rows (live or soft-deleted - a
     *       past run's dead rows go too);</li>
     *   <li>{@code system.role_menu} bindings of the fixture roles AND fixture menus;</li>
     *   <li>{@code system.user_role} bindings of the run/fixture users;</li>
     *   <li>{@code system.user_info}: the run and fixture users themselves;</li>
     *   <li>{@code system.role_info}: the fixture roles themselves;</li>
     *   <li>{@code system.menu_info}: the fixture menus themselves;</li>
     *   <li>{@code public.dict_detail} / {@code public.dict_catal}: fixture details
     *       ride their swept catalogs (ca_id);</li>
     *   <li>{@code system.org_room} / {@code system.org_device}: the registry fixture
     *       family (org-device-room-api; zero physical FKs, swept before their owners);</li>
     *   <li>{@code system.org_dep}: fixture departments (before their campus);</li>
     *   <li>{@code system.org_info}: the fixture orgs themselves.</li>
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
     * The password travels ENCRYPTED since the transport-encryption cutover (task 2.2).
     */
    static String runAdminLoginBody() {
        doEnsureRunAdminIdentity();
        return loginBody(RUN_ACCOUNT, ADMIN_TEST_PASSWORD);
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

    /**
     * The sweep itself: hard {@code DELETE}s, one transaction, dependency-safe order
     * (bindings before owners - V1 FKs: role_menu → role_info/menu_info,
     * user_role → user_info/role_info, dict_detail → dict_catal by convention,
     * org_dep → org_info). No {@code deleted = 0} filter on the sweep targets: rows a
     * previous run soft-deleted before an interrupted cleanup get swept all the same -
     * THAT backlog is what the 2026-11-07 ruling exists to purge.
     */
    private static void doSweepResidue() throws SQLException {
        try (Connection connection = openTestServerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // 1. Log/audit rows of the run + fixture families (no audit block - plain
                //    DELETE, no update_time touch). The log_sign sweep is guarded:
                //    V25 (audit-log-unify-api) DROPPED the family, so the relation may
                //    legitimately be absent on every current database (full re-target of
                //    the sign suites is batch 4 - until then the sweep must survive both
                //    shapes).
                int signRows = 0;
                java.sql.ResultSet signTable = statement.executeQuery(
                        "SELECT to_regclass('public.log_sign')");
                if (signTable.next() && signTable.getObject(1) != null) {
                    signRows = statement.executeUpdate(
                            "DELETE FROM public.log_sign WHERE ("
                                    + accountResiduePredicate("account") + ")");
                }
                signTable.close();
                int infoRows = statement.executeUpdate(
                        "DELETE FROM public.log_info WHERE ("
                                + accountResiduePredicate("account") + ")");
                // 2. role_menu bindings of fixture roles AND fixture menus (V1 FK).
                int roleMenuRows = statement.executeUpdate(
                        "DELETE FROM system.role_menu WHERE role_id IN (SELECT id FROM "
                                + "system.role_info WHERE (" + roleResiduePredicate("name") + ")) "
                        + "OR menu_id IN (SELECT id FROM system.menu_info WHERE ("
                                + menuTitleResiduePredicate("title") + " OR "
                                + prefixLikePredicate("perms", MENU_PERMS_PREFIXES) + "))");
                // 3. user_role bindings of the run/fixture users.
                int userRoleRows = statement.executeUpdate(
                        "DELETE FROM system.user_role WHERE user_id IN (SELECT id FROM "
                                + "system.user_info WHERE ("
                                + accountResiduePredicate("username") + "))");
                // 4. The run and fixture users themselves.
                int userRows = statement.executeUpdate(
                        "DELETE FROM system.user_info WHERE ("
                                + accountResiduePredicate("username") + ")");
                // 5. The fixture roles themselves.
                int roleRows = statement.executeUpdate(
                        "DELETE FROM system.role_info WHERE (" + roleResiduePredicate("name") + ")");
                // 6. The fixture menus themselves.
                int menuRows = statement.executeUpdate(
                        "DELETE FROM system.menu_info WHERE ("
                                + menuTitleResiduePredicate("title")
                                + " OR " + prefixLikePredicate("perms", MENU_PERMS_PREFIXES) + ")");
                // 7. Fixture dict details ride their swept catalogs (ca_id), then catalogs.
                int detailRows = statement.executeUpdate(
                        "DELETE FROM public.dict_detail WHERE ca_id IN (SELECT id FROM "
                                + "public.dict_catal WHERE ("
                                + prefixLikePredicate("name", CATAL_NAME_PREFIXES) + "))");
                int catalRows = statement.executeUpdate(
                        "DELETE FROM public.dict_catal WHERE ("
                                + prefixLikePredicate("name", CATAL_NAME_PREFIXES) + ")");
                // 8. Fixture rooms/devices of the registry family (org-device-room-api,
                //    zero physical FKs - but swept BEFORE their owners so the logical
                //    references die with the owning rows): rooms by name prefix or
                //    fixture-org scope, devices by code prefix or fixture-org scope.
                int roomRows = statement.executeUpdate(
                        "DELETE FROM system.org_room WHERE ("
                                + prefixLikePredicate("name", ROOM_NAME_PREFIXES) + ") "
                        + "OR oid IN (SELECT id FROM system.org_info WHERE ("
                                + orgResiduePredicate() + "))");
                int deviceRows = statement.executeUpdate(
                        "DELETE FROM system.org_device WHERE ("
                                + prefixLikePredicate("code", DEVICE_CODE_PREFIXES) + ") "
                        + "OR oid IN (SELECT id FROM system.org_info WHERE ("
                                + orgResiduePredicate() + "))");
                // 9. Fixture departments before their campus (org_dep.org_id FK).
                int depRows = statement.executeUpdate(
                        "DELETE FROM system.org_dep WHERE ("
                                + prefixLikePredicate("code", DEP_CODE_PREFIXES) + ") "
                        + "OR org_id IN (SELECT id FROM system.org_info WHERE ("
                                + orgResiduePredicate() + "))");
                // 10. The fixture orgs themselves.
                int orgRows = statement.executeUpdate(
                        "DELETE FROM system.org_info WHERE (" + orgResiduePredicate() + ")");
                connection.commit();
                System.out.println("[system-test-hook] hard-swept run residue - log_sign=" + signRows
                        + " log_info=" + infoRows + " role_menu=" + roleMenuRows
                        + " user_role=" + userRoleRows + " user_info=" + userRows
                        + " role_info=" + roleRows + " menu_info=" + menuRows
                        + " dict_detail=" + detailRows + " dict_catal=" + catalRows
                        + " org_room=" + roomRows + " org_device=" + deviceRows
                        + " org_dep=" + depRows + " org_info=" + orgRows);
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
        return prefixLikePredicate(column, ROLE_NAME_PREFIXES);
    }

    /** SQL fragment over system.org_info for the fixture org namespace (name + code). */
    private static String orgResiduePredicate() {
        return "(" + prefixLikePredicate("name", ORG_NAME_PREFIXES)
                + " OR " + prefixLikePredicate("code", ORG_CODE_PREFIXES) + ")";
    }

    /** SQL fragment matching the fixture-menu title namespace (LIKE prefixes, escaped). */
    private static String menuTitleResiduePredicate(String column) {
        return prefixLikePredicate(column, MENU_TITLE_PREFIXES);
    }

    /**
     * SQL fragment: {@code column LIKE 'prefix%' ESCAPE '\\'} OR-joined over the given
     * fixture-prefix family. Every prefix escapes to a literal-prefix match - no
     * wildcard accidents.
     */
    private static String prefixLikePredicate(String column, String[] prefixes) {
        StringBuilder predicate = new StringBuilder();
        for (int i = 0; i < prefixes.length; i++) {
            if (i > 0) {
                predicate.append(" OR ");
            }
            predicate.append(column).append(" LIKE '")
                    .append(likePrefixPattern(prefixes[i])).append("%' ESCAPE '\\'");
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

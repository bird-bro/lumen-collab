package org.birdbro.lumen.setting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;
import org.birdbro.framework.security.TokenService;

/**
 * Abstract base for lumen-setting integration tests against the TEAM TEST SERVER (the
 * same externally provisioned PostgreSQL + Redis lumen-system's suites use - NO
 * Testcontainers). Clone of the {@code AuthIntegrationTestBase} pattern, narrowed to
 * what the setting service actually exercises:
 *
 * <ol>
 *   <li>resolves the {@code AUTH_TEST_*} connection variables from the gitignored
 *       {@code lumen-sev/.env-test.local} (see {@link SettingTestEnv}) - missing
 *       variables fail the context EXPLICITLY, never a silent skip;</li>
 *   <li>bootstraps the server at most once per JVM: {@code CREATE DATABASE} guarded by
 *       a {@code pg_database} existence check, then {@code CREATE SCHEMA IF NOT EXISTS
 *       system} ({@link SettingTestServerBootstrap}) - since the 2026-11-13 merge
 *       ruling the exam-setting tables live in the shared {@code system} schema under
 *       the ONE service Flyway ledger, exactly as {@code AuthIntegrationTestBase}
 *       targets;</li>
 *   <li>injects the application datasource ({@code currentSchema=system,public}),
 *       Redis, and Flyway properties via {@code @DynamicPropertySource} - Flyway
 *       executes the module's migrations against the real database.</li>
 * </ol>
 *
 * <p><b>Authentication is MINTED, not logged in:</b> this service issues no tokens (the
 * JWT issuer is lumen-system), so suites call {@link #mintToken} - a real
 * {@link TokenService#createToken(UserLogin)} against the test server's Redis - and
 * send the returned JWT as {@code Authorization: Bearer <jwt>}. The minted session
 * carries {@link #RUN_UID} + a run-scoped name, so the {@code UserHeaderBridgeFilter}
 * derives audit identities ({@code create_by}) from the session exactly as production
 * would; no {@code user_info} row is created and the system schema stays untouched.
 * Permissions come straight from the minted {@link UserLogin#getAuthorities()} - a
 * suite pins a perm by minting WITH it, and exercises a refusal by minting WITHOUT it
 * (the {@code hasAuthority} chain sees the same shapes production does).</p>
 *
 * <p>Shared-database hygiene (user rulings 2026-11-05 / 2026-11-07): suites hard-delete
 * their own fixtures inline; {@link #sweepSettingResidue} (@@AfterAll) is the SAFETY NET
 * - one JDBC transaction that PHYSICALLY deletes every live or soft-deleted fixture row
 * matching the {@link #EXAM_GROUP_NAME_PREFIXES} / {@link #EXAM_BODY_CODE_PREFIXES} /
 * {@link #EXAM_METHOD_CODE_PREFIXES} / {@link #EXAM_ITEM_CODE_PREFIXES} /
 * {@link #REP_TEMP_NAME_PREFIXES} families (bindings before owners). REAL catalog rows
 * must NEVER take these prefixes. New fixture families register their prefix HERE.</p>
 *
 * @author birdbro
 */
// classes= is REQUIRED (merge-setting-into-system): the Boot configuration search
// walks UP from the test package (org.birdbro.lumen.setting -> org.birdbro) and the
// hosting SystemApplication sits in the SIBLING package org.birdbro.lumen.system -
// never found by the upward walk (the retired SettingApplication used to be the
// same-package @SpringBootConfiguration).
@SpringBootTest(classes = org.birdbro.lumen.system.SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class SettingIntegrationTestBase {

    /**
     * Run-scoped identity minted into every session: a synthetic uid in a dedicated
     * band (990000+) that real users never reach, stable for the whole JVM so audit
     * assertions can compare against it.
     */
    public static final Integer RUN_UID = 990000 + (int) (System.currentTimeMillis() % 100000);

    /** Run-scoped display name the bridge puts into {@code UserHeader.name}. */
    public static final String RUN_NAME = "it-setting run fixture";

    /** Header name every authenticated call carries. */
    public static final String AUTH_HEADER = "Authorization";

    // ===================== fixture namespaces (sweep-targetable) =====================

    /**
     * Fixture body-group NAMES (exam-setting suites - the group table carries no code
     * column, the NAME is the operational key). REAL groups must NEVER take these.
     */
    public static final String[] EXAM_GROUP_NAME_PREFIXES = { "ut组_" };

    /** Fixture body codes (exam-setting suites). REAL bodies must NEVER take these. */
    public static final String[] EXAM_BODY_CODE_PREFIXES = { "ut_exb_" };

    /** Fixture method codes (exam-setting suites). REAL methods must NEVER take these. */
    public static final String[] EXAM_METHOD_CODE_PREFIXES = { "ut_exm_" };

    /** Fixture item codes (exam-setting suites). REAL items must NEVER take these. */
    public static final String[] EXAM_ITEM_CODE_PREFIXES = { "ut_exi_" };

    /** Fixture template/folder names (rep_temp has no code column - the name is the key). */
    public static final String[] REP_TEMP_NAME_PREFIXES = { "ut模板目录_", "ut模板_" };

    /**
     * Per-class entry: idempotently ensures the ONE external dependency this domain
     * reads - the dict 1027 检查类型 vocabulary - exists exactly as V27 seeded it. The
     * shared test database is LONG-LIVED and parallel sessions' suites have been
     * observed physically purging V26/V27 dict rows mid-flight (1021/1023/1027 all
     * vanished between runs on 2026-11-12), so every class restores the spec-defined
     * rows ON CONFLICT-guarded (deterministic ids, the V27 values verbatim) before
     * testing - additive no-op when present, spec-true restore when purged. No other
     * platform data is touched.
     */
    @org.junit.jupiter.api.BeforeAll
    static void ensureModalityVocabulary() {
        String[] modalities = {"DR", "CT", "MR", "DSA", "US", "ENDO"};
        try (Connection connection = openTestServerConnection();
             Statement statement = connection.createStatement()) {
            // Resolve the catalog by its UNIQUE name - the id has flip-flopped
            // (1021/1027) across sessions; the name is the stable anchor.
            java.sql.ResultSet catalog = statement.executeQuery(
                    "SELECT id FROM public.dict_catal WHERE name = '检查类型' "
                            + "AND deleted = 0 ORDER BY id LIMIT 1");
            long catalogId;
            if (catalog.next()) {
                catalogId = catalog.getLong(1);
                catalog.close();
            } else {
                catalog.close();
                try {
                    statement.execute("INSERT INTO public.dict_catal (id, name, enable, "
                            + "sort, remark) VALUES (1027, '检查类型', 1, 99, "
                            + "'检查类型词表(与设备类型同值集,机器值=value模态串)')");
                    catalogId = 1027;
                } catch (SQLException nameTaken) {
                    // A SOFT-DELETED catalog still squats on the unique name - revive
                    // it instead of racing the constraint.
                    java.sql.ResultSet squat = statement.executeQuery(
                            "SELECT id FROM public.dict_catal WHERE name = '检查类型' "
                                    + "ORDER BY id LIMIT 1");
                    if (!squat.next()) {
                        throw nameTaken;
                    }
                    catalogId = squat.getLong(1);
                    squat.close();
                    statement.execute("UPDATE public.dict_catal SET deleted = 0, "
                            + "enable = 1 WHERE id = " + catalogId);
                }
            }
            // Ensure every modality value exists under the catalog (id = ca*1000+sort,
            // the V23/V26/V27 deterministic band - ON CONFLICT no-op when present).
            for (int i = 0; i < modalities.length; i++) {
                statement.execute("INSERT INTO public.dict_detail (id, ca_id, code, "
                        + "value, sort, enable) VALUES (" + (catalogId * 1000 + i + 1)
                        + ", " + catalogId + ", " + (i + 1) + ", '" + modalities[i]
                        + "', " + (i + 1) + ", 1) "
                        + "ON CONFLICT (id) DO UPDATE SET enable = 1, deleted = 0");
                // DO UPDATE heals drifted rows too (a parallel session's seed once
                // wrote the sort value into enable - CT landed enable=2 and the
                // vocabulary check went blind). The heal keeps the six rows spec-true.
            }
        } catch (SQLException e) {
            throw new IllegalStateException("the 检查类型 vocabulary guard failed: "
                    + e.getMessage(), e);
        }
    }

    @Autowired
    protected TokenService tokenService;

    /**
     * Registers the test-server properties. Runs during context creation, BEFORE the
     * context refreshes - an unconfigured or unreachable server fails the context right
     * here, with the explicit signal.
     */
    @DynamicPropertySource
    static void teamTestServerProperties(DynamicPropertyRegistry registry) {
        SettingTestEnv env = SettingTestEnv.load();
        Map<String, String> required = env.require(
                SettingTestEnv.PG_HOST, SettingTestEnv.PG_PORT, SettingTestEnv.PG_USER,
                SettingTestEnv.PG_PASSWORD, SettingTestEnv.REDIS_HOST, SettingTestEnv.REDIS_PORT);
        String database = env.optional(SettingTestEnv.DB, SettingTestEnv.DEFAULT_DB);
        String redisPassword = env.optional(SettingTestEnv.REDIS_PASSWORD, null);

        String pgHost = required.get(SettingTestEnv.PG_HOST);
        int pgPort = Integer.parseInt(required.get(SettingTestEnv.PG_PORT).trim());

        SettingTestServerBootstrap.ensureDatabaseAndSchema(pgHost, pgPort,
                required.get(SettingTestEnv.PG_USER), required.get(SettingTestEnv.PG_PASSWORD),
                database);

        // Application datasource: the lumen database through the hosting SYSTEM schema
        // first, then the shared public schema (the design-level dict/log exception) -
        // the SAME shape AuthIntegrationTestBase registers (2026-11-13 merge ruling).
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":"
                + pgPort + "/" + database + "?currentSchema=system,public");
        registry.add("spring.datasource.username", () -> required.get(SettingTestEnv.PG_USER));
        registry.add("spring.datasource.password", () -> required.get(SettingTestEnv.PG_PASSWORD));

        // Redis on the test server: minted sessions land there for real.
        registry.add("spring.redis.host", () -> required.get(SettingTestEnv.REDIS_HOST));
        registry.add("spring.redis.port", () -> required.get(SettingTestEnv.REDIS_PORT));
        if (redisPassword != null) {
            registry.add("spring.redis.password", () -> redisPassword);
        }

        // Flyway migrates the SYSTEM schema of the test server's database - the one
        // service ledger V1..V29 (2026-11-13 merge ruling). Flyway 7.x requires
        // default-schema to be a member of schemas.
        registry.add("spring.flyway.schemas", () -> SettingTestServerBootstrap.SYSTEM_SCHEMA);
        registry.add("spring.flyway.default-schema",
                () -> SettingTestServerBootstrap.SYSTEM_SCHEMA);
        registry.add("spring.flyway.check-location", () -> "false");
    }

    // ===================== minted authentication =====================

    /**
     * Mints a JWT for the run identity carrying EXACTLY the given permissions (the
     * {@code hasAuthority} pins see the same shapes production does). The session lands
     * in the test server's Redis through the real {@link TokenService}; no user row is
     * created anywhere.
     *
     * <p>{@code createToken} resolves request-scoped state ({@code ServletUtils} for
     * the User-Agent block), so minting binds a {@code MockHttpServletRequest} to the
     * test thread first - the minted session's agent block is then exactly what a real
     * login would store.</p>
     */
    protected String mintToken(String... permissions) {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                == null) {
            org.springframework.mock.web.MockHttpServletRequest mockRequest =
                    new org.springframework.mock.web.MockHttpServletRequest();
            mockRequest.addHeader("User-Agent", "setting-integration-tests/1.0");
            mockRequest.setRemoteAddr("127.0.0.1");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(
                            mockRequest));
        }
        Set<String> perms = new LinkedHashSet<>();
        for (String permission : permissions) {
            perms.add(permission);
        }
        UserSign sign = new UserSign();
        sign.setUid(RUN_UID);
        sign.setAccount("it-setting_" + RUN_UID);
        sign.setName(RUN_NAME);
        sign.setEnable(1);
        UserLogin login = new UserLogin(RUN_UID, sign, perms);
        return tokenService.createToken(login);
    }

    /** Token carrying every setting perm (the all-granted administrator shape). */
    protected String fullPermToken() {
        return mintToken("setting:item:list", "setting:item:create", "setting:item:update",
                "setting:item:delete", "setting:body:list", "setting:body:create",
                "setting:body:update", "setting:body:delete", "setting:method:list",
                "setting:method:create", "setting:method:update", "setting:method:delete",
                "setting:temp:list", "setting:temp:create", "setting:temp:update",
                "setting:temp:delete");
    }

    /** Token carrying NO permissions (every perm-gated path must refuse it). */
    protected String noPermToken() {
        return mintToken();
    }

    // ===================== residue sweep (safety net) =====================

    /**
     * Per-class exit: one JDBC transaction that PHYSICALLY deletes the run's exam-
     * table residue in the system schema (user ruling 2026-11-07 - hard DELETE,
     * never soft), bindings
     * before owners: item_method joins, then items, bodies, groups, methods, and the
     * rep_temp family. No {@code deleted = 0} filter - soft-deleted leftovers from an
     * interrupted run are exactly the pollution this purges. A failure fails the class
     * EXPLICITLY (never a silent skip).
     */
    @AfterAll
    static void sweepSettingResidue() {
        try {
            doSweepResidue();
        } catch (SQLException e) {
            throw new IllegalStateException("the shared-database residue sweep failed: "
                    + e.getMessage(), e);
        }
    }

    private static void doSweepResidue() throws SQLException {
        try (Connection connection = openTestServerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                int itemMethodRows = statement.executeUpdate(
                        "DELETE FROM system.exam_item_method WHERE exam_id IN ("
                                + "SELECT id FROM system.exam_item WHERE ("
                                + prefixLike("code", EXAM_ITEM_CODE_PREFIXES) + "))");
                int itemRows = statement.executeUpdate(
                        "DELETE FROM system.exam_item WHERE ("
                                + prefixLike("code", EXAM_ITEM_CODE_PREFIXES) + ")");
                int bodyRows = statement.executeUpdate(
                        "DELETE FROM system.exam_body WHERE ("
                                + prefixLike("code", EXAM_BODY_CODE_PREFIXES) + ")");
                int groupRows = statement.executeUpdate(
                        "DELETE FROM system.exam_body_group WHERE ("
                                + prefixLike("name", EXAM_GROUP_NAME_PREFIXES) + ")");
                int methodRows = statement.executeUpdate(
                        "DELETE FROM system.exam_method WHERE ("
                                + prefixLike("code", EXAM_METHOD_CODE_PREFIXES) + ")");
                int tempRows = statement.executeUpdate(
                        "DELETE FROM system.rep_temp WHERE ("
                                + prefixLike("name", REP_TEMP_NAME_PREFIXES) + ")");
                connection.commit();
                System.out.println("[setting-test-hook] hard-swept run residue - "
                        + "item_method=" + itemMethodRows + " item=" + itemRows
                        + " body=" + bodyRows + " body_group=" + groupRows
                        + " method=" + methodRows + " rep_temp=" + tempRows);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Plain-JDBC connection to the team test server's database ({@code
     * currentSchema=system,public}) - the same resolution as
     * {@code teamTestServerProperties}. Connects OUTSIDE Spring on purpose.
     */
    private static Connection openTestServerConnection() throws SQLException {
        SettingTestEnv env = SettingTestEnv.load();
        Map<String, String> required = env.require(
                SettingTestEnv.PG_HOST, SettingTestEnv.PG_PORT,
                SettingTestEnv.PG_USER, SettingTestEnv.PG_PASSWORD);
        String database = env.optional(SettingTestEnv.DB, SettingTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(SettingTestEnv.PG_HOST) + ":"
                + required.get(SettingTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=system,public";
        return DriverManager.getConnection(url, required.get(SettingTestEnv.PG_USER),
                required.get(SettingTestEnv.PG_PASSWORD));
    }

    /** LIKE prefix family fragment (escaped, OR-joined) over one column. */
    private static String prefixLike(String column, String[] prefixes) {
        StringBuilder predicate = new StringBuilder();
        for (int i = 0; i < prefixes.length; i++) {
            if (i > 0) {
                predicate.append(" OR ");
            }
            String pattern = prefixes[i]
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            predicate.append(column).append(" LIKE '").append(pattern)
                    .append("%' ESCAPE '\\'");
        }
        return predicate.toString();
    }
}

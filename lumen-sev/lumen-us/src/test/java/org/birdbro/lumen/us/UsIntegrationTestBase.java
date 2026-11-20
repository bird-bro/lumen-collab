package org.birdbro.lumen.us;

import java.util.Map;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Abstract base for lumen-us integration tests against the TEAM TEST SERVER
 * (us-station-registration-sev task 1.1, design D8 - mirrors lumen-patient's
 * PatientIntegrationTestBase shape against the shared team server). PostgreSQL + Redis
 * are externally provisioned - NO Testcontainers. The base:
 *
 * <ol>
 *   <li>resolves the {@code AUTH_TEST_*} connection variables from the gitignored
 *       {@code lumen-sev/.env-test.local} (see {@link UsTestEnv}) - the SAME file and
 *       the SAME key names every lumen module uses (the platform-wide test contract);
 *       missing required variables fail the context EXPLICITLY, never a silent
 *       skip;</li>
 *   <li>bootstraps the server at most once per JVM: {@code CREATE DATABASE} guarded by
 *       a {@code pg_database} existence check, then {@code CREATE SCHEMA IF NOT EXISTS
 *       us} ({@link UsTestServerBootstrap});</li>
 *   <li>injects the application datasource ({@code currentSchema=us,public} +
 *       {@code tcpKeepAlive=true}, the shared-server transport resilience convention),
 *       Redis, and Flyway properties via {@code @DynamicPropertySource} - so Flyway
 *       executes the us ledger migrations against the real database and the token
 *       state lands in the server's Redis.</li>
 * </ol>
 *
 * <p>{@code @ActiveProfiles("test")} overrides the dev default of application.yml, so
 * the gitignored application-dev.yml is not loaded in tests - the dynamic properties
 * below are the ONLY datasource source of truth. {@code RANDOM_PORT} web environment
 * (batch 3, us-station-registration-sev task 3.2 - the {@code
 * PatientIntegrationTestBase} precedent): the registration surface is REAL HTTP under
 * the real security chain, proven through {@code TestRestTemplate}.</p>
 *
 * <p>The Feign seam stubs (batch 3, design D8 - the patient base precedent): the
 * lumen-api clients this service consumes ({@code PatientApi} / {@code NumberApi}) are
 * {@code @MockBean}'d HERE so EVERY suite shares ONE context - no counterpart service
 * runs in this JVM, and the adapters must never fabricate an identity or a number, so
 * the seam is stubbed with LENIENT unique-band defaults (distinctive {@code us3} bands
 * that never collide with real patient rows); scenario suites override in their own
 * {@code @BeforeEach}, which runs after this base method. The platform MECHANISM is
 * proven patient-side/system-side (PatientInternalApiTests / NumberIssuerServiceTests).</p>
 *
 * <p>Deliberately MINIMAL (batch 1): no run-admin cloning and no residue sweep - the
 * boot/schema suites need no authenticated identity and write no fixtures. The base is
 * extensible: {@code UsApiTestBase} layers the run-scoped identity + hard-DELETE sweep
 * for the HTTP suites (user ruling 2026-11-07).</p>
 *
 * @author birdbro
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class UsIntegrationTestBase {

    /**
     * The stub identity band (batch 3): the stubbed {@code patUid} values live in a
     * distinctive per-JVM band (973000+, never a real patient_master.id) so the
     * order-linkage assertions and the residue sweep key on values no other suite can
     * write. The jsonb/uid references are LOGICAL (no FK) - no patient row needs to
     * exist behind a stubbed uid.
     */
    public static final int STUB_UID_BASE = 973000 + (int) (System.currentTimeMillis() % 25000);

    /** The stub accession prefix (batch 3) - a distinctive sweep key too. */
    public static final String STUB_ACCESSION_PREFIX = "US3-";

    private static final java.util.concurrent.atomic.AtomicInteger STUB_SEQ =
            new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * The Feign seam stubs (design D8): lenient unique-band defaults every suite
     * inherits - the adapters' counterparties never answer in this JVM.
     */
    @org.springframework.boot.test.mock.mockito.MockBean
    protected org.birdbro.lumen.api.patient.PatientApi patientApi;

    @org.springframework.boot.test.mock.mockito.MockBean
    protected org.birdbro.lumen.api.number.NumberApi numberApi;

    @org.junit.jupiter.api.BeforeEach
    void stubFeignCounterparties() {
        java.util.concurrent.atomic.AtomicInteger seq = STUB_SEQ;
        org.mockito.Mockito.when(patientApi.resolveOrRegister(
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> org.birdbro.common.core.R.ofSuccess(
                        org.birdbro.lumen.api.patient.PatientResolveOrRegisterResult.builder()
                                .result(org.birdbro.lumen.api.patient
                                        .PatientResolveOrRegisterResult.RESULT_MATCHED)
                                .identity(org.birdbro.lumen.api.patient
                                        .PatientIdentityResponse.builder()
                                        .patUid(STUB_UID_BASE + seq.incrementAndGet())
                                        .patId(STUB_ACCESSION_PREFIX + "PAT" + seq.get())
                                        .name("us3默认档案" + seq.get())
                                        .gender(1)
                                        .state("RESOLVED")
                                        .build())
                                .build()));
        org.mockito.Mockito.when(patientApi.lookup(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> org.birdbro.common.core.R.ofSuccess(
                        org.birdbro.lumen.api.patient.PatientLookupResult.builder()
                                .result(org.birdbro.lumen.api.patient
                                        .PatientLookupResult.RESULT_NONE)
                                .build()));
        org.mockito.Mockito.when(numberApi.issue(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> org.birdbro.common.core.R.ofSuccess(
                        org.birdbro.lumen.api.number.NumberIssueResponse.builder()
                                .number(STUB_ACCESSION_PREFIX + seq.incrementAndGet())
                                .build()));
    }

    /**
     * Registers the test-server properties. Runs during context creation, BEFORE the
     * context refreshes - so an unconfigured or unreachable server fails the context
     * right here, with the explicit signal.
     */
    @DynamicPropertySource
    static void teamTestServerProperties(DynamicPropertyRegistry registry) {
        UsTestEnv env = UsTestEnv.load();
        Map<String, String> required = env.require(
                UsTestEnv.PG_HOST, UsTestEnv.PG_PORT, UsTestEnv.PG_USER,
                UsTestEnv.PG_PASSWORD, UsTestEnv.REDIS_HOST, UsTestEnv.REDIS_PORT);
        String database = env.optional(UsTestEnv.DB, UsTestEnv.DEFAULT_DB);
        String redisPassword = env.optional(UsTestEnv.REDIS_PASSWORD, null);

        String pgHost = required.get(UsTestEnv.PG_HOST);
        int pgPort = parseIntPort(UsTestEnv.PG_PORT, required.get(UsTestEnv.PG_PORT));
        String pgUser = required.get(UsTestEnv.PG_USER);
        String pgPassword = required.get(UsTestEnv.PG_PASSWORD);
        String redisHost = required.get(UsTestEnv.REDIS_HOST);
        int redisPort = parseIntPort(UsTestEnv.REDIS_PORT, required.get(UsTestEnv.REDIS_PORT));

        UsTestServerBootstrap.ensureDatabaseAndSchema(pgHost, pgPort, pgUser, pgPassword, database);

        // Application datasource: the lumen database through the us schema first, then
        // the shared public schema. tcpKeepAlive rides the URL (the 2026-11-18
        // shared-server transport convention). NO stringtype=unspecified: the jsonb
        // writes (kernel patient snapshot / exam items) go through the kernel's
        // Types.OTHER handler - a URL flag here would mask a handler-route regression.
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":"
                + pgPort + "/" + database + "?currentSchema=us,public&tcpKeepAlive=true");
        registry.add("spring.datasource.username", () -> pgUser);
        registry.add("spring.datasource.password", () -> pgPassword);

        // Redis on the test server: framework shared beans (token state et al.) are real.
        registry.add("spring.redis.host", () -> redisHost);
        registry.add("spring.redis.port", () -> String.valueOf(redisPort));
        // Set ONLY when non-empty (empty/optional per the env contract).
        if (redisPassword != null) {
            registry.add("spring.redis.password", () -> redisPassword);
        }

        // Flyway migrates the us schema of the test server's database (V1 vendored
        // kernel DDL, V2 extension table, V3 cross-schema menu seed). Flyway 7.x (the
        // Boot-managed train) requires default-schema to be a member of schemas.
        registry.add("spring.flyway.schemas", () -> UsTestServerBootstrap.US_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> UsTestServerBootstrap.US_SCHEMA);
    }

    private static int parseIntPort(String name, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be an integer, but was: '"
                    + raw + "'", e);
        }
    }
}

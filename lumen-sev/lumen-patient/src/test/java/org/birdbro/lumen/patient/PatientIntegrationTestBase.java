package org.birdbro.lumen.patient;

import java.util.Map;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Abstract base for lumen-patient integration tests against the TEAM TEST SERVER
 * (patient-domain design D6, task 1.3 - mirrors lumen-system's AuthIntegrationTestBase
 * shape against the shared team server). PostgreSQL + Redis are externally provisioned -
 * NO Testcontainers. The base:
 *
 * <ol>
 *   <li>resolves the {@code AUTH_TEST_*} connection variables from the gitignored
 *       {@code lumen-sev/.env-test.local} (see {@link PatientTestEnv}) - the SAME file
 *       and the SAME key names lumen-system uses (the platform-wide test contract);
 *       missing required variables fail the context EXPLICITLY, never a silent skip;</li>
 *   <li>bootstraps the server at most once per JVM: {@code CREATE DATABASE} guarded by a
 *       {@code pg_database} existence check, then {@code CREATE SCHEMA IF NOT EXISTS
 *       patient} ({@link PatientTestServerBootstrap});</li>
 *   <li>injects the application datasource ({@code currentSchema=patient,public}),
 *       Redis, and Flyway properties via {@code @DynamicPropertySource} - so Flyway
 *       executes the patient ledger migrations against the real database and the token
 *       state lands in the server's Redis.</li>
 * </ol>
 *
 * <p>{@code @ActiveProfiles("test")} overrides the dev default of application.yml, so
 * the gitignored application-dev.yml is not loaded in tests - the dynamic properties
 * below are the ONLY datasource source of truth.</p>
 *
 * <p>Deliberately MINIMAL (patient-domain batch 1): no run-admin cloning and no residue
 * sweep - the module has no API surface yet (entities/services arrive in later batches),
 * so the schema proof needs no authenticated identity and writes no fixtures. The base
 * is extensible: later batches add their hooks here following the lumen-system pattern
 * (run-scoped identity + hard-DELETE sweep, user ruling 2026-11-07).</p>
 *
 * @author birdbro
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class PatientIntegrationTestBase {

    /**
     * The internal-service key configured for the TEST context (us-station-registration-sev
     * task 2.2): the patient internal endpoints are internal-key-guarded (fail-closed -
     * an UNCONFIGURED key rejects every call), so the shared context carries a fixed
     * test value both the guard and the internal-surface suites agree on. Suites send it
     * as the {@code X-Internal-Key} header; every OTHER suite is unaffected (the value
     * only arms the interceptor on {@code /api/v1/patient/internal/**}).
     */
    public static final String TEST_INTERNAL_KEY = "patient-internal-test-key-usb2";

    /**
     * Registers the test internal-service key (us-station-registration-sev task 2.2).
     * Runs alongside {@link #teamTestServerProperties} during context creation - a
     * second {@code @DynamicPropertySource} member on the SAME base keeps every patient
     * suite on ONE shared context (no per-suite re-boot).
     */
    @DynamicPropertySource
    static void internalKeyProperty(DynamicPropertyRegistry registry) {
        registry.add("lumen.internal-api-key", () -> TEST_INTERNAL_KEY);
    }

    /**
     * Registers the test-server properties. Runs during context creation, BEFORE the
     * context refreshes - so an unconfigured or unreachable server fails the context
     * right here, with the explicit signal.
     */
    @DynamicPropertySource
    static void teamTestServerProperties(DynamicPropertyRegistry registry) {
        PatientTestEnv env = PatientTestEnv.load();
        Map<String, String> required = env.require(
                PatientTestEnv.PG_HOST, PatientTestEnv.PG_PORT, PatientTestEnv.PG_USER,
                PatientTestEnv.PG_PASSWORD, PatientTestEnv.REDIS_HOST, PatientTestEnv.REDIS_PORT);
        String database = env.optional(PatientTestEnv.DB, PatientTestEnv.DEFAULT_DB);
        String redisPassword = env.optional(PatientTestEnv.REDIS_PASSWORD, null);

        String pgHost = required.get(PatientTestEnv.PG_HOST);
        int pgPort = parseIntPort(PatientTestEnv.PG_PORT, required.get(PatientTestEnv.PG_PORT));
        String pgUser = required.get(PatientTestEnv.PG_USER);
        String pgPassword = required.get(PatientTestEnv.PG_PASSWORD);
        String redisHost = required.get(PatientTestEnv.REDIS_HOST);
        int redisPort = parseIntPort(PatientTestEnv.REDIS_PORT, required.get(PatientTestEnv.REDIS_PORT));

        PatientTestServerBootstrap.ensureDatabaseAndSchema(pgHost, pgPort, pgUser, pgPassword, database);

        // Application datasource: the lumen database through the patient schema first,
        // then the shared public schema. NO stringtype=unspecified: the jsonb payload
        // writes go through PatientJsonbTypeHandler (setObject Types.OTHER, the pgjdbc
        // unspecified-type idiom, b2-review amendment) - a URL flag here would mask a
        // handler-route regression.
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":"
                + pgPort + "/" + database + "?currentSchema=patient,public");
        registry.add("spring.datasource.username", () -> pgUser);
        registry.add("spring.datasource.password", () -> pgPassword);

        // Redis on the test server: TokenService token state is real in tests.
        registry.add("spring.redis.host", () -> redisHost);
        registry.add("spring.redis.port", () -> String.valueOf(redisPort));
        // Set ONLY when non-empty (empty/optional per the env contract).
        if (redisPassword != null) {
            registry.add("spring.redis.password", () -> redisPassword);
        }

        // Flyway migrates the patient schema of the test server's database. Flyway 7.x
        // (the Boot-managed train) requires default-schema to be a member of schemas.
        registry.add("spring.flyway.schemas", () -> PatientTestServerBootstrap.PATIENT_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> PatientTestServerBootstrap.PATIENT_SCHEMA);
    }

    // The service-to-service seam (number-rule-unification-sev task 3.1): archive
    // creation issues pat_id through the platform NumberApi Feign contract - EVERY
    // patient test stubs it HERE (a lenient unique-number default; scenario suites
    // override in their own @BeforeEach, which runs after this base method). The
    // platform MECHANISM is proven system-side (NumberIssuerServiceTests).
    @org.springframework.boot.test.mock.mockito.MockBean
    protected org.birdbro.lumen.api.number.NumberApi numberApi;

    @org.junit.jupiter.api.BeforeEach
    void stubPlatformIssuance() {
        java.util.concurrent.atomic.AtomicInteger seq =
                new java.util.concurrent.atomic.AtomicInteger(26111900);
        org.mockito.Mockito.when(numberApi.issue(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> org.birdbro.common.core.R.ofSuccess(
                        org.birdbro.lumen.api.number.NumberIssueResponse.builder()
                                .number("PB4" + seq.incrementAndGet()).build()));
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

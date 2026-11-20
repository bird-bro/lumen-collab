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
 * below are the ONLY datasource source of truth. {@code MOCK} web environment (the
 * KernelContextBoot precedent named by task 1.1): batch 1 proves the SHELL - no HTTP
 * surface exists yet, and the declared {@code server.port=8083} identity stays
 * assertable in the environment (specs/service-topology "Service Identity").</p>
 *
 * <p>Deliberately MINIMAL (batch 1): no run-admin cloning and no residue sweep - the
 * module has no API surface yet (entities/adapters/controllers arrive in later
 * batches), so the boot proof needs no authenticated identity and writes no fixtures.
 * The base is extensible: later batches add their hooks here following the lumen-system
 * pattern (run-scoped identity + hard-DELETE sweep, user ruling 2026-11-07).</p>
 *
 * @author birdbro
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class UsIntegrationTestBase {

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

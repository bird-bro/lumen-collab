package org.birdbro.lumen.core;

import java.util.Map;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Abstract base for lumen-core integration tests against the TEAM TEST SERVER
 * (lumen-core-registration-kernel task 2.1, design D9 - mirrors lumen-patient's
 * PatientIntegrationTestBase shape). PostgreSQL + Redis are externally provisioned -
 * NO Testcontainers. The base:
 *
 * <ol>
 *   <li>resolves the {@code AUTH_TEST_*} connection variables from the gitignored
 *       {@code lumen-sev/.env-test.local} (see {@link KernelTestEnv}) - the SAME file
 *       and key names every lumen module uses (the platform-wide test contract);
 *       missing required variables fail the context EXPLICITLY, never a silent skip;</li>
 *   <li>bootstraps the server at most once per JVM: {@code CREATE DATABASE} guarded by
 *       a {@code pg_database} existence check, then {@code CREATE SCHEMA IF NOT EXISTS
 *       core} ({@link KernelTestServerBootstrap}) - the kernel-tests-only schema;</li>
 *   <li>injects the application datasource ({@code currentSchema=core,public} +
 *       {@code tcpKeepAlive=true}, the shared-server transport resilience convention),
 *       Redis, and Flyway properties via {@code @DynamicPropertySource} - Flyway
 *       applies the CANONICAL kernel DDL ({@code classpath:db/kernel}, design D2) to
 *       the {@code core} schema.</li>
 * </ol>
 *
 * <p>{@code @ActiveProfiles("test")} overrides the dev default, so the gitignored
 * application-dev.yml is not loaded in tests - the dynamic properties are the ONLY
 * datasource source of truth.</p>
 *
 * @author birdbro
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class KernelIntegrationTestBase {

    /**
     * Registers the test-server properties. Runs during context creation, BEFORE the
     * context refreshes - so an unconfigured or unreachable server fails the context
     * right here, with the explicit signal.
     */
    @DynamicPropertySource
    static void teamTestServerProperties(DynamicPropertyRegistry registry) {
        KernelTestEnv env = KernelTestEnv.load();
        Map<String, String> required = env.require(
                KernelTestEnv.PG_HOST, KernelTestEnv.PG_PORT, KernelTestEnv.PG_USER,
                KernelTestEnv.PG_PASSWORD, KernelTestEnv.REDIS_HOST, KernelTestEnv.REDIS_PORT);
        String database = env.optional(KernelTestEnv.DB, KernelTestEnv.DEFAULT_DB);
        String redisPassword = env.optional(KernelTestEnv.REDIS_PASSWORD, null);

        String pgHost = required.get(KernelTestEnv.PG_HOST);
        int pgPort = parseIntPort(KernelTestEnv.PG_PORT, required.get(KernelTestEnv.PG_PORT));
        String pgUser = required.get(KernelTestEnv.PG_USER);
        String pgPassword = required.get(KernelTestEnv.PG_PASSWORD);
        String redisHost = required.get(KernelTestEnv.REDIS_HOST);
        int redisPort = parseIntPort(KernelTestEnv.REDIS_PORT, required.get(KernelTestEnv.REDIS_PORT));

        KernelTestServerBootstrap.ensureDatabaseAndSchema(pgHost, pgPort, pgUser, pgPassword, database);

        // Application datasource: the lumen database through the kernel-tests-only core
        // schema first, then the shared public schema. tcpKeepAlive rides the URL (the
        // 2026-11-18 shared-server transport convention); Hikari keepalive comes from
        // application-test.yml. NO stringtype=unspecified: jsonb writes go through the
        // kernel's Types.OTHER handler - a URL flag here would mask a handler regression.
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":"
                + pgPort + "/" + database + "?currentSchema=core,public&tcpKeepAlive=true");
        registry.add("spring.datasource.username", () -> pgUser);
        registry.add("spring.datasource.password", () -> pgPassword);

        // Redis on the test server: framework shared beans (token state et al.) are real.
        registry.add("spring.redis.host", () -> redisHost);
        registry.add("spring.redis.port", () -> String.valueOf(redisPort));
        if (redisPassword != null) {
            registry.add("spring.redis.password", () -> redisPassword);
        }

        // Flyway applies the CANONICAL kernel DDL to the core schema (design D2/D9).
        // Flyway 7.x (the Boot-managed train) requires default-schema in schemas.
        registry.add("spring.flyway.schemas", () -> KernelTestServerBootstrap.CORE_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> KernelTestServerBootstrap.CORE_SCHEMA);
        registry.add("spring.flyway.locations", () -> "classpath:db/kernel");
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

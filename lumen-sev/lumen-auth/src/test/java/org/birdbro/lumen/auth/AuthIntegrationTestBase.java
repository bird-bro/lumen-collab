package org.birdbro.lumen.auth;

import java.util.Map;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Abstract base for lumen-auth integration tests against the TEAM TEST SERVER (design D3,
 * auth-rbac-foundation tasks 1.2-1.3). PostgreSQL + Redis are externally provisioned - NO
 * Testcontainers (design D3 rework: team server, not containers). The base:
 *
 * <ol>
 *   <li>resolves the {@code AUTH_TEST_*} connection variables from the gitignored
 *       {@code lumen-sev/.env-test.local} (see {@link AuthTestEnv}) - missing required
 *       variables fail the context EXPLICITLY, never a silent skip;</li>
 *   <li>bootstraps the server at most once per JVM: {@code CREATE DATABASE} guarded by a
 *       {@code pg_database} existence check, then {@code CREATE SCHEMA IF NOT EXISTS auth}
 *       ({@link AuthTestServerBootstrap});</li>
 *   <li>injects the application datasource ({@code currentSchema=auth}), Redis, and
 *       Flyway properties via {@code @DynamicPropertySource} - so Flyway executes its
 *       migrations against the real database and the token state lands in the server's
 *       Redis.</li>
 * </ol>
 *
 * <p>{@code @ActiveProfiles("test")} keeps the Nacos-off semantics of
 * {@code application-test.yml}; the dummy datasource of the skeleton is gone - the
 * dynamic properties below are the ONLY datasource source of truth.</p>
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

        // Application datasource: the lumen database through the auth schema.
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":"
                + pgPort + "/" + database + "?currentSchema=auth");
        registry.add("spring.datasource.username", () -> pgUser);
        registry.add("spring.datasource.password", () -> pgPassword);

        // Redis on the test server: TokenService token state is real in tests.
        registry.add("spring.redis.host", () -> redisHost);
        registry.add("spring.redis.port", () -> String.valueOf(redisPort));
        // Set ONLY when non-empty (empty/optional per the env contract).
        if (redisPassword != null) {
            registry.add("spring.redis.password", () -> redisPassword);
        }

        // Flyway migrates the auth schema of the test server's database.
        registry.add("spring.flyway.default-schema", () -> AuthTestServerBootstrap.AUTH_SCHEMA);
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

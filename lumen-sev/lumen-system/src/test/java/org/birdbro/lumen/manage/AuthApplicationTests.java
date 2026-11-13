package org.birdbro.lumen.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 1 green proof (auth-rbac-foundation tasks 1.2-1.3, design D3): the lumen-system
 * context loads against the TEAM TEST SERVER - a real PostgreSQL connection through the
 * {@code system} schema and Flyway initialized on the server - while keeping the skeleton's
 * service-identity asserts. The zero-infrastructure guarantee of init-backend-skeleton
 * task 4.1 now applies to the OTHER modules only: per the service-topology MODIFIED
 * delta, lumen-system's data-layer tests run against the externally provisioned test
 * server and FAIL EXPLICITLY when its connection is unconfigured (the designed signal of
 * {@link AuthTestEnv#require(String...)}, never a silent skip).
 *
 * <p>The Batch 1/2 {@code AuthTestStubConfig} deferral ended with the auth feature
 * change: the service-owned real {@code UserDetailsService} ships and satisfies the
 * copied framework {@code SecurityConfig} injection, so no test-context import is needed
 * anymore.</p>
 *
 * @author birdbro
 */
class AuthApplicationTests extends AuthIntegrationTestBase {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // service identity (unchanged skeleton guarantee). The port property is asserted
        // through local.server.port: the base runs RANDOM_PORT, so server.port resolves
        // to 0 in the environment by design - the skeleton's fixed 8081 remains the
        // production/main-profile contract (application.yml), untouched here.
        assertNotNull(environment);
        assertEquals("lumen-system", environment.getProperty("spring.application.name"));
        String localPort = environment.getProperty("local.server.port");
        assertNotNull(localPort, "local.server.port must be set under RANDOM_PORT");
        assertTrue(Integer.parseInt(localPort) > 0, "random port must be positive");

        // real connection to the team test server through the pooled DataSource, and
        // currentSchema=system in force
        assertEquals("system", jdbcTemplate.queryForObject("SELECT current_schema()", String.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class).intValue());

        // Flyway ran on the test server: its history table is queryable in the system
        // schema (Flyway 7.7.3 - the Boot 2.5.15 managed train - creates it on migrate()
        // even before Batch 2's V1/V2 migrations exist, so this holds for Batch 1
        // already; the query itself is the proof - it throws if the table is missing)
        Integer historyEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.flyway_schema_history", Integer.class);
        assertNotNull(historyEntries);
    }
}

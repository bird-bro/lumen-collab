package org.birdbro.lumen.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 1 green proof (auth-rbac-foundation tasks 1.2-1.3, design D3): the lumen-auth
 * context loads against the TEAM TEST SERVER - a real PostgreSQL connection through the
 * {@code auth} schema and Flyway initialized on the server - while keeping the skeleton's
 * service-identity asserts. The zero-infrastructure guarantee of init-backend-skeleton
 * task 4.1 now applies to the OTHER modules only: per the service-topology MODIFIED
 * delta, lumen-auth's data-layer tests run against the externally provisioned test
 * server and FAIL EXPLICITLY when its connection is unconfigured (the designed signal of
 * {@link AuthTestEnv#require(String...)}, never a silent skip).
 *
 * <p>{@code @Import(AuthTestStubConfig.class)}: the copied framework {@code SecurityConfig}
 * still requires a service-owned {@code UserDetailsService}; the real implementation
 * ships in Batch 3 - keep the stub until then.</p>
 *
 * @author birdbro
 */
@Import(AuthTestStubConfig.class)
class AuthApplicationTests extends AuthIntegrationTestBase {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // service identity (unchanged skeleton guarantee)
        assertNotNull(environment);
        assertEquals("lumen-auth", environment.getProperty("spring.application.name"));
        assertEquals("8081", environment.getProperty("server.port"));

        // real connection to the team test server through the pooled DataSource, and
        // currentSchema=auth in force
        assertEquals("auth", jdbcTemplate.queryForObject("SELECT current_schema()", String.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class).intValue());

        // Flyway ran on the test server: its history table is queryable in the auth
        // schema (Flyway 7.15 creates it on migrate() even before Batch 2's V1/V2
        // migrations exist, so this holds for Batch 1 already)
        Integer historyEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth.flyway_schema_history", Integer.class);
        assertNotNull(historyEntries);
        assertTrue(historyEntries >= 0);
    }
}

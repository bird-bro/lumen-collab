package org.birdbro.lumen.us;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Context boot + service-identity proof (us-station-registration-sev task 1.1, the
 * KernelContextBoot precedent): the us shell boots against the shared test server, the
 * Flyway history lands IN THE US SCHEMA (the history-stays-in-service-schema ruling -
 * never public), and the declared service identity matches the module name with a live
 * bound port (specs/service-topology "Service Identity").
 *
 * <p>Batch 3 amendment: the base runs RANDOM_PORT (the real-HTTP registration surface,
 * the {@code AuthApplicationTests} precedent), so {@code server.port} resolves to 0 in
 * the environment BY DESIGN and the identity asserts through {@code local.server.port} -
 * the skeleton's reserved 8083 stays the production/main-profile contract (the committed
 * application.yml + the port-table row), untouched here.</p>
 *
 * <p>This is the shell-level guard for every later batch - if the service identity
 * drifts or the ledger leaves the us schema, this fails first.</p>
 *
 * @author birdbro
 */
class UsContextBootTests extends UsIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    void contextBootsAndFlywayHistoryLandsInUsSchema() {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'us' AND table_name = 'flyway_schema_history'",
                Integer.class);
        assertEquals(1, found.intValue(),
                "flyway_schema_history must live in the us schema (never public)");
    }

    @Test
    void serviceIdentityDeclaresUsModuleOnARandomBoundPort() {
        assertEquals("lumen-us", environment.getProperty("spring.application.name"),
                "spring.application.name must equal the module name (service-topology "
                + "Service Identity)");
        // the AuthApplicationTests precedent: RANDOM_PORT binds port 0 and the real
        // bound port surfaces as local.server.port - the reserved 8083 lives in the
        // committed application.yml (the production contract), not in the test env
        String localPort = environment.getProperty("local.server.port");
        assertNotNull(localPort, "local.server.port must be set under RANDOM_PORT");
        assertTrue(Integer.parseInt(localPort) > 0, "the random bound port must be positive");
    }
}

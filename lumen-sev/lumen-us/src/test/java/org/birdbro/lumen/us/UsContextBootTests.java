package org.birdbro.lumen.us;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Context boot + service-identity proof (us-station-registration-sev task 1.1, the
 * KernelContextBoot precedent): the us shell boots against the shared test server, the
 * Flyway history lands IN THE US SCHEMA (the history-stays-in-service-schema ruling -
 * never public), and the declared service identity matches the reserved port-table
 * entry (specs/service-topology "Service Identity": {@code spring.application.name}
 * resolves to the module name, {@code server.port} to the reserved port 8083).
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
    void serviceIdentityDeclaresUsModuleOnReservedPort8083() {
        assertEquals("lumen-us", environment.getProperty("spring.application.name"),
                "spring.application.name must equal the module name (service-topology "
                + "Service Identity)");
        assertEquals("8083", environment.getProperty("server.port"),
                "server.port must resolve to the reserved port-table entry 8083");
    }
}

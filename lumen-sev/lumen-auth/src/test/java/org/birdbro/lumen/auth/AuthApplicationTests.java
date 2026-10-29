package org.birdbro.lumen.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Context-load test for the lumen-auth service shell (init-backend-skeleton task 4.1).
 *
 * <p>The change's only behavioral guarantee: the context starts with ZERO infrastructure
 * - no Nacos, PostgreSQL, Redis, or RocketMQ (execution-contract behaviors 12, 14, 15,
 * and 7 via the Redis-conditional adaptation). The {@code test} profile neutralizes the
 * Nacos bootstrap phase; no assertion goes beyond service identity: {@code
 * spring.application.name} = module name, {@code server.port} = 8081.
 * {@code @Import(AuthTestStubConfig.class)}: the copied framework {@code SecurityConfig}
 * requires a service-owned {@code UserDetailsService} that this change deliberately does
 * not ship (design D5, adaptation 2) - see AuthTestStubConfig.</p>
 *
 * @author birdbro
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AuthTestStubConfig.class)
class AuthApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
        assertNotNull(environment);
        assertEquals("lumen-auth", environment.getProperty("spring.application.name"));
        assertEquals("8081", environment.getProperty("server.port"));
    }
}

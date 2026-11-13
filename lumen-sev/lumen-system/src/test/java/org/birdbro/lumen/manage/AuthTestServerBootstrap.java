package org.birdbro.lumen.manage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Once-per-JVM startup bootstrap against the team test server (design D3,
 * auth-rbac-foundation task 1.2). Plain JDBC via {@link DriverManager} with the
 * framework's PostgreSQL driver (42.2.18) - no Testcontainers, the server is externally
 * provisioned:
 *
 * <ol>
 *   <li>connect to the {@code postgres} MAINTENANCE database;</li>
 *   <li>{@code CREATE DATABASE} the test database only when a {@code pg_database}
 *       existence check finds it missing (existence guard - idempotent);</li>
 *   <li>connect to the test database and {@code CREATE SCHEMA IF NOT EXISTS system}
 *       (idempotent).</li>
 * </ol>
 *
 * <p>Every step is safe to repeat, and the {@link #ensureDatabaseAndSchema} guard makes
 * the work happen at most once per JVM, so multiple Spring test contexts (context
 * caching) never re-run the DDL. {@code CREATE DATABASE} cannot run inside a transaction
 * - JDBC's default autocommit satisfies it.</p>
 *
 * @author birdbro
 */
public final class AuthTestServerBootstrap {

    /** The fixed lumen-system service schema (design D3, renamed auth -> system by
     *  rename-auth-to-system task 1.1; not env-configurable). */
    static final String SYSTEM_SCHEMA = "system";

    private static final Object BOOTSTRAP_LOCK = new Object();
    private static volatile boolean bootstrapped;

    private AuthTestServerBootstrap() {
    }

    /**
     * Idempotently ensures the test database and the {@code system} schema exist on the
     * team test server (at most once per JVM).
     *
     * @throws IllegalStateException when the server is unreachable or rejects the DDL -
     *     the explicit-failure path, never a silent skip
     */
    static void ensureDatabaseAndSchema(String host, int port, String user,
                                        String password, String database) {
        if (bootstrapped) {
            return;
        }
        synchronized (BOOTSTRAP_LOCK) {
            if (bootstrapped) {
                return;
            }
            try {
                doBootstrap(host, port, user, password, database);
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "lumen-system test-server bootstrap failed against " + host + ":"
                        + port + " (as user '" + user + "'): " + e.getMessage(), e);
            }
            bootstrapped = true;
        }
    }

    private static void doBootstrap(String host, int port, String user,
                                    String password, String database) throws SQLException {
        if (!database.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException(
                    "AUTH_TEST_DB must be a plain identifier ([A-Za-z0-9_]+), but was: '"
                    + database + "'");
        }

        String maintenanceUrl = "jdbc:postgresql://" + host + ":" + port
                + "/postgres?connectTimeout=10";
        try (Connection maintenance = DriverManager.getConnection(maintenanceUrl, user, password)) {
            if (databaseExists(maintenance, database)) {
                System.out.println("[system-test-bootstrap] database '" + database
                        + "' already exists on " + host + ":" + port);
            } else {
                try (Statement statement = maintenance.createStatement()) {
                    statement.execute("CREATE DATABASE \"" + database + "\"");
                }
                System.out.println("[system-test-bootstrap] created database '" + database
                        + "' on " + host + ":" + port);
            }
        }

        String databaseUrl = "jdbc:postgresql://" + host + ":" + port
                + "/" + database + "?connectTimeout=10";
        try (Connection connection = DriverManager.getConnection(databaseUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SYSTEM_SCHEMA);
        }
        System.out.println("[system-test-bootstrap] schema '" + SYSTEM_SCHEMA
                + "' ensured in database '" + database + "'");
    }

    private static boolean databaseExists(Connection maintenance, String database)
            throws SQLException {
        try (PreparedStatement query =
                     maintenance.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            query.setString(1, database);
            try (ResultSet found = query.executeQuery()) {
                return found.next();
            }
        }
    }
}

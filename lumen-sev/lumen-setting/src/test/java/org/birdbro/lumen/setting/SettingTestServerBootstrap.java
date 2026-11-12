package org.birdbro.lumen.setting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Once-per-JVM startup bootstrap against the team test server. Clone of lumen-system's
 * {@code AuthTestServerBootstrap} with the {@code setting} schema - plain JDBC via
 * {@link DriverManager}, no Testcontainers, the server is externally provisioned:
 *
 * <ol>
 *   <li>connect to the {@code postgres} MAINTENANCE database;</li>
 *   <li>{@code CREATE DATABASE} the test database only when a {@code pg_database}
 *       existence check finds it missing (existence guard - idempotent);</li>
 *   <li>connect to the test database and {@code CREATE SCHEMA IF NOT EXISTS setting}
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
public final class SettingTestServerBootstrap {

    /** The fixed lumen-setting service schema (exam-setting-api D1; not env-configurable). */
    static final String SETTING_SCHEMA = "setting";

    private static final Object BOOTSTRAP_LOCK = new Object();
    private static volatile boolean bootstrapped;

    private SettingTestServerBootstrap() {
    }

    /**
     * Idempotently ensures the test database and the {@code setting} schema exist on the
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
                bootstrapped = true;
            } catch (SQLException e) {
                throw new IllegalStateException("the team test server bootstrap failed: "
                        + e.getMessage(), e);
            }
        }
    }

    private static void doBootstrap(String host, int port, String user,
                                    String password, String database) throws SQLException {
        String maintenanceUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection maintenance = DriverManager.getConnection(maintenanceUrl, user, password);
             Statement statement = maintenance.createStatement()) {
            ResultSet existing = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + database + "'");
            boolean databaseExists = existing.next();
            existing.close();
            if (!databaseExists) {
                statement.executeUpdate("CREATE DATABASE " + database);
            }
        }

        String databaseUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        try (Connection target = DriverManager.getConnection(databaseUrl, user, password);
             Statement statement = target.createStatement()) {
            statement.executeUpdate("CREATE SCHEMA IF NOT EXISTS " + SETTING_SCHEMA);
        }
    }
}

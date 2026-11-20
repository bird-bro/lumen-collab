package org.birdbro.lumen.us;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader for the gitignored team-test-server env file ({@code lumen-sev/.env-test.local},
 * us-station-registration-sev task 1.1 - mirrors lumen-patient's PatientTestEnv).
 *
 * <p>PLATFORM-WIDE TEST CONTRACT: the file name and the {@code AUTH_TEST_*} key names are
 * the SAME ones lumen-system, lumen-patient, and lumen-core read - the team test server
 * is shared across every lumen module's data-layer tests, and a developer configures it
 * ONCE for the whole platform. Do not fork the key names per module. Resolution order:
 * a non-blank system-environment value wins over the file; the file is found walking UP
 * from the JVM working directory (surefire runs in {@code lumen-sev/lumen-us}, the file
 * sits at {@code lumen-sev/}). {@link #require(String...)} is the DESIGNED
 * explicit-failure signal - unconfigured connections are never silently skipped.</p>
 *
 * @author birdbro
 */
public final class UsTestEnv {

    /** Name of the gitignored env file (key = variable name, value = plain text). */
    public static final String FILE_NAME = ".env-test.local";

    /** PostgreSQL host on the team test server (required). */
    public static final String PG_HOST = "AUTH_TEST_PG_HOST";
    /** PostgreSQL port on the team test server (required). */
    public static final String PG_PORT = "AUTH_TEST_PG_PORT";
    /** PostgreSQL user (required - the user fills it locally). */
    public static final String PG_USER = "AUTH_TEST_PG_USER";
    /** PostgreSQL password (required - the user fills it locally). */
    public static final String PG_PASSWORD = "AUTH_TEST_PG_PASSWORD";
    /** Database name to bootstrap/use (optional, default {@link #DEFAULT_DB}). */
    public static final String DB = "AUTH_TEST_DB";
    /** Default database name (the shared platform test database: {@code lumen}). */
    public static final String DEFAULT_DB = "lumen";
    /** Redis host on the team test server (required). */
    public static final String REDIS_HOST = "AUTH_TEST_REDIS_HOST";
    /** Redis port on the team test server (required). */
    public static final String REDIS_PORT = "AUTH_TEST_REDIS_PORT";
    /** Redis password (optional - empty allowed when the server has none). */
    public static final String REDIS_PASSWORD = "AUTH_TEST_REDIS_PASSWORD";

    private final Map<String, String> fileValues;
    private final Path envFile;
    private final Path searchStart;

    private UsTestEnv(Map<String, String> fileValues, Path envFile, Path searchStart) {
        this.fileValues = fileValues;
        this.envFile = envFile;
        this.searchStart = searchStart;
    }

    /**
     * Loads the env file (walking up from the working directory). A missing file is NOT
     * an error here - only system-environment variables can satisfy require() then.
     */
    public static UsTestEnv load() {
        Path start = Paths.get("").toAbsolutePath();
        Path dir = start;
        Path found = null;
        while (dir != null) {
            Path candidate = dir.resolve(FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                found = candidate;
                break;
            }
            dir = dir.getParent();
        }

        Map<String, String> values = new LinkedHashMap<>();
        if (found != null) {
            values.putAll(parse(found));
        }
        return new UsTestEnv(values, found, start);
    }

    /** Non-blank system environment wins over the file; {@code null} when neither. */
    public String get(String name) {
        String fromEnvironment = System.getenv(name);
        if (fromEnvironment != null && !fromEnvironment.trim().isEmpty()) {
            return fromEnvironment.trim();
        }
        String fromFile = fileValues.get(name);
        if (fromFile != null && !fromFile.trim().isEmpty()) {
            return fromFile.trim();
        }
        return null;
    }

    /**
     * Requires every named variable, collecting ALL missing ones into one explicit
     * {@link IllegalStateException} (surfaces as the context-load failure cause).
     */
    public Map<String, String> require(String... names) {
        List<String> missing = new ArrayList<>();
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String name : names) {
            String value = get(name);
            if (value == null) {
                missing.add(name);
            } else {
                resolved.put(name, value);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(missingVariablesMessage(missing));
        }
        return resolved;
    }

    /** Optional variable with a fallback ({@code null} fallback = not provided). */
    public String optional(String name, String fallback) {
        String value = get(name);
        return value != null ? value : fallback;
    }

    /** Absolute path of the env file actually used, or {@code null} when not found. */
    public Path envFile() {
        return envFile;
    }

    private String missingVariablesMessage(List<String> missing) {
        StringBuilder message = new StringBuilder();
        message.append("lumen-us's data tests fail explicitly by design: the team test ")
               .append("server connection is not configured (unconfigured connections are ")
               .append("never silently skipped).\n");
        message.append("Missing required variables: ").append(missing).append("\n");
        if (envFile != null) {
            message.append("Provide them in the gitignored env file ")
                   .append(envFile.toAbsolutePath())
                   .append(" (AUTH_TEST_* keys) or as AUTH_TEST_* environment variables ")
                   .append("(environment overrides the file).\n");
        } else {
            message.append("The gitignored env file ").append(FILE_NAME)
                   .append(" was NOT FOUND searching upward from ")
                   .append(searchStart.toAbsolutePath())
                   .append(" - expected at the lumen-sev/ directory level.\n");
        }
        return message.toString();
    }

    private static Map<String, String> parse(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Found the test-server env file " + file.toAbsolutePath()
                    + " but could not read it: " + e.getMessage(), e);
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }
}

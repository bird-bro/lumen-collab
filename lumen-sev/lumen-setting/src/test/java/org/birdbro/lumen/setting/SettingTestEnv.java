package org.birdbro.lumen.setting;

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
 * Loader for the gitignored team-test-server env file ({@code lumen-sev/.env-test.local}).
 * Clone of lumen-system's {@code AuthTestEnv} with the setting-service message - the
 * SAME file, the SAME {@code AUTH_TEST_*} variable contract, the SAME resolution order
 * (environment wins over file; file located by walking UP from the JVM working
 * directory), because both services test against ONE shared team server. Unconfigured =
 * the tests fail EXPLICITLY, never a silent skip (specs/service-topology).
 *
 * @author birdbro
 */
public final class SettingTestEnv {

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
    /** Database name to bootstrap/use (optional, defaults to {@link #DEFAULT_DB}). */
    public static final String DB = "AUTH_TEST_DB";
    /** Default database name (design D3: {@code lumen}). */
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

    private SettingTestEnv(Map<String, String> fileValues, Path envFile, Path searchStart) {
        this.fileValues = fileValues;
        this.envFile = envFile;
        this.searchStart = searchStart;
    }

    /**
     * Loads the env file (walking up from the working directory) and exposes the
     * {@code AUTH_TEST_*} values. A missing file is NOT an error here - it simply means
     * only system-environment variables can satisfy {@link #require(String...)}.
     */
    public static SettingTestEnv load() {
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
        return new SettingTestEnv(values, found, start);
    }

    /**
     * Resolved value of one variable: non-blank system environment wins, else the file
     * value; {@code null} when neither provides a non-blank value.
     */
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
     * Requires every named variable. Missing ones (unset or blank after resolution) are
     * ALL collected into one explicit {@link IllegalStateException} - the designed
     * failure signal that surfaces in surefire output as the context-load cause.
     *
     * @return the resolved values keyed by variable name
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

    /**
     * Optional variable with a fallback: {@code fallback} when the variable is unset or
     * blank ({@code null} fallback = treat as not provided).
     */
    public String optional(String name, String fallback) {
        String value = get(name);
        return value != null ? value : fallback;
    }

    /** Absolute path of the env file actually used, or {@code null} when not found. */
    public Path envFile() {
        return envFile;
    }

    /** Directory the upward search started from (surefire working dir at runtime). */
    public Path searchStart() {
        return searchStart;
    }

    private String missingVariablesMessage(List<String> missing) {
        StringBuilder message = new StringBuilder();
        message.append("lumen-setting's tests fail explicitly by design: the team test ")
               .append("server connection is not configured (specs/service-topology: ")
               .append("unconfigured connections are never silently skipped).\n");
        message.append("Missing required variables: ").append(missing).append("\n");
        if (envFile != null) {
            message.append("Provide them in the gitignored env file ")
                   .append(envFile.toAbsolutePath())
                   .append(" (AUTH_TEST_* keys, the SAME file lumen-system's tests use) ")
                   .append("or as AUTH_TEST_* environment variables ")
                   .append("(environment overrides the file).\n");
        } else {
            message.append("The gitignored env file ").append(FILE_NAME)
                   .append(" was NOT FOUND searching upward from ")
                   .append(searchStart.toAbsolutePath())
                   .append(" - expected at the lumen-sev/ directory level. Provide the ")
                   .append("AUTH_TEST_* variables there or as environment variables.\n");
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
                continue; // not a KEY=VALUE line - ignore (comments handled above)
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            // tolerate one pair of matching quotes around the value
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

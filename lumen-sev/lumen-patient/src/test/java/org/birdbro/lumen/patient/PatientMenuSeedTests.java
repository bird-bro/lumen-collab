package org.birdbro.lumen.patient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The patient-config menu seed's RETIREMENT regression (number-rule-unification-sev
 * task 3.2, user ruling 2026-11-18): the 号码规则 console entry moved to the 系统管理
 * subtree with the system:number:* perms (the system ledger's V40), and the patient
 * seed V2's guarded inserts anchor on the OLD perms strings — a ledger repair that
 * re-runs V2 RESURRECTS old-shape rows (recorded in V40's REPAIR-PATH note). This
 * suite pins the retired state and proves the repair contract:
 *
 * <ol>
 *   <li>the retired state holds: NO live {@code patient:rule:%} row and NO live
 *       患者配置 group (the platform home is asserted system-side by
 *       NumberRuleSeedTests);</li>
 *   <li>re-executing V2 resurrects the old-shape rows (V2's contract is unchanged),
 *     and the suite HARD-DELETES the resurrected rows afterwards - the shared
 *     database never keeps the phantoms (user ruling 2026-11-07); replaying V40
 *     after V2 converges the state (content-anchored, the repair runbook).</li>
 * </ol>
 *
 * @author birdbro
 */
class PatientMenuSeedTests extends PatientIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void patientConfigSeedRetired() {
        Integer liveRuleRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'patient:rule:%' "
                        + "AND deleted = 0", Integer.class);
        assertEquals(Integer.valueOf(0), liveRuleRows,
                "no live patient:rule:* menu row survives the unification move");

        Integer liveGroup = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE type = 'M' AND title = '患者配置' "
                        + "AND deleted = 0", Integer.class);
        assertEquals(Integer.valueOf(0), liveGroup, "the 患者配置 group stays retired");
    }

    @Test
    void v2ReExecutionResurrectsThenTheSuiteCleans() throws Exception {
        reExecuteSeed();

        // V2's guards anchor on the OLD perms strings: the re-run resurrects
        Integer resurrected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'patient:rule:%' "
                        + "AND deleted = 0", Integer.class);
        assertTrue(resurrected >= 4,
                "V2 re-execution resurrects the old-shape rows (its contract is unchanged): "
                + resurrected);

        // the suite HARD-DELETES the phantoms - bindings BEFORE rows (the V1 FK:
        // role_menu -> menu_info), group grants included; the repair runbook replays
        // V40 after V2 to re-move them content-anchored
        jdbcTemplate.update(
                "DELETE FROM system.role_menu rm USING system.menu_info m "
                        + "WHERE rm.menu_id = m.id AND (m.perms LIKE 'patient:rule:%' "
                        + "OR (m.type = 'M' AND m.title = '患者配置'))");
        jdbcTemplate.update(
                "DELETE FROM system.menu_info WHERE perms LIKE 'patient:rule:%' AND deleted = 0");
        jdbcTemplate.update(
                "DELETE FROM system.menu_info WHERE type = 'M' AND title = '患者配置'"
                        + " AND deleted = 0");

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'patient:rule:%' "
                        + "AND deleted = 0", Integer.class);
        assertEquals(Integer.valueOf(0), after, "the phantoms never persist");
    }

    // ===================== helpers =====================

    /** Re-executes every statement of the patient seed migration. */
    private void reExecuteSeed() throws SQLException {
        for (String sql : migrationStatements("/db/migration/V2__patient_menu.sql")) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    /**
     * Every statement of one migration file, split on semicolons - the comments never
     * contain one (the recorded re-execution footgun), so the split is safe.
     */
    private static List<String> migrationStatements(String classPathFile) {
        try {
            String raw = new String(new ClassPathResource(classPathFile).getInputStream()
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(classPathFile + " must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /** Plain-JDBC connection to the team test server's database. */
    private static Connection openMigrationConnection() throws SQLException {
        PatientTestEnv env = PatientTestEnv.load();
        Map<String, String> required = env.require(
                PatientTestEnv.PG_HOST, PatientTestEnv.PG_PORT,
                PatientTestEnv.PG_USER, PatientTestEnv.PG_PASSWORD);
        String database = env.optional(PatientTestEnv.DB, PatientTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(PatientTestEnv.PG_HOST) + ":"
                + required.get(PatientTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=patient,public";
        return DriverManager.getConnection(url, required.get(PatientTestEnv.PG_USER),
                required.get(PatientTestEnv.PG_PASSWORD));
    }
}

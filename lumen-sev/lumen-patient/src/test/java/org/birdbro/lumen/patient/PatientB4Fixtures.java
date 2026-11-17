package org.birdbro.lumen.patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Batch-4 API-suite fixture registry + hard-DELETE sweep (mirrors
 * {@code PatientB3Fixtures}): every archive id and rule id the batch-4 HTTP tests
 * hand out is captured here so {@link #sweep(JdbcTemplate)} can hard-delete the
 * master/identifier/outbox/merge_log and rule families (user ruling 2026-11-07:
 * shared-test-DB residue is always hard deleted, never soft-deleted).
 *
 * <p>Secondary sweep keys for crashed prior JVMs: every batch-4 test archive NAME
 * carries the {@code b4} marker prefix and every batch-4 test rule TYPE carries the
 * {@code b4_} prefix, so unregistered rows are still found. Never touches batch-2/3
 * fixture families ({@code b2_} rules, {@code B2T-} masters, {@code b3} archives) or
 * the seeded {@code pat_id} rule row beyond consuming serials (accepted residue -
 * serials advance, gap-tolerant).</p>
 *
 * @author birdbro
 */
final class PatientB4Fixtures {

    /** The marker prefix every batch-4 test archive name carries (sweep key). */
    static final String NAME_MARKER = "b4";

    /** The marker prefix every batch-4 test rule TYPE carries (sweep key). */
    static final String RULE_TYPE_MARKER = "b4_";

    /** Archive ids issued by the running batch-4 classes. */
    private static final List<Integer> ARCHIVE_IDS =
            Collections.synchronizedList(new ArrayList<>());

    /** Rule ids issued by the running batch-4 classes. */
    private static final List<Integer> RULE_IDS =
            Collections.synchronizedList(new ArrayList<>());

    private PatientB4Fixtures() {
    }

    /** Registers an archive id (created OR matched survivor) for the sweep. */
    static void registerArchive(Integer archiveId) {
        if (archiveId != null) {
            ARCHIVE_IDS.add(archiveId);
        }
    }

    /** Registers a rule id for the sweep. */
    static void registerRule(Integer ruleId) {
        if (ruleId != null) {
            RULE_IDS.add(ruleId);
        }
    }

    /**
     * Hard-DELETEs every batch-4 fixture family: the captured ids PLUS the marker-keyed
     * rows recovered from a crashed prior JVM. Rule deletions are plain deletes (the
     * API's own delete is soft/logical - sweeping must remove the ROW, the ruling's
     * hard-delete shape); archive children are swept before their master.
     */
    static void sweep(JdbcTemplate jdbcTemplate) {
        List<Integer> archiveIds = new ArrayList<>(ARCHIVE_IDS);
        jdbcTemplate.queryForList(
                "SELECT id FROM patient_master WHERE name LIKE 'b4%'", Integer.class)
                .forEach(archiveIds::add);
        for (Integer id : archiveIds) {
            jdbcTemplate.update(
                    "DELETE FROM patient_merge_log WHERE merged_id = ? OR survivor_id = ?", id, id);
            jdbcTemplate.update(
                    "DELETE FROM patient_event_outbox WHERE patient_id = ?", id);
            jdbcTemplate.update(
                    "DELETE FROM patient_identifier WHERE patient_id = ?", id);
            jdbcTemplate.update(
                    "DELETE FROM patient_master WHERE id = ?", id);
        }
        ARCHIVE_IDS.clear();

        List<Integer> ruleIds = new ArrayList<>(RULE_IDS);
        jdbcTemplate.queryForList(
                "SELECT id FROM patient_number_rule WHERE rule_type LIKE 'b4\\_%' ESCAPE '\\'",
                Integer.class)
                .forEach(ruleIds::add);
        for (Integer id : ruleIds) {
            jdbcTemplate.update("DELETE FROM patient_number_rule WHERE id = ?", id);
        }
        RULE_IDS.clear();
    }
}

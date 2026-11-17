package org.birdbro.lumen.patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.birdbro.lumen.patient.entity.PatientMaster;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Batch-3 fixture registry + hard-DELETE sweep (patient-domain brief decision 10):
 * test-created archives consume REAL serials from the seeded {@code pat_id} rule
 * (accepted residue - serials advance, gap-tolerant), and EVERY archive id the batch
 * hands out is captured here so {@link #sweep(JdbcTemplate)} can hard-delete the
 * master/identifier/outbox/merge_log families (user ruling 2026-11-07: shared-test-DB
 * residue is always hard deleted, never soft-deleted).
 *
 * <p>Secondary sweep key: every batch-3 test archive NAME carries the {@code b3}
 * marker prefix, so rows left by a crashed prior JVM (whose captured list died with
 * it) are still found. Never touches batch-2's fixture families ({@code b2_} rules,
 * {@code B2T-} masters) or the seeded {@code pat_id} rule row beyond consuming
 * serials.</p>
 *
 * @author birdbro
 */
final class PatientB3Fixtures {

    /** The marker prefix every batch-3 test archive name carries (sweep key). */
    static final String NAME_MARKER = "b3";

    /** Archive ids issued by the running batch-3 classes (all merge participants). */
    private static final List<Integer> ARCHIVE_IDS = Collections.synchronizedList(new ArrayList<>());

    private PatientB3Fixtures() {
    }

    /** Registers a service-returned archive (created OR matched survivor) for the sweep. */
    static void register(Integer archiveId) {
        if (archiveId != null) {
            ARCHIVE_IDS.add(archiveId);
        }
    }

    /** Registers an archive entity, tolerating the null-id cases. */
    static void register(PatientMaster archive) {
        if (archive != null) {
            register(archive.getId());
        }
    }

    /**
     * Hard-DELETEs every batch-3 archive family: the captured ids PLUS any
     * {@code b3}-prefixed-name rows recovered from a crashed prior JVM. Children are
     * swept before their master (merge_log/outbox/identifier logically reference it).
     */
    static void sweep(JdbcTemplate jdbcTemplate) {
        List<Integer> ids = new ArrayList<>(ARCHIVE_IDS);
        jdbcTemplate.queryForList(
                "SELECT id FROM patient_master WHERE name LIKE 'b3%'", Integer.class)
                .forEach(ids::add);
        for (Integer id : new ArrayList<>(ids)) {
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
    }
}

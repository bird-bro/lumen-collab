package org.birdbro.lumen.patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * us-station batch-2 fixture registry + hard-DELETE sweep (us-station-registration-sev
 * task 2.2; mirrors {@code PatientB3Fixtures}/{@code PatientB4Fixtures}): every archive
 * id and outbox row the internal-surface HTTP tests hand out is captured here so
 * {@link #sweep(JdbcTemplate)} can hard-delete the master/identifier/outbox/merge_log
 * families (user ruling 2026-11-07: shared-test-DB residue is always hard deleted,
 * never soft-deleted).
 *
 * <p>Secondary sweep keys for crashed prior JVMs: every us-b2 test archive NAME carries
 * the {@code usb2} marker prefix, every us-b2 test archive PAT_ID carries the
 * {@code USB2-} prefix, and every us-b2 outbox row carries an {@code USB2-} event type
 * - unregistered rows are still found. Never touches the earlier patient-domain
 * fixture families ({@code b2_} rules / {@code B2T-} masters, {@code b3} archives,
 * {@code b4} archives) or the seeded {@code pat_id} rule row beyond consuming serials
 * (accepted residue - serials advance, gap-tolerant).</p>
 *
 * @author birdbro
 */
final class PatientUsB2Fixtures {

    /** The marker prefix every us-b2 test archive name carries (sweep key). */
    static final String NAME_MARKER = "usb2";

    /** The marker prefix every us-b2 test archive pat_id carries (sweep key). */
    static final String PAT_ID_MARKER = "USB2-";

    /** The marker prefix every us-b2 outbox event type carries (sweep key). */
    static final String EVENT_MARKER = "USB2-";

    /** Archive ids issued by the running us-b2 classes (created OR matched). */
    private static final List<Integer> ARCHIVE_IDS =
            Collections.synchronizedList(new ArrayList<>());

    private PatientUsB2Fixtures() {
    }

    /** Registers an archive id (created OR matched survivor) for the sweep. */
    static void registerArchive(Integer archiveId) {
        if (archiveId != null) {
            ARCHIVE_IDS.add(archiveId);
        }
    }

    /**
     * Hard-DELETEs every us-b2 fixture family: the captured archive ids (children
     * before their master) PLUS the marker-keyed rows recovered from a crashed prior
     * JVM - name/pat_id-keyed masters and event-type-keyed outbox rows.
     */
    static void sweep(JdbcTemplate jdbcTemplate) {
        List<Integer> archiveIds = new ArrayList<>(ARCHIVE_IDS);
        jdbcTemplate.queryForList(
                "SELECT id FROM patient_master WHERE name LIKE 'usb2%' OR pat_id LIKE 'USB2-%'",
                Integer.class).forEach(archiveIds::add);
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

        // Outbox rows whose carrier archive came from another family (e.g. the
        // merge/carrier reuse) - keyed by the us-b2 event-type marker.
        jdbcTemplate.update(
                "DELETE FROM patient_event_outbox WHERE event_type LIKE 'USB2-%'");
    }
}

package org.birdbro.lumen.us;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The batch-3 sweep fixture's predicate proof (us-station-registration-sev B3-review
 * Minor-3 rider). The sweep's marker SELECT gates every row-match on {@code deleted = 0}
 * - the AND-precedence bug had {@code deleted = 0 AND patient_uid >= ? AND patient_uid < ?}
 * binding as one arm with the OR arms floating free, so a marker-keyed row matched
 * REGARDLESS of its deleted flag. The fix parenthesizes the OR group; this test pins the
 * fixed semantics end-to-end through the REAL {@link UsRegistrationFixtures#sweep}:
 *
 * <ul>
 *   <li>a LIVE marker-keyed order is hard-deleted (the sweep's job);</li>
 *   <li>a SOFT-DELETED marker-keyed order survives the sweep - {@code deleted = 0}
 *       now genuinely applies to the whole row-match predicate.</li>
 * </ul>
 *
 * <p>Seeds ride the suite's own stub band + the {@code us3} jsonb-name marker (the same
 * recovery keys the fixture sweeps), and the test hard-deletes its own soft-deleted seed
 * in a finally (user ruling 2026-11-07: zero shared-database residue).</p>
 *
 * @author birdbro
 */
class UsRegistrationSweepTests extends UsIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sweepMarkerPredicateAppliesDeletedGateToTheWholeRowMatch() {
        // the seeds carry BOTH sweep markers (the us3 jsonb name + the US3- accession
        // prefix) - either arm must stay gated by deleted = 0 after the fix
        String accessionBase = "US3-SWEEP-" + System.currentTimeMillis();
        int bandUid = STUB_UID_BASE + 711;
        jdbcTemplate.update(
                "INSERT INTO exam_order (status, patient_uid, accession_no, patient_snapshot,"
                        + " deleted) VALUES ('REGISTERED', ?, ?, ?::jsonb, 0)",
                bandUid, accessionBase + "-LIVE", "{\"name\":\"us3sweep活行\"}");
        Integer liveId = jdbcTemplate.queryForObject(
                "SELECT id FROM exam_order WHERE patient_snapshot->>'name' = 'us3sweep活行'",
                Integer.class);
        jdbcTemplate.update(
                "INSERT INTO exam_order (status, patient_uid, accession_no, patient_snapshot,"
                        + " deleted) VALUES ('REGISTERED', ?, ?, ?::jsonb, 1)",
                bandUid, accessionBase + "-DEAD", "{\"name\":\"us3sweep软删行\"}");
        Integer softDeletedId = jdbcTemplate.queryForObject(
                "SELECT id FROM exam_order WHERE patient_snapshot->>'name' = 'us3sweep软删行'",
                Integer.class);

        try {
            UsRegistrationFixtures.sweep(jdbcTemplate);

            Long liveLeft = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM exam_order WHERE id = ?", Long.class, liveId);
            assertEquals(Long.valueOf(0), liveLeft,
                    "the live marker-keyed order is hard-deleted by the sweep");
            Long softDeletedLeft = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM exam_order WHERE id = ?", Long.class, softDeletedId);
            assertEquals(Long.valueOf(1), softDeletedLeft,
                    "the soft-deleted marker-keyed order SURVIVES the sweep - deleted = 0 "
                            + "genuinely gates the whole row-match predicate (the "
                            + "AND-precedence fix)");
        } finally {
            // zero residue: the fixed sweep deliberately leaves soft-deleted rows to the
            // domain - this test's own seed must still leave the shared database clean
            jdbcTemplate.update("DELETE FROM exam_order WHERE id = ?", softDeletedId);
        }
    }
}

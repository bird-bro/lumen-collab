package org.birdbro.lumen.us;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * us-station batch-3 fixture registry + hard-DELETE sweep (us-station-registration-sev
 * task 3.2; mirrors {@code PatientUsB2Fixtures}): every order id the registration API
 * tests create is captured here so {@link #sweep(JdbcTemplate)} can hard-delete the
 * ext/execution/report children and the order rows themselves (user ruling
 * 2026-11-07: shared-test-DB residue is always hard deleted, never soft-deleted).
 *
 * <p>Secondary sweep keys for crashed prior JVMs: the stubbed identity band ({@code
 * STUB_UID_BASE} and above - a per-JVM band no other suite can write), the {@code us3}
 * patient-name marker inside the jsonb snapshot, and the {@code US3-} accession prefix.
 * The us schema is this module's alone - the sweep touches ONLY the us tables (the run
 * identity lives in the {@code UsApiTestBase} sweep, never here).</p>
 *
 * @author birdbro
 */
final class UsRegistrationFixtures {

    /** The stub identity band width (the swept patient_uid range: [base, base+width)). */
    private static final int BAND_WIDTH = 100000;

    /** Order ids issued by the running batch-3 classes. */
    private static final List<Integer> ORDER_IDS =
            java.util.Collections.synchronizedList(new ArrayList<>());

    /**
     * Slot-config row ids the redesign-② suites create (the UR3- modality-token family
     * below sweeps any crashed-JVM residue too).
     */
    private static final List<Integer> SLOT_CONFIG_IDS =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private UsRegistrationFixtures() {
    }

    /** Registers an order id for the sweep. */
    static void registerOrder(Integer orderId) {
        if (orderId != null) {
            ORDER_IDS.add(orderId);
        }
    }

    /** Registers a slot-config row id for the sweep. */
    static void registerSlotConfig(Integer configId) {
        if (configId != null) {
            SLOT_CONFIG_IDS.add(configId);
        }
    }

    /**
     * Hard-DELETEs every batch-3 fixture family: the captured order ids (children before
     * their order) PLUS the marker-keyed rows recovered from a crashed prior JVM - the
     * stub-band orders, jsonb-name-marked orders, and accession-marked orders. Never
     * touches other families' rows (the kernel DDL tables carry no other us fixtures
     * today).
     */
    static void sweep(JdbcTemplate jdbcTemplate) {
        List<Integer> orderIds = new ArrayList<>(ORDER_IDS);
        jdbcTemplate.queryForList(
                "SELECT id FROM exam_order WHERE deleted = 0 AND ("
                        // the band pair's inner AND is parenthesized EXPLICITLY (B3 review
                        // Minor-3 rider) so no reader can misparse the gate as binding
                        // only the band arm - the deleted = 0 gate applies to the WHOLE
                        // row-match group, exactly as the sweep-semantics test pins
                        + "(patient_uid >= ? AND patient_uid < ?)"
                        + " OR patient_snapshot->>'name' LIKE 'us3%'"
                        + " OR accession_no LIKE 'US3-%')",
                Integer.class, UsIntegrationTestBase.STUB_UID_BASE,
                UsIntegrationTestBase.STUB_UID_BASE + BAND_WIDTH).forEach(orderIds::add);
        for (Integer id : orderIds) {
            jdbcTemplate.update("DELETE FROM us_registration_ext WHERE order_id = ?", id);
            jdbcTemplate.update("DELETE FROM exam_report WHERE order_id = ?", id);
            jdbcTemplate.update("DELETE FROM exam_execution WHERE order_id = ?", id);
            jdbcTemplate.update("DELETE FROM exam_order WHERE id = ?", id);
        }
        ORDER_IDS.clear();
        // the slot-config family: captured ids PLUS the UR3- modality-token marker
        // (recovers a crashed prior JVM's rows - the shared-server sweep ruling)
        List<Integer> configIds = new ArrayList<>(SLOT_CONFIG_IDS);
        jdbcTemplate.queryForList(
                "SELECT id FROM exam_slot_config WHERE modality LIKE 'UR3%'",
                Integer.class).forEach(configIds::add);
        for (Integer id : configIds) {
            jdbcTemplate.update("DELETE FROM exam_slot_config WHERE id = ?", id);
        }
        SLOT_CONFIG_IDS.clear();
    }
}

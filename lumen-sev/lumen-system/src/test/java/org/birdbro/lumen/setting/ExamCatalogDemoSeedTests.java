package org.birdbro.lumen.setting;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo exam-catalog seed proof (exam-catalog-device-fields task 3.1; the
 * V52/UsVocabDictSeedTests + V22/AuthWorkstationSeedTests precedents): V53 seeds
 * the five-table transitive closure (23 groups / 34 bodies / 15 methods / 46 items /
 * 58 links, every row remark-marked 演示种子 2026-11-24) with the reviewed Latin
 * device values. The V53 re-execution no-op proof was removed by
 * exam-body-device-field-cleanup (V54): replaying a superseded migration
 * resurrects its dropped columns (ADD COLUMN IF NOT EXISTS) and poisons the shared
 * schema - Flyway never re-runs a superseded ledger row, so the scenario is not
 * real. The data-level replay safety stays proven by the shared DB replaying the
 * archived migration as a no-op on every fresh install.
 *
 * @author birdbro
 */
class ExamCatalogDemoSeedTests extends SettingIntegrationTestBase {

    private static final String MARK = "演示种子 2026-11-24";

    /** {modality, live count} of the seeded items. */
    private static final String[][] MODALITY_COUNTS = {
            {"CT", "12"}, {"DR", "5"}, {"ENDO", "5"}, {"MR", "8"}, {"US", "16"}};

    /** {modality, code, exam, deviceDesc} spot rows (one per modality family). */
    private static final String[][] SPOT_ROWS = {
            {"US", "US-ABD-01", "腹部超声（肝胆胰脾）", "US ABDOMEN COMPLETE"},
            {"CT", "CT-VA-02", "冠脉CTA（冠状动脉成像）", "CT CORONARY CTA"},
            {"MR", "MR-AB-01", "上腹MRI平扫+MRCP", "MR UPPER ABDOMEN WITH MRCP"},
            {"DR", "DR-TX-01", "胸部正侧位摄影", "DR CHEST PA AND LATERAL"},
            {"ENDO", "EN-GS-01", "普通胃镜", "GASTROSCOPY"}};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    @Test
    void perModalityCountsMatchTheSeed() {
        for (String[] mc : MODALITY_COUNTS) {
            assertEquals(Long.parseLong(mc[1]),
                    count("SELECT COUNT(*) FROM system.exam_item "
                            + "WHERE modality = ? AND remark = ? AND deleted = 0", mc[0], MARK),
                    "the " + mc[0] + " demo count must match the seed");
        }
        // the five-table closure presence
        assertEquals(23L, count("SELECT COUNT(*) FROM system.exam_body_group WHERE remark = ?", MARK),
                "the 23 seeded body groups");
        // bodies count by the seed's EXPLICIT ids - the shared DB carries extra
        // hand-applied marked bodies (11 rows beyond the closure) that are NOT the
        // seed's responsibility
        assertEquals(34L, count("SELECT COUNT(*) FROM system.exam_body WHERE remark = ? "
                + "AND id BETWEEN 1011 AND 1044", MARK),
                "the 34 seeded bodies (by their seeded ids)");
        assertEquals(15L, count("SELECT COUNT(*) FROM system.exam_method WHERE remark = ?", MARK),
                "the 15 seeded methods");
        assertEquals(58L, count("SELECT COUNT(*) FROM system.exam_item_method m "
                + "JOIN system.exam_item i ON m.exam_id = i.id WHERE i.remark = ?", MARK),
                "the 58 seeded method links");
    }

    @Test
    void spotRowsCarryTheReviewedDeviceValues() {
        for (String[] spot : SPOT_ROWS) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT exam, device_desc FROM system.exam_item "
                            + "WHERE modality = ? AND code = ? AND remark = ? AND deleted = 0",
                    spot[0], spot[1], MARK);
            assertEquals(spot[2], row.get("exam"), "the " + spot[1] + " name");
            assertEquals(spot[3], row.get("device_desc"),
                    "the " + spot[1] + " reviewed device description");
        }
        // the body term spot row (one per modality family; the Latin-name column was
        // dropped by V54 - the body's device field set is the single Body Part term)
        assertEquals("ABDOMEN_UPPER", jdbcTemplate.queryForObject(
                "SELECT device_body_part FROM system.exam_body WHERE id = 1011", String.class),
                "the 肝胆胰脾 DICOM term");
        // the method device text
        assertEquals("Color Doppler", jdbcTemplate.queryForObject(
                "SELECT device_desc FROM system.exam_method WHERE id = 812", String.class),
                "the 彩色多普勒 Latin text");
    }

    @Test
    void theClosureIsDangleFree() {
        assertEquals(0L, count("SELECT COUNT(*) FROM system.exam_item i "
                + "LEFT JOIN system.exam_body b ON i.body_id = b.id "
                + "WHERE i.remark = ? AND b.id IS NULL", MARK),
                "every seeded item's body resolves");
        assertEquals(0L, count("SELECT COUNT(*) FROM system.exam_body b "
                + "LEFT JOIN system.exam_body_group g ON b.group_id = g.id "
                + "WHERE b.remark = ? AND g.id IS NULL", MARK),
                "every seeded body's group resolves");
        assertEquals(0L, count("SELECT COUNT(*) FROM system.exam_item_method m "
                + "LEFT JOIN system.exam_method e ON m.method_id = e.id "
                + "JOIN system.exam_item i ON m.exam_id = i.id "
                + "WHERE i.remark = ? AND e.id IS NULL", MARK),
                "every seeded method link resolves");
    }
}

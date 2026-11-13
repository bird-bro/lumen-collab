package org.birdbro.lumen.manage;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lineage-seed proof for {@code V4__dict_city.sql} (dict-city-reference task 1.1): after
 * Flyway applies V4, the administrative-divisions reference table must exist in the
 * {@code public} schema of the team test server's {@code lumen} database and hold the
 * 随访 lineage snapshot EXACTLY (design D1 - the seed is the data source, so the row
 * counts are frozen checksums, not the keyed-assertion style of the mutable tables):
 *
 * <ul>
 *   <li>3751 rows total - level 0 (中国 root) x1, level 1 (province) x35, level 2
 *       (city) x373, level 3 (district) x3342;</li>
 *   <li>the 13-column lineage shape in order, {@code Long} bigint id (the recorded
 *       lineage-fidelity deviation) and NO audit block (static reference data);</li>
 *   <li>PK(id) + idx(up_id) + idx(code) per the binding DDL;</li>
 *   <li>the 中国 root ({@code id = 100000}, {@code up_id = 0}, level 0) queryable - the
 *       tree root the service assembles from;</li>
 *   <li>the id sequence advanced past the seeded MAX(id) (V2 setval discipline - without
 *       it the first sequence-driven insert would collide with the seed).</li>
 *   </ul>
 *
 * <p>The table has NO write endpoint (design D3), so exact-count checksums are stable
 * against test interference by construction.</p>
 *
 * @author birdbro
 */
class AuthDictCityMigrationTests extends AuthIntegrationTestBase {

    /** The exact 13-column lineage shape in ordinal order (design D2). */
    private static final List<String> LINEAGE_COLUMNS = Arrays.asList(
            "id", "up_id", "code", "name", "abbreviation", "level", "city_code",
            "zip_code", "merger", "pinyin", "lng", "lat", "initial");

    /** Expected information_schema data_type per column (bigint id deviation + D2). */
    private static final Map<String, String> LINEAGE_DATA_TYPES;

    /** Level -> frozen row count from the lineage snapshot (design D1 checksum). */
    private static final Map<Integer, Integer> LEVEL_CHECKSUMS;

    static {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("id", "bigint");            // Long-entity lineage deviation (design D2)
        types.put("up_id", "integer");
        types.put("code", "character varying");
        types.put("name", "character varying");
        types.put("abbreviation", "character varying");
        types.put("level", "smallint");
        types.put("city_code", "character varying");
        types.put("zip_code", "character varying");
        types.put("merger", "character varying");
        types.put("pinyin", "character varying");
        types.put("lng", "character varying");
        types.put("lat", "character varying");
        types.put("initial", "character varying");
        LINEAGE_DATA_TYPES = types;

        Map<Integer, Integer> checksums = new LinkedHashMap<>();
        checksums.put(0, 1);        // 中国 root
        checksums.put(1, 35);       // provinces (incl. 钓鱼岛)
        checksums.put(2, 373);      // cities
        checksums.put(3, 3342);     // districts
        LEVEL_CHECKSUMS = checksums;
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dictCityTableExistsInPublicSchema() {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = 'dict_city'",
                Integer.class);
        assertEquals(1, found.intValue(),
                "the reference table public.dict_city must exist (user decision: dict "
                + "tables live in the shared public schema)");
    }

    @Test
    void carriesThe13ColumnLineageShapeInOrderWithoutAuditBlock() {
        List<String> orderedColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'dict_city' "
                + "ORDER BY ordinal_position", String.class);
        assertEquals(LINEAGE_COLUMNS, orderedColumns,
                "public.dict_city must carry the exact 13-column lineage shape in order");

        for (Map.Entry<String, String> type : LINEAGE_DATA_TYPES.entrySet()) {
            String dataType = jdbcTemplate.queryForObject(
                    "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'dict_city' "
                    + "AND column_name = ?", String.class, type.getKey());
            assertEquals(type.getValue(), dataType,
                    "column " + type.getKey() + " data type must follow the lineage DDL");
        }
    }

    @Test
    void seededRowChecksumMatchesLineageSnapshot() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_city", Integer.class);
        assertEquals(3751, total.intValue(),
                "the seed must land the full lineage snapshot of 3751 rows");

        Integer orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_city WHERE level IS NULL OR level > 3",
                Integer.class);
        assertEquals(0, orphans.intValue(), "every row sits at one of levels 0-3");

        for (Map.Entry<Integer, Integer> expected : LEVEL_CHECKSUMS.entrySet()) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.dict_city WHERE level = ?",
                    Integer.class, expected.getKey());
            assertEquals(expected.getValue().intValue(), count.intValue(),
                    "level " + expected.getKey() + " row count must match the lineage "
                    + "checksum (design D1)");
        }
    }

    @Test
    void primaryKeyAndLookupIndexesExist() {
        for (String indexName : Arrays.asList(
                "dict_city_pkey", "idx_dict_city_up_id", "idx_dict_city_code")) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' "
                    + "AND indexname = ?", Integer.class, indexName);
            assertEquals(1, found.intValue(),
                    "index " + indexName + " must exist on public.dict_city (binding DDL: "
                    + "PK(id) + idx(up_id) + idx(code))");
        }
    }

    @Test
    void chinaRootIsPresentAsTreeRoot() {
        Map<String, Object> root = jdbcTemplate.queryForMap(
                "SELECT id, up_id, code, name, level, pinyin FROM public.dict_city "
                + "WHERE up_id = 0");
        assertEquals(100000L, ((Number) root.get("id")).longValue(),
                "the 中国 root carries the lineage id 100000");
        assertEquals("0", root.get("code"), "the root's lineage code is '0'");
        assertEquals("中国", root.get("name"), "the root display name");
        assertEquals(0, ((Number) root.get("level")).intValue(), "the root sits at level 0");
        assertEquals("China", root.get("pinyin"), "the root lineage pinyin");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_city WHERE up_id = 0", Integer.class).intValue(),
                "the 中国 root is the ONLY up_id = 0 row (single tree root)");
    }

    @Test
    void idSequenceAdvancedPastSeedMax() {
        Long maxId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM public.dict_city", Long.class);
        assertNotNull(maxId, "the seeded table must hold rows");
        String sequence = jdbcTemplate.queryForObject(
                "SELECT pg_get_serial_sequence('public.dict_city', 'id')", String.class);
        assertNotNull(sequence,
                "the id column must own a serial-style sequence (pg_get_serial_sequence "
                + "resolves it - otherwise IdType.AUTO inserts would fail)");
        Long lastValue = jdbcTemplate.queryForObject(
                "SELECT last_value FROM " + sequence, Long.class);
        assertTrue(lastValue >= maxId,
                "the sequence must sit at/after the seeded MAX(id) " + maxId
                + " (V2 setval discipline) but was " + lastValue);
    }
}

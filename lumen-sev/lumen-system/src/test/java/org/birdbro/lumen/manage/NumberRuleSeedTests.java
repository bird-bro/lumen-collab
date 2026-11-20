package org.birdbro.lumen.manage;

import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Number-rule platform seed proof (number-rule-unification-sev task 2.3; spec
 * number-rule "Number Rule Directory" - Family defaults are seeded): the V40
 * migration seeds the THREE current families' defaults and moves the console entry
 * into the 系统 group with the system:number:* perms (the 患者配置 group retires).
 *
 * @author birdbro
 */
class NumberRuleSeedTests extends AuthIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void familyDefaultsAreSeeded() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT rule_type, prefix, serial_length, is_stamp, stamp, active"
                + " FROM number_rule WHERE rule_type IN ('影像号','登记号','预约号')"
                + " AND deleted = 0 ORDER BY rule_type");
        assertEquals(3, rows.size(), "the three family defaults exist: " + rows);
        for (Map<String, Object> row : rows) {
            assertEquals(1, ((Number) row.get("active")).intValue(),
                    "each seeded family is active: " + row);
            assertEquals("yyMMdd", row.get("stamp"), "the yyMMdd stamp default: " + row);
        }
    }

    @Test
    void typeDictCatalogSeeded() {
        // V44 (number-rule-type-dict): the 编号类型 catalog 1028 is the rule_type
        // vocabulary source - the three base families ride the seed; beyond that the
        // vocabulary is operator-maintained in 字典管理 (the V43 stamp-catalog precedent)
        Map<String, Object> catal = jdbcTemplate.queryForMap(
                "SELECT id, name FROM public.dict_catal WHERE id = 1028 AND deleted = 0");
        assertEquals("编号类型", catal.get("name"), "the catalog anchors by name: " + catal);
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value FROM public.dict_detail WHERE ca_id = 1028"
                + " AND deleted = 0 AND enable = 1 ORDER BY sort", String.class);
        // SUBSET assertion: the vocabulary is operator-maintained beyond the seed
        // (dict UI additions are the feature, not drift) - only the three base
        // families are contractual
        assertTrue(values.containsAll(List.of("影像号", "登记号", "预约号")),
                "the three base families are seeded (operator additions ride along): " + values);
    }

    @Test
    void modalityScopeColumnAndCoalesceUniqueIndex() {
        // V49 (number-rule-modality-scope task 1.1): number_rule gains the nullable
        // modality scope - the dict-1024 VALUE token (CT/DR/US), varchar(16), and
        // NULL = 全局 (design D1). The partial unique index recreates as the COALESCE
        // expression form so two GLOBAL actives of one family stay rejected (PG treats
        // NULLs as distinct in a plain partial unique index, design D2).
        Map<String, Object> column = jdbcTemplate.queryForMap(
                "SELECT data_type, character_maximum_length FROM information_schema.columns"
                + " WHERE table_schema = 'system' AND table_name = 'number_rule'"
                + " AND column_name = 'modality'");
        assertEquals("character varying", column.get("data_type"), "the scope stores the token: " + column);
        assertEquals(16, ((Number) column.get("character_maximum_length")).intValue(),
                "the token column is varchar(16) (dict-1024 value shape): " + column);

        String indexdef = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'system'"
                + " AND indexname = 'uk_number_rule_active'", String.class);
        assertTrue(indexdef.contains("UNIQUE"), "the active-pool index stays UNIQUE: " + indexdef);
        assertTrue(indexdef.contains("COALESCE(modality"), "the NULL-safe uniqueness keys on"
                + " COALESCE(modality, '') so two global actives still collide: " + indexdef);
        assertTrue(indexdef.contains("rule_type") && indexdef.contains("active = 1")
                        && indexdef.contains("deleted = 0"),
                "the index stays the per-family active partial form: " + indexdef);
    }

    @Test
    void seededFamilyRowsAreTheGlobalRules() {
        // No data backfill (design D8): every existing row simply becomes its family's
        // GLOBAL rule - modality stays NULL, the platform-wide shared pool. Per-family
        // >=1 (not an exact count): the unique index guarantees the exactly-one-active
        // shape, and operator-added rows on the shared test server ride along.
        for (String family : new String[] {"影像号", "登记号", "预约号"}) {
            Integer globalRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM number_rule WHERE rule_type = ?"
                    + " AND active = 1 AND deleted = 0 AND modality IS NULL",
                    Integer.class, family);
            assertTrue(globalRows != null && globalRows >= 1,
                    "family " + family + " keeps an active GLOBAL rule (modality NULL)");
        }
    }

    @Test
    void consoleEntryMovedToSystemGroup() {
        // CONTENT-anchored (the V40 philosophy - ids are data, perms are the contract):
        // a ledger repair may re-seed the rows under fresh ids, the perms string is
        // the stable key
        Map<String, Object> page = jdbcTemplate.queryForMap(
                "SELECT up_id, path, perms, component, deleted FROM system.menu_info"
                + " WHERE type = 'C' AND perms = 'system:number:list' AND deleted = 0");
        assertEquals(15, ((Number) page.get("up_id")).intValue(),
                "号码规则 joins the 系统 (15) group: " + page);
        assertEquals("number-rules", page.get("path"), "the route segment follows the system surface");
        assertEquals("system:number:list", page.get("perms"), "the page perm migrates");
        assertEquals("system/number-rule/index", page.get("component"),
                "the component key follows the system IA (the V41 completion of the V40 "
                + "move - the web console route map keys on the component column; the stale "
                + "patient seed key left the leaf unclickable): " + page);

        List<Map<String, Object>> buttons = jdbcTemplate.queryForList(
                "SELECT id, perms FROM system.menu_info WHERE up_id ="
                + " (SELECT id FROM system.menu_info WHERE type = 'C'"
                + " AND perms = 'system:number:list' AND deleted = 0) AND deleted = 0"
                + " ORDER BY sort");
        assertEquals(3, buttons.size(), "the three button F nodes survive the move");
        assertTrue(String.valueOf(buttons.get(0).get("perms")).startsWith("system:number:"),
                "the button perms migrate to the system family: " + buttons);

        Integer liveGroups = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE type = 'M' AND title = '患者配置'"
                + " AND deleted = 0", Integer.class);
        assertEquals(0, liveGroups,
                "the empty 患者配置 group stays OUT of the live tree (the V40 retirement "
                + "contract - soft-deleted OR hard-gone both satisfy it; the shared-DB "
                + "housekeeping owns the row's physical fate)");
    }
}

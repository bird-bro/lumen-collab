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

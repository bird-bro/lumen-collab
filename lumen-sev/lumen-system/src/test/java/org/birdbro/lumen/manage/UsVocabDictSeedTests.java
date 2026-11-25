package org.birdbro.lumen.manage;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * US workbench vocabulary dict seed proof (us-vocab-dict-catalogs; user ruling
 * 2026-11-24): V52 seeds the 「患者来源」 catalog 1023 and the 「妊娠状态」 catalog
 * 1002 that the registration workbench resolves id-anchored alongside the V6
 * 性别 (1004) / 证件类型 (1006) catalogs. The stored token for both string
 * columns is the dict VALUE text (the modality catalogs' value-keyed shape).
 *
 * @author birdbro
 */
class UsVocabDictSeedTests extends AuthIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void patientSourceCatalogSeeded() {
        // V52 (us-vocab-dict-catalogs): catalog 1023 reuses the dissolved 诊室类型
        // band slot V27 retired unseeded - the id was free, the ruling reassigns it
        Map<String, Object> catal = jdbcTemplate.queryForMap(
                "SELECT id, name FROM public.dict_catal WHERE id = 1023 AND deleted = 0");
        assertEquals("患者来源", catal.get("name"), "the catalog anchors by name: " + catal);
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value FROM public.dict_detail WHERE ca_id = 1023"
                + " AND deleted = 0 AND enable = 1 ORDER BY sort", String.class);
        // SUBSET assertion: the vocabulary is operator-maintained beyond the seed
        assertTrue(values.containsAll(List.of("门诊", "住院", "急诊", "体检")),
                "the four base sources are seeded (operator additions ride along): " + values);
    }

    @Test
    void pregnancyStatusCatalogSeeded() {
        // V52: catalog 1002 reuses the mislabeled rcp ORG_TYPE slot V6 excluded -
        // likewise never seeded before this ruling
        Map<String, Object> catal = jdbcTemplate.queryForMap(
                "SELECT id, name FROM public.dict_catal WHERE id = 1002 AND deleted = 0");
        assertEquals("妊娠状态", catal.get("name"), "the catalog anchors by name: " + catal);
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value FROM public.dict_detail WHERE ca_id = 1002"
                + " AND deleted = 0 AND enable = 1 ORDER BY sort", String.class);
        assertTrue(values.containsAll(List.of("未孕", "早孕", "中晚孕", "未知")),
                "the four base statuses are seeded (operator additions ride along): " + values);
    }
}

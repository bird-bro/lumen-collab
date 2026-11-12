package org.birdbro.lumen.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Device/room registry migration proof (org-device-room-api task 1.1; spec deltas
 * system-data "Org Device Core Columns" / "Org Device Access and Acquisition Columns" /
 * "Org Room Core Columns" / "Org Room Binding and Call Columns" / "Device and Room Dict
 * Catalogs" / "Device and Room Dict Values" / "Device and Room Menu Pages" / "Device and
 * Room Button Perms"): the V27 migration creates the split tables {@code system.org_device}
 * (asset + access/acquisition; {@code type} is varchar storing the dict-1024 VALUE, the
 * modality token 'CT'/'DR' - ruling 2026-11-12) and {@code system.org_room} (business
 * place + 叫号 + rebindable {@code dev_id}, zero physical FKs, NO type column - the
 * 诊室类型 concept is dissolved, ruling 2026-11-12), seeds the dict catalogs 1024-1026
 * (deterministic ids, detail bands {@code ca_id * 1000 + sort}) and RETIRES V26's
 * 检查类型 1021 - 1024 设备类型 is the SINGLE modality vocabulary serving both
 * device and exam references (unification ruling 2026-11-12; 1023 stays unseeded),
 * and seeds the console
 * menus 18/19 under 组织架构 with their F button nodes + admin auto-grant. Re-execution is
 * a row-count no-op.
 *
 * <p>Shared long-lived database: every assertion is an existence/contains-style check,
 * never an exact row count outside the migration's own seed set.</p>
 *
 * @author birdbro
 */
class AuthDeviceRoomSeedTests extends AuthIntegrationTestBase {

    /** The org_device columns V27 creates, in the ruled order (audit block the tail). */
    private static final String[] DEVICE_COLUMNS = {
            "id", "code", "name", "type", "status", "tag", "brand", "model", "spec",
            "serial_num", "manufacturer", "production_date", "purchase_date",
            "acceptance_date", "responsible", "responsible_phone", "start_date",
            "end_date", "remark", "oid", "did", "ae", "ip", "port", "box_adapter",
            "box_ip", "box_port", "box_mac", "box_print", "capture_w", "capture_h",
            "prune", "prune_u", "prune_d", "prune_l", "prune_r",
            "deleted", "create_by", "create_time", "update_by", "update_time"};

    /** The org_room columns V27 creates (NO device inline column, NO type column). */
    private static final String[] ROOM_COLUMNS = {
            "id", "oid", "did", "name", "code", "enable",
            "call_ip", "call_port", "call_mac", "dev_id",
            "deleted", "create_by", "create_time", "update_by", "update_time"};

    /** OPS inline-shape columns that must NEVER appear on org_room (the split ruling). */
    private static final String[] FORBIDDEN_ROOM_COLUMNS = {
            "dev_ae", "dev_ip", "dev_port", "dev_mac", "dev_brand", "dev_version",
            "dev_iot", "dev_about", "dev_tag", "dev_start_date", "dev_end_date",
            "box_adapter", "box_ip", "box_port", "box_mac", "box_print",
            "capture_w", "capture_h", "prune", "prune_u", "prune_d", "prune_l", "prune_r"};

    /** {catalog id, name} of the three seeded catalogs (1023 + 检查类型 stay absent). */
    private static final String[][] CATALOGS = {
            {"1024", "设备类型"}, {"1025", "设备状态"}, {"1026", "归属类型"}};

    /** {ca_id, code, value} of every seeded detail, in the ruled vocabularies. */
    private static final String[][] DETAILS = {
            {"1024", "1", "DR"}, {"1024", "2", "CT"}, {"1024", "3", "MR"},
            {"1024", "4", "DSA"}, {"1024", "5", "US"}, {"1024", "6", "ENDO"},
            {"1025", "1", "在用"}, {"1025", "2", "停用"}, {"1025", "3", "维修"},
            {"1025", "4", "报废"},
            {"1026", "10", "自购"}, {"1026", "11", "租赁"}};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deviceAndRoomTablesExistWithRuledColumns() {
        assertTableColumns("org_device", DEVICE_COLUMNS);
        assertTableColumns("org_room", ROOM_COLUMNS);

        // NOT NULL anchors (spec: device oid required/did optional; room oid+did required).
        assertEquals("NO", columnNullability("org_device", "oid"),
                "org_device.oid must be NOT NULL");
        assertEquals("YES", columnNullability("org_device", "did"),
                "org_device.did must stay nullable - a device may hang off the org alone");
        assertEquals("NO", columnNullability("org_room", "oid"),
                "org_room.oid must be NOT NULL");
        assertEquals("NO", columnNullability("org_room", "did"),
                "org_room.did must be NOT NULL");

        // The split ruling's hard line: no OPS inline device/box/capture column on the room.
        Integer forbidden = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = 'org_room' "
                        + "AND column_name = ANY (?)",
                Integer.class, (Object) FORBIDDEN_ROOM_COLUMNS);
        assertEquals(0, forbidden.intValue(),
                "org_room must carry NO inline device/box/capture column");

        // The 2026-11-12 type rulings: device type is the modality TOKEN (varchar),
        // the room carries no type at all (rooms hang off departments).
        assertEquals("character varying", columnDataType("org_device", "type"),
                "org_device.type must be varchar - it stores the dict-1024 VALUE token");
        Integer roomType = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = 'org_room' "
                        + "AND column_name = 'type'",
                Integer.class);
        assertEquals(0, roomType.intValue(), "org_room must carry NO type column");
    }

    /** The unification ruling: ONE modality catalog - 检查类型 stays retired, 1024
     * 设备类型 is the single source for device AND exam references. */
    @Test
    void examTypeCatalogRetiredSingleModalitySource() {
        Integer retired = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_catal WHERE name = '检查类型' AND deleted = 0",
                Integer.class);
        assertEquals(0, retired.intValue(),
                "the 检查类型 catalog must stay retired (1024 设备类型 is the single source)");
        Integer orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_detail WHERE ca_id NOT IN "
                        + "(SELECT id FROM public.dict_catal WHERE deleted = 0)",
                Integer.class);
        assertEquals(0, orphans.intValue(),
                "no orphaned 检查类型 details may survive the retirement");
    }

    @Test
    void registryTablesStayAtZeroPhysicalForeignKeys() {
        for (String table : new String[] {"org_device", "org_room"}) {
            Integer fk = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint con "
                            + "JOIN pg_class rel ON rel.oid = con.conrelid "
                            + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                            + "WHERE ns.nspname = 'system' AND rel.relname = ? "
                            + "AND con.contype = 'f'",
                    Integer.class, table);
            assertEquals(0, fk.intValue(),
                    table + " must carry zero physical FKs - references are service-enforced");
        }
    }

    @Test
    void deviceRoomDictCatalogsSeeded() {
        for (String[] catalog : CATALOGS) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.dict_catal WHERE id = ? AND name = ?",
                    Integer.class, Integer.valueOf(catalog[0]), catalog[1]);
            assertEquals(1, found.intValue(),
                    "dict catalog " + catalog[1] + " must exist with the deterministic id "
                            + catalog[0]);
        }
        for (String[] detail : DETAILS) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.dict_detail "
                            + "WHERE ca_id = ? AND code::text = ? AND value = ? AND enable = 1",
                    Integer.class, Integer.valueOf(detail[0]), detail[1], detail[2]);
            assertEquals(1, found.intValue(),
                    "dict detail " + detail[2] + " must exist under catalog " + detail[0]);
        }
    }

    @Test
    void deviceRoomMenusAndButtonPermsSeeded() {
        // The two C pages under 组织架构 (menu 13) - CONTENT contract, ids are data
        // (the corrected menu seeding: serial inserts, no pinned ids).
        for (String[] page : new String[][] {
                {"设备档案", "system:device:list", "device", "system/device/index"},
                {"诊室配置", "system:room:list", "room", "system/room/index"}}) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info WHERE up_id = 13 "
                            + "AND type = 'C' AND title = ? AND perms = ? AND path = ? "
                            + "AND component = ? AND deleted = 0",
                    Integer.class, page[0], page[1], page[2], page[3]);
            assertEquals(1, found.intValue(), "menu " + page[0] + " must exist under 组织架构");
        }

        // The F button nodes (7 across the two pages).
        for (String[] node : new String[][] {
                {"设备档案", "system:device:create"}, {"设备档案", "system:device:update"},
                {"设备档案", "system:device:delete"}, {"诊室配置", "system:room:create"},
                {"诊室配置", "system:room:update"}, {"诊室配置", "system:room:status"},
                {"诊室配置", "system:room:delete"}}) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info WHERE up_id = (SELECT id FROM "
                            + "system.menu_info WHERE type = 'C' AND title = ? AND deleted = 0) "
                            + "AND type = 'F' AND perms = ? AND deleted = 0",
                    Integer.class, node[0], node[1]);
            assertEquals(1, found.intValue(),
                    "F node " + node[1] + " must exist under " + node[0]);
        }

        // The admin auto-grant: role 1 owns the two C pages and every F node.
        Integer granted = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT m.id) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 AND ("
                        + "(m.type = 'C' AND m.perms IN ('system:device:list','system:room:list')) "
                        + "OR m.perms LIKE 'system:device:%' OR m.perms LIKE 'system:room:%')",
                Integer.class);
        assertEquals(9, granted.intValue(),
                "role 1 must own the two C pages and all seven F nodes");
    }

    @Test
    void seedStatementsReExecuteAsARowCountNoOp() throws Exception {
        long catalogsBefore = catalogCount();
        long detailsBefore = detailCount();
        long fNodesBefore = fNodeCount();
        long grantsBefore = grantCount();

        String sql = Files.readString(Path.of(
                getClass().getResource("/db/migration/V27__org_device_room.sql").toURI()));
        for (String statement : splitStatements(sql)) {
            jdbcTemplate.execute(statement);
        }

        assertEquals(catalogsBefore, catalogCount(), "re-run must not add catalogs");
        assertEquals(detailsBefore, detailCount(), "re-run must not add details");
        assertEquals(fNodesBefore, fNodeCount(), "re-run must not add F nodes");
        assertEquals(grantsBefore, grantCount(), "re-run must not add grants");
    }

    // ===================== helpers =====================

    private void assertTableColumns(String table, String[] columns) {
        for (String column : columns) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'system' AND table_name = ? "
                            + "AND column_name = ?",
                    Integer.class, table, column);
            assertEquals(1, found.intValue(),
                    "system." + table + " must carry the column " + column);
        }
    }

    /** The information_schema is_nullable verdict for one registry column. */
    private String columnNullability(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = ? "
                        + "AND column_name = ?",
                String.class, table, column);
    }

    /** The information_schema data type verdict for one registry column. */
    private String columnDataType(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = ? "
                        + "AND column_name = ?",
                String.class, table, column);
    }

    private long catalogCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_catal WHERE id IN (1024, 1025, 1026)",
                Long.class);
        return count == null ? 0 : count;
    }

    private long detailCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_detail WHERE ca_id IN (1024, 1025, 1026)",
                Long.class);
        return count == null ? 0 : count;
    }

    private long fNodeCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE type = 'F' AND deleted = 0 "
                        + "AND (perms LIKE 'system:device:%' OR perms LIKE 'system:room:%')",
                Long.class);
        return count == null ? 0 : count;
    }

    private long grantCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 "
                        + "AND ((m.type = 'C' AND m.perms IN ('system:device:list',"
                        + "'system:room:list')) OR m.perms LIKE 'system:device:%' "
                        + "OR m.perms LIKE 'system:room:%')",
                Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Strips comment lines, drops empties - the executable statement stream (the V15/V22
     * house split; V27 carries no dollar-quoted function body, so the plain split holds -
     * the REMARK/comment strings carry no semicolons, the V21 discipline).
     */
    private static List<String> splitStatements(String sql) {
        return java.util.Arrays.stream(sql.split(";"))
                .map(part -> part.lines()
                        .filter(line -> !line.strip().startsWith("--"))
                        .reduce((a, b) -> a + "\n" + b)
                        .map(String::strip)
                        .orElse(""))
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
}

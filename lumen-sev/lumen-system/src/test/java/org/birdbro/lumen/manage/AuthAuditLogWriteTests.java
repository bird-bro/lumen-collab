package org.birdbro.lumen.manage;

import java.util.Date;
import java.util.Map;

import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.service.ILogInfoService;
import org.birdbro.framework.log.service.LogSignInsertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import cn.hutool.core.util.IdUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit-log WRITE-path proof (audit-log-unify-api tasks 2.1-2.4, design D2/D3/D5): BOTH
 * insert services land everything in the ONE table {@code public.log_info} - operation
 * rows through the single translation point ({@code LogTypes.codeOf} AFTER the copy:
 * the {@code @Log} String label becomes the Integer code at rest, unknown/blank labels
 * degrade to a NULL type, the row still writes), sign events as Integer-code passthrough
 * ({@code Constants.SUCCESS/FAIL/SIGN_OUT}) with the credential token anchored in the
 * existing dead {@code session} column (D5) for the query side's select-exclusion.
 *
 * <p>Seeding is a DIRECT service call (synchronous - no async poll needed, unlike the
 * aspect-driven suites), reads are plain JDBC keyed by per-run unique account markers
 * (never global row counts - shared long-lived database), and this run's fixture rows
 * are hard-DELETEd inline (user ruling 2026-11-07); the base sweep's {@code b2} account
 * family is the interrupted-run safety net.</p>
 *
 * @author birdbro
 */
class AuthAuditLogWriteTests extends AuthIntegrationTestBase {

    @Autowired
    private ILogInfoService logInfoService;

    @Autowired
    private LogSignInsertService logSignService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Task 2.1 (design D2/D3): a {@code @Log(type="新增")}-shaped {@code LogInfo} lands
     * {@code type = 1}; an unknown label lands a NULL type with the row STILL written
     * (degrade, never break the write path); blank degrades the same way. The known row
     * also pins the copy sanity (title/uid/date_ym carry through beside the translation).
     */
    @Test
    void operationInsertTranslatesKnownLabelAndDegradesUnknownOnes() {
        long ts = System.currentTimeMillis();
        String marker = "b2.op." + ts;
        int currentYm = Integer.parseInt(DateUtils.dateTimeNow("yyyyMM"));
        try {
            // Known label 新增 -> code 1 (the five-label LogTypes vocabulary).
            assertTrue(logInfoService.insert(LogInfo.builder()
                    .account(marker + "-known").uid(410001).title("b2操作标题")
                    .descr("b2操作探测").type("新增").actionTime(new Date(ts)).name("b2op")
                    .spent(5L).build()), "the known-label operation row must insert");
            Map<String, Object> known = jdbcTemplate.queryForMap(
                    "SELECT type, title, uid, date_ym FROM public.log_info "
                            + "WHERE account = ?", marker + "-known");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_info WHERE account = ?",
                    Integer.class, marker + "-known").intValue(),
                    "the known-label row must be written");
            assertEquals(1, ((Number) known.get("type")).intValue(),
                    "@Log label 新增 must land as code 1");
            assertEquals("b2操作标题", known.get("title"), "title must carry through the copy");
            assertEquals(410001, ((Number) known.get("uid")).intValue(), "uid must carry through the copy");
            assertEquals(currentYm, ((Number) known.get("date_ym")).intValue(),
                    "date_ym must be stamped with the current month");

            // Unknown label: degrade to NULL type - the row is STILL written (design D2).
            assertTrue(logInfoService.insert(LogInfo.builder()
                    .account(marker + "-unknown").uid(410002).title("b2操作标题")
                    .descr("b2操作探测").type("未知标签" + ts).actionTime(new Date(ts)).name("b2op")
                    .build()), "the unknown-label operation row must still insert");
            Map<String, Object> unknown = jdbcTemplate.queryForMap(
                    "SELECT type FROM public.log_info WHERE account = ?",
                    marker + "-unknown");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_info WHERE account = ?",
                    Integer.class, marker + "-unknown").intValue(),
                    "an unknown label must NOT break the write path - the row lands");
            assertNull(unknown.get("type"), "an unknown label must degrade to a NULL type");

            // Blank label edge: same degrade-to-NULL behavior.
            assertTrue(logInfoService.insert(LogInfo.builder()
                    .account(marker + "-blank").uid(410003).title("b2操作标题")
                    .type("").actionTime(new Date(ts)).name("b2op")
                    .build()), "the blank-label operation row must still insert");
            Map<String, Object> blank = jdbcTemplate.queryForMap(
                    "SELECT type FROM public.log_info WHERE account = ?",
                    marker + "-blank");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_info WHERE account = ?",
                    Integer.class, marker + "-blank").intValue(),
                    "the blank-label row lands");
            assertNull(blank.get("type"), "a blank label must degrade to a NULL type");
        } finally {
            sweepMarker(marker);
        }
    }

    /**
     * Task 2.3 (design D2/D5): a {@code LogSign} carrying SUCCESS/FAIL/SIGN_OUT plus the
     * token anchor lands a {@code log_info} row with the Integer code PASSED THROUGH,
     * title = msg (Sign-IN/Sign-OUT), descr 1:1, and {@code session} = the anchor UUID.
     * The dropped log_sign family stays dropped (D1) - the retarget must not re-create it.
     */
    @Test
    void signInsertLandsInLogInfoWithCodePassthroughAndSessionAnchor() {
        long ts = System.currentTimeMillis();
        String marker = "b2.sg." + ts;
        String successAnchor = IdUtil.randomUUID();
        String failAnchor = IdUtil.randomUUID();
        String outAnchor = IdUtil.randomUUID();
        try {
            // SUCCESS (10) with the token anchor.
            assertTrue(logSignService.insert(LogSign.builder()
                    .account(marker + "-in").uid(410011).msg("Sign-IN")
                    .descr("b2登录成功探测").type(Constants.SUCCESS).token(successAnchor)
                    .actionTime(new Date(ts)).ip("127.0.0.1").build()),
                    "the SUCCESS sign row must insert");
            assertSignRow(marker + "-in", Constants.SUCCESS, "Sign-IN", successAnchor, "b2登录成功探测");

            // FAIL (11) passthrough.
            assertTrue(logSignService.insert(LogSign.builder()
                    .account(marker + "-fail").uid(410012).msg("Sign-IN")
                    .descr("b2登录失败探测").type(Constants.FAIL).token(failAnchor)
                    .actionTime(new Date(ts)).ip("127.0.0.1").build()),
                    "the FAIL sign row must insert");
            assertSignRow(marker + "-fail", Constants.FAIL, "Sign-IN", failAnchor, "b2登录失败探测");

            // SIGN_OUT (12) passthrough.
            assertTrue(logSignService.insert(LogSign.builder()
                    .account(marker + "-out").uid(410013).msg("Sign-OUT")
                    .descr("b2登出探测").type(Constants.SIGN_OUT).token(outAnchor)
                    .actionTime(new Date(ts)).ip("127.0.0.1").build()),
                    "the SIGN_OUT sign row must insert");
            assertSignRow(marker + "-out", Constants.SIGN_OUT, "Sign-OUT", outAnchor, "b2登出探测");

            // And the dropped log_sign relation stays dropped (D1: no sign re-creation).
            String signRelation = jdbcTemplate.queryForObject(
                    "SELECT to_regclass('public.log_sign')::text", String.class);
            assertNull(signRelation, "the log_sign relation must stay dropped (D1)");
        } finally {
            sweepMarker(marker);
        }
    }

    /** Keyed row assertion: code passthrough + title = msg + descr 1:1 + session = anchor. */
    private void assertSignRow(String account, Integer expectedType, String expectedTitle,
            String anchor, String expectedDescr) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT type, title, descr, session FROM public.log_info "
                        + "WHERE account = ?", account);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.log_info WHERE account = ?",
                Integer.class, account).intValue(),
                "the sign row must land in public.log_info (account " + account + ")");
        assertEquals(expectedType.intValue(), ((Number) row.get("type")).intValue(),
                "the sign event code must pass through unchanged");
        assertEquals(expectedTitle, row.get("title"), "title must carry the sign msg");
        assertEquals(expectedDescr, row.get("descr"), "descr must carry through 1:1");
        assertEquals(anchor, row.get("session"),
                "the credential token anchor must land in the session column (design D5)");
    }

    /** Inline hard-DELETE of this run's marker family (ruling 2026-11-07; sweep = net). */
    private void sweepMarker(String marker) {
        jdbcTemplate.update("DELETE FROM public.log_info WHERE account LIKE ? ESCAPE '\\'",
                likePrefix(marker));
    }

    /** LIKE prefix pattern for a marker (backslash is PostgreSQL's escape). */
    private String likePrefix(String marker) {
        return marker.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}

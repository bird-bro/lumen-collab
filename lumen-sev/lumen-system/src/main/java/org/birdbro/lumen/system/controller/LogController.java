package org.birdbro.lumen.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;

import cn.hutool.core.util.StrUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.service.ILogInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Unified audit-log query endpoint (audit-log-unify-api design D6/D8): the SINGLE
 * paginated read of {@code public.log_info} serving BOTH event families — the
 * {@code @Log}-annotated operation events (type codes 1–5) and the login/logout sign
 * events (codes 10/11/12) — under the {@code /api/v1/system} namespace nginx routes
 * to this service (Gateway Path Convention). Token-guarded by the copied security
 * chain; read-only — the log table is append-write, there is deliberately no mutation
 * endpoint (Out of scope: log delete/archive strategies).
 *
 * <p>The optional {@code type}/{@code uid}/{@code account}/{@code dateYm} filters
 * AND-combine; rows order {@code action_time DESC} with the {@code id DESC} tie-break.
 * The former sign-log sibling {@code GET /sign} (sign-log-query task 2.2) is retired
 * together with its {@code system:signlog:list} method gate — the sensitivity split
 * behind the dual-permission design collapsed (both event families carry the same
 * UA/IP columns; coordination audit-log-unify D4) and both live in this one table
 * and this one permission. Response rows NEVER carry the {@code session} column —
 * the sign-event credential anchor stays server-side (design D5, the select
 * excludes it at the service layer).</p>
 *
 * @author birdbro
 */
@Api(tags = "审计日志")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:log:list')")
@RestController
@RequestMapping("/api/v1/system/logs")
public class LogController {

    /** Hard cap on {@code pageSize} - a fetch-everything request must not page the whole log. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ILogInfoService logInfoService;

    public LogController(ILogInfoService logInfoService) {
        this.logInfoService = logInfoService;
    }

    /** Strict {@code yyyy-MM-dd} day format for the date-range bounds (log-query-date-range D2). */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Paginated audit-log query ({@code page}/{@code pageSize} default 1/20,
     * {@code pageSize} capped at {@value #MAX_PAGE_SIZE} and floored at 1; the optional
     * {@code type} Integer-code/{@code uid}/{@code account}/{@code dateYm} filters plus
     * the {@code beginDate}/{@code endDate} date-range bounds (log-query-date-range:
     * additive — the pre-existing filters are retained) AND-combine; both event
     * families, newest first, session column excluded. Range bounds are strict
     * {@code yyyy-MM-dd} days, each optional; day semantics are double-closed —
     * {@code beginDate} includes its whole day, {@code endDate} includes its whole
     * day (converted to the exclusive next-day 00:00 upper bound at the service
     * boundary). An unparseable bound or {@code beginDate} after {@code endDate}
     * rejects as {@code A0400}.
     */
    @ApiOperation("审计日志分页查询（type 码/uid/account/dateYm/beginDate-endDate 日期段可选过滤，actionTime 倒序，不含 session）")
    @GetMapping
    public IPage<LogInfoEntity> list(@RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer uid,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) Integer dateYm,
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate) {
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), MAX_PAGE_SIZE);
        LocalDate begin = parseDay(beginDate, "beginDate");
        LocalDate end = parseDay(endDate, "endDate");
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new BusinessException("A0400", "参数[beginDate]不得晚于参数[endDate]");
        }
        // Double-closed day bounds (design D2): begin keeps its whole day; the end day
        // converts to the exclusive next-day 00:00 upper bound.
        LocalDateTime beginInclusive = begin == null ? null : begin.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();
        return logInfoService.getPage(safePage, safeSize, type, uid, account, dateYm, beginInclusive, endExclusive);
    }

    /** Parses an optional strict {@code yyyy-MM-dd} bound; blank = absent, otherwise A0400 on failure. */
    private static LocalDate parseDay(String value, String param) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DAY_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BusinessException("A0400", "参数[" + param + "]须为 yyyy-MM-dd 格式日期");
        }
    }
}

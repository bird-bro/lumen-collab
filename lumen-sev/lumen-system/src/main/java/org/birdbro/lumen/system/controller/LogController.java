package org.birdbro.lumen.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.service.ILogInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

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

    /**
     * Paginated audit-log query ({@code page}/{@code pageSize} default 1/20,
     * {@code pageSize} capped at {@value #MAX_PAGE_SIZE} and floored at 1; the optional
     * {@code type} Integer-code/{@code uid}/{@code account}/{@code dateYm} filters
     * AND-combine; both event families, newest first, session column excluded).
     */
    @ApiOperation("审计日志分页查询（type 码/uid/account/dateYm 可选过滤，actionTime 倒序，不含 session）")
    @GetMapping
    public IPage<LogInfoEntity> list(@RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer uid,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) Integer dateYm) {
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), MAX_PAGE_SIZE);
        return logInfoService.getPage(safePage, safeSize, type, uid, account, dateYm);
    }
}

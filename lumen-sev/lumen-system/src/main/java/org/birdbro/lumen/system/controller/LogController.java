package org.birdbro.lumen.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.entity.LogSignEntity;
import org.birdbro.framework.log.service.ILogInfoService;
import org.birdbro.framework.log.service.ILogSignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Operation-log query endpoint (system-data task 3.2): paginated {@code public.log_info}
 * reads with the optional {@code type}/{@code uid}/{@code dateYm} filters and the
 * {@code action_time DESC} ordering (spec scenario 7), under the {@code /api/v1/system}
 * namespace nginx routes to this service (Gateway Path Convention). Token-guarded by the
 * copied security chain ({@code anyRequest().authenticated()}); read-only - the log
 * tables are append-write, there is deliberately no mutation endpoint (Out of scope:
 * log delete/archive strategies).
 *
 * <p>The sign-log sibling {@code GET /sign} (sign-log-query task 2.2) pages
 * {@code public.log_sign} under its OWN method-level {@code system:signlog:list} gate -
 * for that method the method-level annotation replaces the class-level one (Spring
 * method-security precedence, design D4), making the two page permissions independent.</p>
 *
 * @author birdbro
 */
@Api(tags = "操作日志")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:log:list')")
@RestController
@RequestMapping("/api/v1/system/logs")
public class LogController {

    /** Hard cap on {@code pageSize} - a fetch-everything request must not page the whole log. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ILogInfoService logInfoService;

    /**
     * sign-log-query: 登录日志查询服务。Field-injected so the pre-existing constructor
     * stays untouched (never-modify rule; the alternative - a widened or second
     * constructor - would change or duplicate the existing public signature).
     */
    @Autowired
    private ILogSignService logSignService;

    public LogController(ILogInfoService logInfoService) {
        this.logInfoService = logInfoService;
    }

    /**
     * Paginated log query ({@code page}/{@code pageSize} default 1/20, {@code pageSize}
     * capped at {@value #MAX_PAGE_SIZE} and floored at 1; the optional
     * {@code type}/{@code uid}/{@code dateYm} filters AND-combine).
     */
    @ApiOperation("操作日志分页查询（type/uid/dateYm 可选过滤，actionTime 倒序）")
    @GetMapping
    public IPage<LogInfoEntity> list(@RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer uid,
            @RequestParam(required = false) Integer dateYm) {
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), MAX_PAGE_SIZE);
        return logInfoService.getPage(safePage, safeSize, type, uid, dateYm);
    }

    /**
     * Paginated sign-log query ({@code page}/{@code pageSize} default 1/20, the same
     * {@value #MAX_PAGE_SIZE} cap and the 1 floor; the optional {@code account}/{@code
     * uid}/{@code dateYm} filters AND-combine). Rows never carry the {@code token}
     * credential column - the service select excludes it (design D7). The METHOD-level
     * gate below replaces the class-level one for this method (design D4): holding
     * {@code system:log:list} alone grants nothing here, and vice versa.
     */
    @ApiOperation("登录日志分页查询（account/uid/dateYm 可选过滤，actionTime 倒序，不含 token）")
    @PreAuthorize("hasAuthority('system:signlog:list')")
    @GetMapping("/sign")
    public IPage<LogSignEntity> sign(@RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) Integer uid,
            @RequestParam(required = false) Integer dateYm) {
        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeSize = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), MAX_PAGE_SIZE);
        return logSignService.getPage(safePage, safeSize, account, uid, dateYm);
    }
}

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

/**
 * Operation-log query endpoint (system-data task 3.2): paginated {@code public.log_info}
 * reads with the optional {@code type}/{@code uid}/{@code dateYm} filters and the
 * {@code action_time DESC} ordering (spec scenario 7), under the {@code /api/v1/system}
 * namespace nginx routes to this service (Gateway Path Convention). Token-guarded by the
 * copied security chain ({@code anyRequest().authenticated()}); read-only - the log
 * tables are append-write, there is deliberately no mutation endpoint (Out of scope:
 * log delete/archive strategies).
 *
 * @author birdbro
 */
@Api(tags = "操作日志")
@RestController
@RequestMapping("/api/v1/system/logs")
public class LogController {

    private final ILogInfoService logInfoService;

    public LogController(ILogInfoService logInfoService) {
        this.logInfoService = logInfoService;
    }

    /**
     * Paginated log query ({@code page}/{@code pageSize} default 1/20; the optional
     * {@code type}/{@code uid}/{@code dateYm} filters AND-combine).
     */
    @ApiOperation("操作日志分页查询（type/uid/dateYm 可选过滤，actionTime 倒序）")
    @GetMapping
    public IPage<LogInfoEntity> list(@RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer uid,
            @RequestParam(required = false) Integer dateYm) {
        return logInfoService.getPage(page, pageSize, type, uid, dateYm);
    }
}

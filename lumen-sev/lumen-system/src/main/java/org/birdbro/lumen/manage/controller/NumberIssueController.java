package org.birdbro.lumen.manage.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.lumen.manage.dto.NumberIssueRequest;
import org.birdbro.lumen.manage.dto.NumberIssueResponse;
import org.birdbro.lumen.manage.service.NumberIssuerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The MACHINE issuance endpoint (number-rule-unification-sev task 2.2, design D4; spec
 * number-rule "Internal Service Issuance Contract"): services issue numbers here. It
 * is deliberately a SEPARATE controller from the user-facing {@link NumberRuleController}
 * - it carries NO @PreAuthorize (there is no user behind a service call) and the path
 * sits on the permit-all list, guarded by the {@code X-Internal-Key} interceptor
 * (manage/config/InternalKeyInterceptor) instead. The envelope wraps the payload via
 * the framework advice - exactly the shape the {@code NumberApi} Feign contract
 * decodes.
 *
 * @author birdbro
 */
@Api(tags = "号码发放（内部服务）")
@RestController
@RequestMapping("/api/v1/system/number-rules")
public class NumberIssueController {

    @Autowired
    private NumberIssuerService numberIssuerService;

    @ApiOperation("发放一个号码（内部服务专用，X-Internal-Key 门控）")
    @PostMapping("/issue")
    public NumberIssueResponse issue(@RequestBody NumberIssueRequest request) {
        String number = numberIssuerService.issue(request.getRuleType());
        return NumberIssueResponse.builder().number(number).build();
    }
}

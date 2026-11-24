package org.birdbro.lumen.us.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.lumen.feign.patient.PatientLookupResult;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.us.dto.RegistrationCancelRequest;
import org.birdbro.lumen.us.dto.RegistrationCreateRequest;
import org.birdbro.lumen.us.dto.RegistrationCreateResult;
import org.birdbro.lumen.us.dto.RegistrationOrderPageQuery;
import org.birdbro.lumen.us.dto.RegistrationOrderView;
import org.birdbro.lumen.us.service.RegistrationOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The US registration HTTP surface (us-station-registration-sev task 3.2, design D6;
 * specs/us-station "Registration Surface" + "Patient Registry Adapter"), the {@code
 * /api/v1/us} registration family - a THIN face over the kernel services: create rides
 * the kernel registration entry (identity from the patient-registry port, the accession
 * issued INSIDE the creation path through the number-issuer port, the ext block riding
 * the kernel {@code RegistrationHook} seam); query/登记/取消 ride the kernel mapper and
 * the state machine (the status column's single writer - its refusals surface verbatim).
 *
 * <p>Perms: every action sits behind the {@code us:order:*} family the us ledger V3
 * seeded ({@code us:order:list} the page, {@code us:order:create} the 新建 button + the
 * resolve passthrough, {@code us:order:update} the 登记 button, {@code us:order:cancel}
 * the 取消 button) via the framework security chain's method pins (the
 * {@code DictDetailController} button-perm precedent); tokenless requests answer the
 * entry point's "203" and an authenticated perm-less caller the A0301 deny envelope.
 * Errors ride the house {@code GlobalExceptionHandler} codes; patient data ops stay
 * server-side through the adapter (no {@code patient:*} perms client-side, the
 * coordination ruling).</p>
 *
 * @author birdbro
 */
@Api(tags = "US 登记台")
@RestController
@RequestMapping("/api/v1/us")
public class RegistrationController {

    private final RegistrationOrderService registrationOrderService;

    public RegistrationController(RegistrationOrderService registrationOrderService) {
        this.registrationOrderService = registrationOrderService;
    }

    /**
     * The LOOKUP-ONLY resolve passthrough (specs/us-station "US-side resolve passthrough",
     * gated {@code us:order:create}): demographics in, exact identity | candidate list |
     * empty out, NO side effect - order creation itself posts the identity-or-demographics
     * and resolves server-side through the adapter.
     */
    @ApiOperation("患者解析透传（仅查询：精确/候选/无，无建档副作用）")
    @PreAuthorize("hasAuthority('us:order:create')")
    @PostMapping("/patients/resolve")
    public PatientLookupResult resolve(@RequestBody PatientResolveOrRegisterRequest query) {
        return registrationOrderService.resolveLookup(query);
    }

    /** Creates one BOOKED order (specs/us-station "Create a booked order"). */
    @ApiOperation("新建预约登记单（发检查号，携 US 扩展块）")
    @PreAuthorize("hasAuthority('us:order:create')")
    @PostMapping("/orders")
    public RegistrationCreateResult create(@RequestBody RegistrationCreateRequest request) {
        return registrationOrderService.create(request);
    }

    /** Pages the orders with the US extension fields (specs/us-station "Order List"). */
    @ApiOperation("登记单分页查询（携 US 扩展字段；拍板 03-B 宽表条件）")
    @PreAuthorize("hasAuthority('us:order:list')")
    @GetMapping("/orders")
    public IPage<RegistrationOrderView> page(RegistrationOrderPageQuery query) {
        return registrationOrderService.page(query);
    }

    /** The status-chip meter (us-registration-redesign-api design D5 - same filters
     *  minus status, zero-filled across all six states). */
    @ApiOperation("状态计数（EIS ReportStatusCheckbox 语义：同一过滤集减状态条件，六态零填充）")
    @PreAuthorize("hasAuthority('us:order:list')")
    @GetMapping("/orders/status-counts")
    public java.util.Map<String, Long> statusCounts(RegistrationOrderPageQuery query) {
        return registrationOrderService.statusCounts(query);
    }

    /** Fires the 登记 transition (specs/us-station "Illegal transition is refused verbatim"). */
    @ApiOperation("登记（签到：BOOKED -> REGISTERED）")
    @PreAuthorize("hasAuthority('us:order:update')")
    @PostMapping("/orders/{id}/register")
    public ExamOrderStatus register(@PathVariable Integer id) {
        return registrationOrderService.register(id);
    }

    /** Cancels a live order with a reason (specs/us-station "Cancel carries a reason"). */
    @ApiOperation("取消登记单（分支按阶段：BOOKED->BOOKING_CANCELLED，否则->CANCELLED）")
    @PreAuthorize("hasAuthority('us:order:cancel')")
    @PostMapping("/orders/{id}/cancel")
    public ExamOrderStatus cancel(@PathVariable Integer id,
            @RequestBody(required = false) RegistrationCancelRequest request) {
        return registrationOrderService.cancel(id, request == null ? null : request.getReason());
    }
}

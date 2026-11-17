package org.birdbro.lumen.patient.controller;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.patient.dto.PatientNumberRuleCreateRequest;
import org.birdbro.lumen.patient.dto.PatientNumberRulePageQuery;
import org.birdbro.lumen.patient.dto.PatientNumberRuleUpdateRequest;
import org.birdbro.lumen.patient.entity.PatientNumberRule;
import org.birdbro.lumen.patient.service.PatientNumberRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Number-rule management endpoints (patient-domain tasks 4.1/4.2; spec delta
 * patient-domain "Number Rule Management API"), the
 * {@code /api/v1/patient/patient-number-rules} family under the
 * {@code patient:rule:*} permission family. Class gate + button-perm pins follow the
 * house {@code OrgRoomController}/{@code ExamItemController} shape exactly (the
 * coordinator-pinned decision 3): the class gate is the list perm (page-level
 * server-side enforcement), the three mutating methods pin their button perms, and a
 * denial answers the {@code A0301} envelope over the HTTP-200 transport (the copied
 * {@code AuthDenyAdvice}); a tokenless request stays the entry point's "203".
 *
 * @author birdbro
 */
@Api(tags = "号码规则")
// Page-level server-side gate (server-side-perm-enforcement shape).
@PreAuthorize("hasAuthority('patient:rule:list')")
@RestController
@RequestMapping("/api/v1/patient/patient-number-rules")
public class PatientNumberRuleController {

    private final PatientNumberRuleService patientNumberRuleService;

    public PatientNumberRuleController(PatientNumberRuleService patientNumberRuleService) {
        this.patientNumberRuleService = patientNumberRuleService;
    }

    /** Creates a rule (active-rule conflict A0714 service-side). */
    @Log(title = "号码规则", type = "新增")
    @ApiOperation("创建号码规则")
    @PreAuthorize("hasAuthority('patient:rule:create')") // button-perm pin
    @PostMapping
    public Integer create(@Valid @RequestBody PatientNumberRuleCreateRequest request) {
        return patientNumberRuleService.create(request);
    }

    /** Full-surface update (serial_cur is runtime state - no CRUD path reaches it). */
    @Log(title = "号码规则", type = "修改")
    @ApiOperation("号码规则更新（激活冲突 A0714）")
    @PreAuthorize("hasAuthority('patient:rule:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody PatientNumberRuleUpdateRequest request) {
        return patientNumberRuleService.update(id, request);
    }

    /** Logic delete; the ACTIVE rule rejects A0400 (先停用再删除). */
    @Log(title = "号码规则", type = "删除")
    @ApiOperation("删除号码规则（启用规则拒绝）")
    @PreAuthorize("hasAuthority('patient:rule:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return patientNumberRuleService.delete(id);
    }

    /** Paged live rule table (the 号码规则 console page's backing list). */
    @ApiOperation("号码规则分页列表")
    @GetMapping
    public IPage<PatientNumberRule> page(PatientNumberRulePageQuery query) {
        return patientNumberRuleService.page(query);
    }
}

package org.birdbro.lumen.patient.controller;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;
import org.birdbro.common.utils.SecurityUtils;

import org.birdbro.lumen.patient.dto.PatientArchivePageQuery;
import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.dto.PatientArchiveUpdateRequest;
import org.birdbro.lumen.patient.dto.PatientMergeRequest;
import org.birdbro.lumen.patient.dto.PatientResolveRequest;
import org.birdbro.lumen.patient.dto.PatientResolveResult;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.service.PatientArchiveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient master archive endpoints (patient-domain tasks 4.1; spec deltas api
 * "Patient Master Archive" / "Identity Resolution Chain" / "Provisional and Merge
 * State Machine"), the {@code /api/v1/patient/patients} family. DELIBERATELY NO
 * permission annotations (coordinator decision 4): the archive surface is
 * AUTHENTICATION-ONLY in v1 (the auth-rbac b4 precedent - authority gating is deferred
 * until menu nodes exist, so no archive perm strings are invented and none are seeded);
 * the framework chain still enforces authentication - a tokenless request answers the
 * entry point's "203" envelope, ANY authenticated user passes.
 *
 * <p>Envelope mechanics are the copied machinery: {@code GlobalResponseAdvice} wraps
 * the plain returns in the HTTP-200 envelope and {@code AuthDenyAdvice}/
 * {@code GlobalExceptionHandler} carry the A/B/C codes. The issuer org (spec: "the
 * archive SHALL carry the issuer organization id") rides the JWT principal - the b4
 * amendment forbids a client-supplied oid, so {@link #create} extracts
 * {@code UserSign.entry.oid} null-safely (null = "filled when known", the V1 column
 * comment) and passes it through the ADDITIVE service overload
 * {@link PatientArchiveService#create(PatientArchiveSaveRequest, Integer)}; the
 * one-arg service method is untouched (the house never-modify rule).</p>
 *
 * @author birdbro
 */
@Api(tags = "患者档案")
@RestController
@RequestMapping("/api/v1/patient/patients")
public class PatientController {

    private final PatientArchiveService patientArchiveService;

    public PatientController(PatientArchiveService patientArchiveService) {
        this.patientArchiveService = patientArchiveService;
    }

    /** Runs the identity resolve chain (exact -> card -> soft -> create-new). */
    @ApiOperation("身份解析链（精确 -> 就诊卡 -> 软候选 -> 建档）")
    @PostMapping("/resolve")
    public PatientResolveResult resolve(@RequestBody PatientResolveRequest request) {
        return patientArchiveService.resolve(request);
    }

    /** Creates an archive (pat_id + pinyin server-side; oid from the principal). */
    @Log(title = "患者档案", type = "新增")
    @ApiOperation("创建患者档案（发号 + 拼音服务端生成）")
    @PostMapping
    public PatientMaster create(@Valid @RequestBody PatientArchiveSaveRequest request) {
        return patientArchiveService.create(request, principalOid());
    }

    /** Full demographics update (merged rows are immutable history - service guard). */
    @Log(title = "患者档案", type = "修改")
    @ApiOperation("档案人口学信息更新")
    @PutMapping("/{id}")
    public PatientMaster update(@PathVariable Integer id,
            @Valid @RequestBody PatientArchiveUpdateRequest request) {
        return patientArchiveService.update(id, request);
    }

    /**
     * One archive REGARDLESS of merge state ({@code merged_into} tells the caller);
     * a missing id answers {@code data:null} (the house GET-by-id read precedent).
     */
    @ApiOperation("档案详情（合并行仍可查）")
    @GetMapping("/{id}")
    public PatientMaster getById(@PathVariable Integer id) {
        return patientArchiveService.getById(id);
    }

    /** Paged live-unmerged archive list (multi-number + name/pinyin search). */
    @ApiOperation("档案分页列表（姓名/拼音/号码过滤）")
    @GetMapping
    public IPage<PatientMaster> page(PatientArchivePageQuery query) {
        return patientArchiveService.page(query);
    }

    /** The minimal merge; an already-merged row replays idempotently (spec). */
    @Log(title = "患者档案", type = "修改")
    @ApiOperation("最小合并（幂等：已合并行返回既有幸存者）")
    @PostMapping("/{id}/resolve-or-merge")
    public PatientMaster resolveOrMerge(@PathVariable Integer id,
            @Valid @RequestBody PatientMergeRequest request) {
        return patientArchiveService.resolveOrMerge(id, request.getSurvivorId(),
                request.getReason());
    }

    /**
     * Null-safe issuer-org extraction from the JWT principal (b4 amendment 1): the
     * entry/oid may be absent (old sessions, anonymous-adjacent shapes) - the archive
     * then carries NULL ("filled when known", the V1 column comment). Never taken from
     * the request body (a client-supplied issuer is spoofable).
     */
    private Integer principalOid() {
        try {
            if (SecurityUtils.getAuthentication() == null
                    || !(SecurityUtils.getAuthentication().getPrincipal() instanceof UserLogin)) {
                return null;
            }
            UserLogin login = (UserLogin) SecurityUtils.getAuthentication().getPrincipal();
            UserSign user = login.getUser();
            if (user == null || user.getEntry() == null) {
                return null;
            }
            return user.getEntry().getOid();
        } catch (Exception e) {
            return null;
        }
    }
}

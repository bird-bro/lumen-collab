package org.birdbro.lumen.patient.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import cn.hutool.core.bean.BeanUtil;

import org.birdbro.lumen.api.patient.PatientIdentityResponse;
import org.birdbro.lumen.api.patient.PatientLookupResult;
import org.birdbro.lumen.api.patient.PatientOutboxEntry;
import org.birdbro.lumen.api.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.api.patient.PatientResolveOrRegisterResult;
import org.birdbro.lumen.patient.dto.PatientResolveRequest;
import org.birdbro.lumen.patient.dto.PatientResolveResult;
import org.birdbro.lumen.patient.entity.PatientEventOutbox;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.service.PatientArchiveService;
import org.birdbro.lumen.patient.service.PatientEventOutboxDeliveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The patient domain's INTERNAL service surface (us-station-registration-sev task 2.2,
 * design D4; spec patient-domain "Internal Service Surface"), the
 * {@code /api/v1/patient/internal/**} family. Deliberately a SEPARATE controller from
 * the user-facing {@link PatientController} - it carries NO permission annotations and
 * NO user-token dependency (there is no user behind a service call); the path sits on
 * the permit-all list, guarded by the {@code X-Internal-Key} interceptor (patient
 * config/InternalKeyInterceptor) instead. The envelope wraps the plain returns via the
 * framework advice - exactly the shape the {@code PatientApi} Feign contract decodes.
 *
 * <p>Composite discipline: {@link #resolveOrRegister} ORCHESTRATES ONLY the existing
 * {@link PatientArchiveService#resolve(PatientResolveRequest)} chain (its own
 * {@code @Transactional} opens the ONE transaction the exact/soft lookups and the
 * fallback archive creation share) - no resolution logic is rewritten, the creation
 * path's guards ({@code A0410} typeless identity, {@code A0715} conflict semantics)
 * surface verbatim. The contract DTO mapping is 1:1 (the field names match the resolve
 * form; the house {@code BeanUtil.copyProperties(src, dest, true)} idiom).</p>
 *
 * <p>Outbox discipline: {@link #pullOutbox} hands out PENDING entries in id order,
 * limit-capped (no claim - entries stay visible until acked); {@link #ackOutbox} flips
 * one row to CONSUMED and keeps it for audit, idempotently. The payload crosses the
 * contract as the RAW jsonb document string (Jackson re-serialization of the entity's
 * map - the consumer parses it with its own mapper).</p>
 *
 * @author birdbro
 */
@Api(tags = "患者内部服务（内部钥匙）")
@RestController
@RequestMapping("/api/v1/patient/internal")
public class InternalPatientController {

    private final PatientArchiveService patientArchiveService;
    private final PatientEventOutboxDeliveryService outboxDeliveryService;
    private final ObjectMapper objectMapper;

    public InternalPatientController(PatientArchiveService patientArchiveService,
            PatientEventOutboxDeliveryService outboxDeliveryService,
            ObjectMapper objectMapper) {
        this.patientArchiveService = patientArchiveService;
        this.outboxDeliveryService = outboxDeliveryService;
        this.objectMapper = objectMapper;
    }

    /**
     * The composite: resolves an existing archive through the EXISTING chain, or
     * registers a new one (PROVISIONAL placeholders included) - one transaction,
     * {@code A0715}/{@code A0410} unchanged (the identity race surfaces A0715 through
     * the service's additive {@code resolveOrRegister} composite).
     */
    @ApiOperation("解析或建档（内部服务专用，X-Internal-Key 门控）")
    @PostMapping("/patients/resolve-or-register")
    public PatientResolveOrRegisterResult resolveOrRegister(
            @RequestBody PatientResolveOrRegisterRequest request) {
        PatientResolveRequest resolve = new PatientResolveRequest();
        BeanUtil.copyProperties(request, resolve, true);
        PatientResolveResult result = patientArchiveService.resolveOrRegister(resolve);
        return toContractResult(result);
    }

    /**
     * The LOOKUP-ONLY resolve (us-station-registration-sev task 3.1, the B2 review gap
     * closure): the resolution chain's lookups with NO create fallback - exact |
     * candidates | NONE, never a side effect. ORCHESTRATES ONLY the additive
     * {@link PatientArchiveService#lookup(PatientResolveRequest)} (the same private
     * helpers the public resolve chain runs) - the request body is the same resolve
     * form (its {@code provisional} flag is ignored).
     */
    @ApiOperation("仅查询解析（内部服务专用，X-Internal-Key 门控，无建档副作用）")
    @PostMapping("/patients/lookup")
    public PatientLookupResult lookup(@RequestBody PatientResolveOrRegisterRequest request) {
        PatientResolveRequest resolve = new PatientResolveRequest();
        BeanUtil.copyProperties(request, resolve, true);
        PatientResolveResult result = patientArchiveService.lookup(resolve);
        return PatientLookupResult.builder()
                .result(result.getResult())
                .identity(toIdentity(result.getPatient()))
                .candidates(result.getCandidates() == null ? null
                        : result.getCandidates().stream()
                                .map(this::toIdentity)
                                .collect(Collectors.toList()))
                .build();
    }

    /** PENDING outbox batch in id order, limit-capped (no claim - re-pull until ack). */
    @ApiOperation("拉取 PENDING outbox 批次（内部服务专用，X-Internal-Key 门控）")
    @GetMapping("/events/outbox")
    public List<PatientOutboxEntry> pullOutbox(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return outboxDeliveryService.pullPending(limit).stream()
                .map(this::toContractEntry)
                .collect(Collectors.toList());
    }

    /** Flips one pulled entry to CONSUMED (row kept for audit; idempotent no-op). */
    @ApiOperation("确认消费一条 outbox 事件（幂等，行保留审计）")
    @PostMapping("/events/outbox/{id}/ack")
    public Boolean ackOutbox(@PathVariable("id") Integer id) {
        outboxDeliveryService.ack(id);
        return Boolean.TRUE;
    }

    // ---------------------------------------------------------------- mapping

    /** The chain result -> the contract result (1:1 shape, entity fields decoupled). */
    private PatientResolveOrRegisterResult toContractResult(PatientResolveResult result) {
        return PatientResolveOrRegisterResult.builder()
                .result(result.getResult())
                .identity(toIdentity(result.getPatient()))
                .candidates(result.getCandidates() == null ? null
                        : result.getCandidates().stream()
                                .map(this::toIdentity)
                                .collect(Collectors.toList()))
                .build();
    }

    /** The archive -> the contract identity (patUid/patId/state + display fields). */
    private PatientIdentityResponse toIdentity(PatientMaster master) {
        if (master == null) {
            return null;
        }
        return PatientIdentityResponse.builder()
                .patUid(master.getId())
                .patId(master.getPatId())
                .state(master.getArchiveStatus())
                .idType(master.getIdType())
                .idNo(master.getIdNo())
                .name(master.getName())
                .gender(master.getGender())
                .birthDate(master.getBirthDate())
                .phone(master.getPhone())
                .build();
    }

    /** The outbox row -> the contract entry (payload as the raw jsonb string). */
    private PatientOutboxEntry toContractEntry(PatientEventOutbox row) {
        return PatientOutboxEntry.builder()
                .id(row.getId())
                .eventType(row.getEventType())
                .payload(toRawJson(row.getPayload()))
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    /** Serializes the jsonb payload map to its raw document string. */
    private String toRawJson(java.util.Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // a Map<String, Object> payload always serializes - never a caller-facing case
            throw new IllegalStateException("outbox payload serialization failed", e);
        }
    }
}

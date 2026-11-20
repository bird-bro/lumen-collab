package org.birdbro.lumen.api.patient;

import java.util.List;

import org.birdbro.common.core.R;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The patient-domain INTERNAL Feign contract (us-station-registration-sev tasks 2.1,
 * design D3/D4): the machine-facing resolve-or-register composite + the transactional
 * outbox pull/ack pair the consuming services' registration adapters and snapshot
 * pollers call. NEVER behind user perms - every endpoint sits on the patient service's
 * {@code /api/v1/patient/internal/**} family, guarded by the {@code X-Internal-Key}
 * interceptor (the system {@code /issue} precedent); the consumer's Feign
 * RequestInterceptor adds the key from the same gitignored config the patient side
 * checks. Consumers enable it with {@code @EnableFeignClients} and configure the DIRECT
 * target URL per environment ({@code lumen.api.patient.url} - no discovery, the
 * 2026-11-03 local-config ruling).
 *
 * <p>Composite semantics: the endpoint atomically runs the patient domain's EXISTING
 * resolution chain and falls back to archive creation (PROVISIONAL placeholders
 * included, {@code A0715} conflict semantics unchanged) - the consuming adapter refuses
 * an incomplete result (missing {@code patUid}) before any order row is written.</p>
 *
 * <p>Outbox semantics: {@code pullOutbox} hands out PENDING entries in id order,
 * limit-capped; pulled entries stay visible to re-pull until acked (exactly once PER
 * ACK - a failed dispatch that skips the ack leaves the entry PENDING for replay);
 * {@code ackOutbox} flips the row to CONSUMED and keeps it for audit, idempotently.</p>
 *
 * <p>Lookup semantics (task 3.1): {@code lookup} is the LOOKUP-ONLY resolve - exact |
 * candidates | NONE, never a creation (the UI passthrough's no-side-effect read).</p>
 *
 * <p>Fail-fast contract: the fallback THROWS (C0001) - a fabricated identity or a
 * fabricated consumption confirmation is FORBIDDEN.</p>
 *
 * @author birdbro
 */
@FeignClient(name = "lumen-patient-registry", url = "${lumen.api.patient.url:}",
        fallback = PatientApiFallback.class)
public interface PatientApi {

    /**
     * Resolves an existing patient archive or registers a new one (placeholder
     * archives included) in ONE patient-side transaction.
     *
     * @param request the demographics + natural keys the resolution chain walks
     * @return the envelope carrying {@code data} = the chain outcome ({@code result} +
     *         the identity on MATCHED/CREATED, the candidate list on CANDIDATES)
     */
    @PostMapping("/api/v1/patient/internal/patients/resolve-or-register")
    R<PatientResolveOrRegisterResult> resolveOrRegister(
            @RequestBody PatientResolveOrRegisterRequest request);

    /**
     * LOOKUP-ONLY resolve (us-station-registration-sev task 3.1 - the B2 review gap
     * closure: the consuming UI's resolve passthrough must NOT register). Runs the
     * resolution chain's LOOKUP steps only (exact &rarr; card &rarr; soft) - an exact
     * identity, a candidate list, or an EMPTY result returns and NOTHING is created
     * (no side effect, ever). The request body is the SAME resolve form (its
     * {@code provisional} flag is ignored - no fallback archive exists on this path).
     *
     * @param request the resolve keys the chain walks
     * @return the envelope carrying {@code data} = the lookup outcome ({@code result} +
     *         the identity on MATCHED, the candidate list on CANDIDATES, nothing on NONE)
     */
    @PostMapping("/api/v1/patient/internal/patients/lookup")
    R<PatientLookupResult> lookup(@RequestBody PatientResolveOrRegisterRequest request);

    /**
     * Pulls a batch of PENDING outbox entries in id order (entries remain visible to
     * re-pull until acked).
     *
     * @param limit the batch cap (the provider normalizes non-positive values to its
     *        default batch size)
     * @return the envelope carrying {@code data} = the PENDING entry list
     */
    @GetMapping("/api/v1/patient/internal/events/outbox")
    R<List<PatientOutboxEntry>> pullOutbox(@RequestParam("limit") Integer limit);

    /**
     * Marks one pulled entry CONSUMED (the row is kept for audit). Idempotent: acking a
     * non-PENDING (or missing) row is a no-op success.
     *
     * @param id the outbox row id
     * @return the envelope carrying {@code data} = {@code true} on success
     */
    @PostMapping("/api/v1/patient/internal/events/outbox/{id}/ack")
    R<Boolean> ackOutbox(@PathVariable("id") Integer id);
}

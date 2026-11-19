package org.birdbro.lumen.core.service;

import java.util.List;
import java.util.Map;

import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;

/**
 * The HIS two-phase intake service (lumen-core-registration-kernel task 6.1; spec
 * exam-workflow-kernel "HIS Two-Phase Intake"): phase one caches the inbound apply
 * (raw source + mapped target; unmatched exam items dropped at cache time), phase two
 * is the MANUAL confirm that creates the formal order - there is NO server-side
 * auto-commit. Corrections rewrite the target only while the row is cached. The HL7v2
 * protocol layer itself belongs to lumen-integration; this service is the
 * protocol-neutral two-phase contract.
 *
 * @author birdbro
 */
public interface HisTwoPhaseIntakeService {

    /**
     * Phase one - caches an inbound apply. Entries of {@code mappedItems} WITHOUT a
     * catalog reference (null {@code itemId}) are structurally dropped here (the
     * unmatched-item drop rule); the catalog lookup itself is the adapter's concern.
     *
     * @param hisId       the HIS-side document id
     * @param applyNo     the application number (nullable)
     * @param source      the raw inbound payload
     * @param mappedItems the adapter's dictionary-mapped items (matched carry itemId,
     *                    unmatched carry null and are dropped)
     * @param patientHint the raw HIS patient fields (confirm resolves the real identity)
     * @return the cache row id
     */
    Integer cacheApply(String hisId, String applyNo, Map<String, Object> source,
            List<ExamItemRef> mappedItems, Map<String, Object> patientHint);

    /**
     * Corrects a not-yet-committed cache row: re-maps the target (the same drop rule
     * applies). A COMMITTED row refuses the correction.
     *
     * @param cacheId     the cache row id
     * @param mappedItems the corrected mapped items
     * @param patientHint the corrected patient hint (nullable = keep the old one)
     * @throws org.birdbro.common.exception.BusinessException when the row is COMMITTED
     */
    void correctApply(Integer cacheId, List<ExamItemRef> mappedItems,
            Map<String, Object> patientHint);

    /**
     * Phase two - the MANUAL confirm: resolves the patient identity through the
     * patient-registry port, creates the formal order entering REGISTERED (the HIS
     * confirm entry state), carries applyNo/hisId and the cached exam items onto it,
     * and marks the cache row COMMITTED - all inside ONE transaction.
     *
     * @param cacheId        the cache row id
     * @param patientRequest the workstation's patient selection for the port
     * @return the created order id
     * @throws org.birdbro.common.exception.BusinessException when the row is missing or
     *         already COMMITTED
     */
    Integer confirmApply(Integer cacheId, PatientRegistrationRequest patientRequest);
}

package org.birdbro.lumen.core.patient;

/**
 * The patient-registry port seam (lumen-core-registration-kernel task 5.1, design D6;
 * spec exam-workflow-kernel "Patient Registry Port"): the ONLY way the kernel obtains
 * patient identity. The kernel never issues {@code pat_id} and never touches the
 * patient schema - consuming services ADAPT this port to the patient service (HTTP/
 * Feign in their own module); the kernel order-creation path takes identity ONLY from
 * a port result and refuses creation without one.
 *
 * <p>Adapter expectations (documented contract, not enforced here): the adapter
 * implements resolve-or-register semantics against the patient domain's resolution
 * chain (id_no &rarr; card_no &rarr; HIS mapping &rarr; soft-match &rarr; new, patient
 * decision #2) and builds the identity with a NOT NULL {@code patUid} - an incomplete
 * result is refused kernel-side ({@code Order creation consumes the port} / {@code
 * Incomplete port result refused}).</p>
 *
 * @author birdbro
 */
public interface PatientRegistryPort {

    /**
     * Resolves an existing patient archive or registers a new one (placeholder
     * archives included), returning the identity the order links to.
     *
     * @param request the exam-side demographics + natural keys
     * @return the identity with a NOT NULL {@code patUid}
     */
    PatientIdentity resolveOrRegister(PatientRegistrationRequest request);
}

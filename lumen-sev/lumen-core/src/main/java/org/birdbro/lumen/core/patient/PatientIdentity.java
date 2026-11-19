package org.birdbro.lumen.core.patient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The patient-registry port's RESULT (lumen-core-registration-kernel task 5.1, design
 * D6): the resolved-or-registered identity the exam side links to. {@code patUid} is
 * the NOT NULL order-linkage key (patient decision #3); {@code patId} is the imaging
 * number issued by the patient domain (decision #1 - the kernel NEVER issues it, spec
 * "Exam-Side Numbering"); {@code resolved} distinguishes an existing archive from a
 * freshly registered one (the 占位患者 placeholder case included); {@code snapshot}
 * carries the exam-time demographics the order's jsonb snapshot takes.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientIdentity {

    /** The patient domain's pat_uid - the order linkage key, required NOT NULL. */
    private Integer patUid;

    /** 影像号 pat_id (patient-domain-issued, carried for display). */
    private String patId;

    /** true = an existing archive was resolved; false = a new/placeholder archive. */
    private boolean resolved;

    /** The exam-time demographics snapshot payload. */
    private org.birdbro.lumen.core.entity.PatientSnapshot snapshot;
}

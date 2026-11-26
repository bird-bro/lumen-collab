package org.birdbro.lumen.core.service;

import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.patient.PatientIdentity;

/**
 * The port-driven registration entry (lumen-core-registration-kernel task 5.1; spec
 * exam-workflow-kernel "Patient Registry Port" - Order creation consumes the port):
 * kernel order creation takes patient identity ONLY from a patient-registry port
 * result, links {@code patient_uid} from it, and refuses without one. The 检查号
 * accession is issued here when the caller did not bring one (the exam-side issuer's
 * consumer). The entry state (REGISTERED for every path - 预约=预登记, user ruling 2026-11-24) stays
 * the caller's choice - the aggregate service refuses a stateless document.
 *
 * @author birdbro
 */
public interface ExamOrderRegistrationService {

    /**
     * Registers one order against a port-resolved identity: validates the identity,
     * links the patient reference (and the snapshot payload when the caller did not
     * bring one), issues the accession number when absent, and persists the aggregate
     * through {@link ExamOrderAggregateService}.
     *
     * @param identity  the patient-registry port result (patUid required)
     * @param order     the order document (entry status required; accession optional)
     * @param execution the execution member
     * @param report    the report member
     * @return the persisted order id
     * @throws org.birdbro.common.exception.BusinessException when the identity is
     *         missing or incomplete (no patUid) - nothing persists
     */
    Integer registerOrder(PatientIdentity identity, ExamOrder order,
            ExamExecution execution, ExamReport report);
}

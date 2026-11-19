package org.birdbro.lumen.core.service;

import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.PatientSnapshot;

/**
 * The exam-order aggregate service (lumen-core-registration-kernel task 2.2; spec
 * exam-workflow-kernel "Exam-Order Aggregate"): the single transactional writer of the
 * four-member aggregate - the order document plus its 1:1 execution and report
 * members, with the patient reference guarded NOT NULL. The snapshot refresh is
 * report-state-guarded (medical documents never change silently).
 *
 * <p>The status column is NOT written here - task 3.1's state-machine transition
 * service is its single writer. Later batches extend this family: the patient-port
 * order-creation entry (task 5.1), the HIS two-phase confirm (task 6.1), and the
 * ordered registration hooks (task 7.1) all ride {@code insertWithMembers}.</p>
 *
 * @author birdbro
 */
public interface ExamOrderAggregateService {

    /**
     * Saves the aggregate in ONE transaction: validates the patient reference, inserts
     * the order, then links and inserts the execution and report members.
     *
     * @param order     the order document (patientUid required)
     * @param execution the execution member (orderId assigned here)
     * @param report    the report member (orderId assigned here; DRAFTING default)
     * @return the persisted order id
     * @throws org.birdbro.common.exception.BusinessException when the patient reference
     *         is missing (A0400) - nothing persists
     */
    Integer insertWithMembers(ExamOrder order, ExamExecution execution, ExamReport report);

    /**
     * Refreshes the jsonb patient snapshot with the delivered patient data. REFUSED
     * while the order's report is ISSUED (已出) - a medical document never changes
     * silently (the consumer decides the patient.changed follow-up, e.g. a manual
     * re-issue prompt).
     *
     * @param orderId  the order id
     * @param snapshot the delivered patient snapshot
     * @throws org.birdbro.common.exception.BusinessException when the report is ISSUED
     */
    void refreshPatientSnapshot(Integer orderId, PatientSnapshot snapshot);
}

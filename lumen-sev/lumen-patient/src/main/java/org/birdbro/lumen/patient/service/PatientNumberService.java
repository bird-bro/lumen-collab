package org.birdbro.lumen.patient.service;

/**
 * Number issuance (patient-domain task 2.2, design D1): hands out platform numbers
 * (the {@code pat_id} family today) from the {@code patient_number_rule} counter row.
 *
 * <p>Transaction semantics (coordinator-pinned decision 1): {@link #issue(String)}
 * runs with {@code Propagation.REQUIRES_NEW} - the counter increment COMMITS
 * INDEPENDENTLY of the caller's transaction. An archive creation that fails after
 * issuance therefore BURNS the number: the caller's rollback cannot revert
 * {@code serial_cur}, and the number is never reissued (spec: "numbers are never
 * reused"). The {@code pat_id} WRITE onto the archive row stays in the caller's
 * transaction (design D1's atomic write intent).</p>
 *
 * <p>The increment happens under a {@code SELECT ... FOR UPDATE} row lock on the
 * active rule of the requested {@code rule_type}; the serial restarts at 1 whenever
 * the formatted stamp token differs from {@code stamp_value} (day reset, decision 2),
 * and the number composes as prefix + stamp + zero-padded serial + suffix
 * (decision 3).</p>
 *
 * @author birdbro
 */
public interface PatientNumberService {

    /**
     * Issues the next number of the given rule family; the counter increment commits
     * in its own REQUIRES_NEW transaction (burned-serial semantics - see the class
     * javadoc).
     *
     * @param ruleType the number rule family ({@code pat_id} today)
     * @return the composed number (prefix + stamp token when stamped + zero-padded
     *         serial + suffix)
     * @throws org.birdbro.common.exception.BusinessException with code {@code B0001}
     *         when the type has no active rule (system state - an admin fixes the
     *         config), or code {@code A0400} when the active rule's {@code stamp} is
     *         not a valid Java date pattern
     */
    String issue(String ruleType);
}

package org.birdbro.lumen.patient.service;

/**
 * Number issuance via PLATFORM delegation (number-rule-unification-sev task 3.1; the
 * 2026-11-18 unification ruling): the patient service issues numbers through the
 * platform's NumberApi Feign contract - the row-lock burned-serial counter lives in
 * the SYSTEM schema (number_rule) behind the system issuance API.
 *
 * <p>Transaction semantics preserved through the remote hop: the platform commits the
 * increment independently of the caller's transaction, so an archive creation that
 * fails after issuance BURNS the number (never reissued). A platform unreachability
 * throws the C0001 fail-fast fallback - the caller's operation refuses and NO number
 * is fabricated.</p>
 *
 * @author birdbro
 */
public interface PatientNumberService {

    /**
     * Issues the next number of the given rule family; the counter increment commits
     * in its own REQUIRES_NEW transaction (burned-serial semantics - see the class
     * javadoc).
     *
     * @param ruleType the number rule family (dict 编号类型 1028 token - 影像号 today)
     * @return the composed number (prefix + stamp token when stamped + zero-padded
     *         serial + suffix)
     * @throws org.birdbro.common.exception.BusinessException with code {@code B0001}
     *         when the type has no active rule (system state - an admin fixes the
     *         config), or code {@code A0400} when the active rule's {@code stamp} is
     *         not a valid Java date pattern
     */
    String issue(String ruleType);
}

package org.birdbro.lumen.manage.service;

/**
 * The platform number-issuer service (number-rule-unification-sev task 2.1, design D3;
 * spec number-rule "Platform Number Issuance"): rule-driven burned-serial issuance for
 * EVERY number family in the platform directory - the row lock + daily-reset stamp
 * serial live HERE (relocated from the patient domain by the 2026-11-18 unification
 * ruling; the kernel consumes the same machinery behind its NumberIssuerPort).
 *
 * @author birdbro
 */
public interface NumberIssuerService {

    /**
     * Issues one number for the given rule family: {@code prefix + [stamp token] +
     * zero-padded serial + suffix}. The row-locked increment commits in its own
     * transaction (REQUIRES_NEW, the burned-serial semantic) and the serial resets to
     * 1 when a stamped rule's day token rolls over.
     *
     * @param ruleType the number family
     * @return the issued number
     * @throws org.birdbro.common.exception.BusinessException with B0001 when the family
     *         has no active rule (system state - an admin fixes the config), A0400
     *         when the stamp pattern is invalid
     */
    String issue(String ruleType);
}

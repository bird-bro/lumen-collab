package org.birdbro.lumen.core.number;

/**
 * The number-issuer port seam (lumen-core-registration-kernel task 4.1, design D5
 * after the 2026-11-18 rewind; spec exam-workflow-kernel "Number Issuer Port"): the
 * ONLY way the kernel obtains numbers. The row-lock rule machinery (the rule table,
 * the REQUIRES_NEW burned-serial increment, the daily-reset serial) is PLATFORM-owned —
 * it lives in the system schema behind the number-rule service
 * (`number-rule-unification`); consuming services adapt this port to it (Feign in
 * their own module). The kernel never stores rules and never mints numbers itself.
 *
 * <p>Fail-fast contract: a port failure REFUSES the calling operation — no fallback
 * ever fabricates a number, because numbers are never reused (the platform commits the
 * serial increment independently; a caller rollback burns the number, the
 * patient-domain burned-serial semantic preserved remotely).</p>
 *
 * @author birdbro
 */
public interface NumberIssuerPort {

    /**
     * Issues one number for the given rule family (the 登记号 and 预约号 families
     * among them - the dict 编号类型 1028 tokens; family configuration lives
     * platform-side).
     *
     * @param ruleType the rule family
     * @return the issued number
     */
    String issue(String ruleType);
}

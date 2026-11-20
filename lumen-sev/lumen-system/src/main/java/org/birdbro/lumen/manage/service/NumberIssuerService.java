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
     * Issues one number for the given rule family's GLOBAL pool (modality NULL, V49 -
     * the family's platform-wide shared pool): {@code prefix + [stamp token] +
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

    /**
     * Issues one number for the given rule family and modality scope
     * (number-rule-modality-scope task 1.2, design D3; spec "Modality-Scoped Issuance
     * Resolution"): resolution by specificity - the exact active rule of
     * {@code (ruleType, modality)} first, then the family's global active rule
     * (modality NULL), then refusal. Row locks apply to the resolved row only. A
     * blank/null {@code modality} resolves the global rule only (the single-arg
     * legacy shape). An unknown or unconfigured token is NOT validated - it simply
     * matches no scoped rule and falls back.
     *
     * @param ruleType the number family
     * @param modality the modality scope (dict 模态 1024 value token, e.g. CT);
     *                 blank/null = the family's global pool
     * @return the issued number
     * @throws org.birdbro.common.exception.BusinessException with B0001 when neither a
     *         scoped nor a global active rule exists (the message names the family and,
     *         when present, the modality token), A0400 when the stamp pattern is invalid
     */
    String issue(String ruleType, String modality);
}

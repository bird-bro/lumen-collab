package org.birdbro.lumen.feign.number;

import java.io.Serializable;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The number-issue request body (number-rule-unification-sev task 1.1, design D5):
 * the rule FAMILY to issue from (the platform's dict 编号类型 1028 tokens -
 * 影像号 / 登记号 / 预约号 among them). The platform issuer resolves the family's
 * single active rule row and burns one serial.
 *
 * <p>Additive contract (number-rule-modality-scope design D4): the optional modality
 * scope rides alongside the family — consumers rebuilt against the older jar simply
 * omit the key; {@code NumberApi}'s method signature and the fail-fast fallback are
 * untouched, so the addition is rebuild-safe both ways.</p>
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NumberIssueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The rule family (dict 编号类型 1028 token / future families). */
    @NotBlank
    private String ruleType;

    /**
     * The modality scope (dict 模态 1024 VALUE token, e.g. US; optional - absent or
     * blank resolves the family's GLOBAL pool, V49 number-rule-modality-scope task 2.1).
     */
    private String modality;
}

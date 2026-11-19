package org.birdbro.lumen.api.number;

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
}

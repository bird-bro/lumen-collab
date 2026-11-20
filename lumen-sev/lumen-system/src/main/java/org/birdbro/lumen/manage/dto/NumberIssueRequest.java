package org.birdbro.lumen.manage.dto;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The machine issuance request body (number-rule-unification-sev task 2.2): the OWNER
 * side's local mirror of the {@code NumberApi} contract DTO (lumen-feign) — identical
 * JSON field names, deliberately NOT a shared class: the owning service must not carry
 * the consumer-side Feign machinery on its classpath (the FeignAutoConfiguration
 * classpath trap, hit and fixed during apply). The contract stays single-sourced for
 * CONSUMERS; the owner mirrors the declared surface.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NumberIssueRequest {

    /** The rule family (dict 编号类型 1028 token - 影像号 / 登记号 / 预约号 / future families). */
    @NotBlank
    private String ruleType;

    /**
     * The modality scope (dict 模态 1024 VALUE token, e.g. CT; optional - absent or
     * blank resolves the family's GLOBAL pool, V49 number-rule-modality-scope task 1.2).
     */
    private String modality;
}

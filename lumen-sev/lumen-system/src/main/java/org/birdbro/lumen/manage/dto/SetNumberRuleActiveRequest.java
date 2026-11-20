package org.birdbro.lumen.manage.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Number-rule active-state request body ({@code PUT /api/v1/system/number-rules/{id}/active},
 * the org-status-toggle pattern): a dedicated lifecycle command - the full-surface PUT
 * keeps {@code active} for the drawer save, the list's 启停 switch rides THIS endpoint.
 *
 * @author birdbro
 */
@Data
public class SetNumberRuleActiveRequest {

    @NotNull(message = "active must not be null")
    @Min(value = 0, message = "active must be 0 or 1")
    @Max(value = 1, message = "active must be 0 or 1")
    @ApiModelProperty("1 = activate the rule (one live rule per family and scope), 0 = deactivate")
    private Integer active;

    /**
     * The rule's modality scope (dict-1024 value token, optional payload symmetry,
     * V49). NOT consumed by the toggle guard: the scope is pool identity and
     * IMMUTABLE after create (design D2) - the guard keys on the STORED row's scope.
     */
    @ApiModelProperty("the rule's modality scope token (informational; the guard keys on the stored row's scope)")
    private String modality;
}

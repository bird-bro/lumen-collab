package org.birdbro.lumen.patient.dto;

import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Resolve-or-merge request ({@code POST /api/v1/patient/patients/{id}/resolve-or-merge},
 * patient-domain task 4.1): the absorbed archive is the PATH id, the survivor + audit
 * reason ride the body. Guard semantics live in the service (self-merge A0400,
 * non-RESOLVED survivor A0400, missing target B0031, already-merged idempotent replay).
 *
 * @author birdbro
 */
@Data
public class PatientMergeRequest {

    @ApiModelProperty("幸存档案 id（必须存在、未合并、RESOLVED）")
    @NotNull(message = "survivorId 不能为空")
    private Integer survivorId;

    @ApiModelProperty("合并原因（审计用，可空）")
    private String reason;
}

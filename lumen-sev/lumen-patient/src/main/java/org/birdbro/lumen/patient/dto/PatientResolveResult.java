package org.birdbro.lumen.patient.dto;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.birdbro.lumen.patient.entity.PatientMaster;

/**
 * Identity resolve result (patient-domain task 3.3): {@code result} is
 * {@link #RESULT_MATCHED} (decisive hit, {@code patient} set, {@code candidates}
 * null), {@link #RESULT_CANDIDATES} (soft/ambiguous hits - a candidate LIST, never
 * an error, {@code candidates} set), or {@link #RESULT_CREATED} (nothing matched, a
 * new archive was built, {@code patient} set).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientResolveResult {

    /** Decisive single hit (exact id_no, or exactly one live card_no). */
    public static final String RESULT_MATCHED = "MATCHED";

    /** Ambiguous/soft hits: the caller picks from {@code candidates}. */
    public static final String RESULT_CANDIDATES = "CANDIDATES";

    /** Nothing matched - a new archive was created (PROVISIONAL or RESOLVED). */
    public static final String RESULT_CREATED = "CREATED";

    /**
     * Nothing matched - EMPTY result, NO archive row written (us-station-registration-sev
     * task 3.1: the ADDITIVE lookup-only marker; only the ADDITIVE
     * {@code PatientArchiveService#lookup} chain returns it - the creating resolve chain
     * never does).
     */
    public static final String RESULT_NONE = "NONE";

    @ApiModelProperty("解析结果：MATCHED / CANDIDATES / CREATED")
    private String result;

    @ApiModelProperty("决定命中或新建时的档案")
    private PatientMaster patient;

    @ApiModelProperty("软匹配候选列表（调用方挑选，非错误）")
    private List<PatientMaster> candidates;
}

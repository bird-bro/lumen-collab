package org.birdbro.lumen.feign.patient;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The resolve-or-register composite result (us-station-registration-sev task 2.1,
 * design D3/D4): the contract view of the patient domain's resolution chain outcome.
 * {@code result} is {@link #RESULT_MATCHED} (decisive hit, {@code identity} set),
 * {@link #RESULT_CANDIDATES} (ambiguous soft hits - a candidate LIST, never an error;
 * {@code identity} is NULL and the consumer refuses), or {@link #RESULT_CREATED}
 * (nothing matched - a new archive was built, placeholder included, {@code identity}
 * set). The tokens mirror the patient domain's own resolve vocabulary verbatim.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientResolveOrRegisterResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Decisive single hit (exact id pair, or exactly one live card_no). */
    public static final String RESULT_MATCHED = "MATCHED";

    /** Ambiguous/soft hits: the caller picks from {@code candidates}. */
    public static final String RESULT_CANDIDATES = "CANDIDATES";

    /** Nothing matched - a new archive was created (PROVISIONAL placeholder included). */
    public static final String RESULT_CREATED = "CREATED";

    /** 解析结果：MATCHED / CANDIDATES / CREATED. */
    private String result;

    /** 决定命中或新建时的身份（CANDIDATES 时为 NULL - 消费方拒绝不完整结果）. */
    private PatientIdentityResponse identity;

    /** 软匹配候选列表（调用方挑选，非错误）. */
    private List<PatientIdentityResponse> candidates;
}

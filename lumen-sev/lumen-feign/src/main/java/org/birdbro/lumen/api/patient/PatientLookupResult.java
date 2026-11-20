package org.birdbro.lumen.api.patient;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The LOOKUP-ONLY resolve result (us-station-registration-sev task 3.1 - the B2 review
 * gap closure: the us resolve passthrough must NOT register, so it needs a no-side-effect
 * resolve outcome). {@code result} is {@link #RESULT_MATCHED} (decisive exact/card hit,
 * {@code identity} set), {@link #RESULT_CANDIDATES} (ambiguous soft hits - a candidate
 * LIST, never an error; {@code identity} is NULL), or {@link #RESULT_NONE} (nothing
 * matched - an EMPTY result, and NO archive row was written anywhere). The tokens mirror
 * the resolve-or-register vocabulary; NONE replaces CREATED because the lookup chain
 * never reaches the create fallback (patient-domain "Internal Service Surface", the
 * lookup-only endpoint rides the same internal-key guard).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientLookupResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Decisive single hit (exact id pair, or exactly one live card_no). */
    public static final String RESULT_MATCHED = "MATCHED";

    /** Ambiguous/soft hits: the caller picks from {@code candidates}. */
    public static final String RESULT_CANDIDATES = "CANDIDATES";

    /** Nothing matched - an EMPTY result with NO side effect (never a creation). */
    public static final String RESULT_NONE = "NONE";

    /** 解析结果：MATCHED / CANDIDATES / NONE. */
    private String result;

    /** 决定命中时的身份（CANDIDATES/NONE 时为 NULL）. */
    private PatientIdentityResponse identity;

    /** 软匹配候选列表（调用方挑选，非错误）. */
    private List<PatientIdentityResponse> candidates;
}

package org.birdbro.lumen.setting.entity;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normal-range v2 tagged union of {@link RepTempMeasure#normal} (rep-temp-normal-range
 * design D1; spec delta exam-setting "Structured Template Content" v2): exactly one
 * of the two shapes rides the {@code kind} discriminator -
 * <ul>
 *   <li>{@code kind=range}: {@code min}/{@code max} as INDEPENDENTLY NULLABLE decimals
 *       (a blank end is a single-sided open interval; the write path rejects inverted
 *       bounds and both-ends-null);</li>
 *   <li>{@code kind=text}: a non-blank qualitative phrase of at most 64 characters
 *       (阴性/阳性 and friends - no numeric interval exists).</li>
 * </ul>
 * A measure without a range carries {@code normal: null} (no union object). The
 * conditional kind grouping cannot ride Bean Validation - the service asserts it
 * (the D3 校验位置 precedent). The report-filling side's abnormal-value alerting
 * consumes this shape in a future change.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepTempMeasureNormal {

    /** The {@code range} discriminator: numeric interval (min/max nullable decimals). */
    public static final String KIND_RANGE = "range";

    /** The {@code text} discriminator: qualitative phrase (non-blank, ≤64). */
    public static final String KIND_TEXT = "text";

    /** Shape discriminator (range/text) - anything else is rejected by the service. */
    private String kind;

    /** Interval lower bound (nullable decimal - null opens the interval upward). */
    private BigDecimal min;

    /** Interval upper bound (nullable decimal - null opens the interval downward). */
    private BigDecimal max;

    /** Qualitative phrase (kind=text, non-blank, ≤64 characters). */
    private String text;
}

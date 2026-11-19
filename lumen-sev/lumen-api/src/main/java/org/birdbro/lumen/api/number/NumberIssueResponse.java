package org.birdbro.lumen.api.number;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The number-issue response payload (number-rule-unification-sev task 1.1): the issued
 * number in the platform's format (`prefix + [stamp] + zero-padded serial + suffix`),
 * carried inside the standard {@code R} envelope by the endpoint.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NumberIssueResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The issued number (already formatted - never re-formatted client-side). */
    private String number;
}

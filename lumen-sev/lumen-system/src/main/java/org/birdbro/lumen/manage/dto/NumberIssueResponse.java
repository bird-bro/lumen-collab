package org.birdbro.lumen.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The machine issuance response payload (number-rule-unification-sev task 2.2): the
 * OWNER side's local mirror of the {@code NumberApi} contract DTO (identical field
 * names — {@code number} — the advice wraps it in the {@code R} envelope the contract
 * decodes).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NumberIssueResponse {

    /** The issued number (already formatted - never re-formatted client-side). */
    private String number;
}

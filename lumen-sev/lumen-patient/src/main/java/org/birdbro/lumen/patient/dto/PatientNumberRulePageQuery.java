package org.birdbro.lumen.patient.dto;

import org.birdbro.framework.config.PageSizeConfig;

/**
 * Number-rule page query ({@code GET /api/v1/patient/patient-number-rules},
 * patient-domain task 4.1): paging only in v1 - the rule table is a tiny config list
 * (one rule family today), no filters pinned by the web spec.
 *
 * @author birdbro
 */
public class PatientNumberRulePageQuery extends PageSizeConfig {
}

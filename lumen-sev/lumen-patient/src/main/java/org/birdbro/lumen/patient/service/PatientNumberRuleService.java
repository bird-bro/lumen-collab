package org.birdbro.lumen.patient.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.patient.dto.PatientNumberRuleCreateRequest;
import org.birdbro.lumen.patient.dto.PatientNumberRulePageQuery;
import org.birdbro.lumen.patient.dto.PatientNumberRuleUpdateRequest;
import org.birdbro.lumen.patient.entity.PatientNumberRule;

/**
 * Number-rule management (patient-domain task 4.1, brief decision 5) - the CRUD side
 * of {@code patient_number_rule}, DISTINCT from batch 2's issuance service
 * {@code PatientNumberService} (which only READS the active rule under its row lock).
 *
 * <p>Contract highlights:</p>
 *
 * <ul>
 *   <li><b>One active rule per type</b>: creating/updating a rule with
 *       {@code active=1} while ANOTHER live active rule exists for the same
 *       {@code rule_type} is an A0714 conflict whose detail names the type and the
 *       existing rule id (the {@code uk_patient_number_rule_active} partial index is
 *       the concurrency backstop).</li>
 *   <li><b>Active rules never delete</b>: deleting the ACTIVE rule is rejected A0400
 *       (启用的规则不可删除，请先停用 - the b1 ruling); inactive rules soft-delete via
 *       the house logic delete.</li>
 *   <li><b>Runtime state is untouchable</b>: {@code serial_cur}/{@code stamp_value}
 *       have no CRUD request field - only issuance moves them.</li>
 *   <li><b>Stamp default</b>: {@code stamp} is optional - {@code isStamp=1} with a
 *       null/blank stamp defaults to {@code yyMMdd} (the Java pattern the seed uses;
 *       never the lowercase form - uppercase 'Y' is java's week-year trap).</li>
 *   <li><b>Field validation</b> (serialLength 1-10, prefix/suffix ≤8 chars of
 *       {@code [A-Za-z0-9]}) rides the request DTOs' bean validation - the A0400
 *       payload surfaces the field messages for the web form.</li>
 * </ul>
 *
 * @author birdbro
 */
public interface PatientNumberRuleService {

    /**
     * Creates a rule; the active-rule uniqueness check runs first (A0714 naming the
     * existing rule), then the insert (the fresh row starts {@code serial_cur=0},
     * {@code stamp_value=NULL} - database defaults; the service never writes runtime
     * state).
     *
     * @param request the validated create form
     * @return the new rule id
     * @throws org.birdbro.common.exception.BusinessException code {@code A0714} when
     *         an active rule already exists for the type (detail names type + rule id)
     */
    Integer create(PatientNumberRuleCreateRequest request);

    /**
     * Updates a rule's format parameters (prefix/suffix/serialLength/isStamp/stamp/
     * active); null fields keep their current values (the house ignore-null copy), and
     * the active-rule uniqueness check runs against the OTHER rules of the type.
     *
     * @param id the rule id
     * @param request the validated update form
     * @return true when the row was updated
     * @throws org.birdbro.common.exception.BusinessException code {@code B0031} when
     *         the target does not exist; {@code A0714} when the activation conflicts
     *         with another active rule of the type
     */
    Boolean update(Integer id, PatientNumberRuleUpdateRequest request);

    /**
     * Deletes a rule (house logic delete). Only INACTIVE rules may delete.
     *
     * @param id the rule id
     * @return true when the row was logically deleted
     * @throws org.birdbro.common.exception.BusinessException code {@code B0031} when
     *         the target does not exist; {@code A0400} when the rule is the type's
     *         ACTIVE one (启用的规则不可删除，请先停用)
     */
    Boolean delete(Integer id);

    /**
     * Paged list over LIVE rules (deleted = 0 via {@code @TableLogic}), id desc - the
     * 号码规则 console table's backing list.
     *
     * @param query the paging query
     * @return the paged rules (runtime state columns included for display)
     */
    IPage<PatientNumberRule> page(PatientNumberRulePageQuery query);
}

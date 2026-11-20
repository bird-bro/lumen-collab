package org.birdbro.lumen.manage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.lumen.manage.dto.NumberRuleSaveRequest;

/**
 * The number-rule directory service (number-rule-unification-sev task 2.2, design D1;
 * spec number-rule "Number Rule Directory"): rule CRUD on the platform directory.
 * Exactly one ACTIVE live rule per family AND modality scope (NULL = 全局, V49) - the
 * partial unique index is the backstop; the service rejects the duplicate with the
 * A0714 data-duplication error first, its message naming the scope.
 *
 * @author birdbro
 */
public interface NumberRuleService {

    /** Paged rule directory, newest last (id order). */
    Page<org.birdbro.lumen.manage.entity.NumberRule> page(long pageNum, long pageSize);

    /** Creates one rule (duplicate active family → A0714). */
    Integer create(NumberRuleSaveRequest request);

    /** Full-field update of one rule (the family type is immutable). */
    Boolean update(Integer id, NumberRuleSaveRequest request);

    /**
     * The lifecycle toggle (the org-status-toggle pattern): deactivate is always
     * allowed; activating is refused with A0714 while the family's other rule is
     * live (one ACTIVE rule per family). Re-setting the current state is idempotent.
     */
    Boolean setActive(Integer id, Integer active);

    /** Logical delete (only inactive rules may delete - the V-family discipline). */
    Boolean delete(Integer id);
}

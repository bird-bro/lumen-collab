package org.birdbro.lumen.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One exam-item reference on the order (kernel 只持引用+快照 - the naming ruling):
 * {@code itemId} references the exam-setting catalog (the exam_item directory), and
 * {@code itemName} is the snapshot taken at order time so history stays stable when
 * the catalog renames. Carried inside the order's {@code exam_items} jsonb array.
 *
 * <p>jsonb widening (number-rule-modality-scope design D5): {@code modality} carries
 * the item's dict-1024 VALUE token (the same shape {@code org_device.type} and the
 * exam_item catalog store) so the issuer can scope the accession number by modality.
 * Pre-widening rows simply lack the key and read as {@code null} - the null token
 * resolves the family's GLOBAL pool.</p>
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExamItemRef {

    /** The exam-setting catalog item id (org.birdbro.lumen.setting domain). */
    private Integer itemId;

    /** The item name snapshot at order time. */
    private String itemName;

    /**
     * The item's modality token (dict 模态 1024 VALUE, e.g. US; client-originated from
     * the setting catalog - the kernel neither validates nor defaults it; null/blank =
     * the family global rule).
     */
    private String modality;
}

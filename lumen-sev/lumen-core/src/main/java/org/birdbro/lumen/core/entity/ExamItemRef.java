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
}

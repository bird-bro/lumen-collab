package org.birdbro.lumen.setting.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.birdbro.lumen.setting.entity.ExamBody;

/**
 * Body tree node ({@code GET /api/v1/setting/exam-bodies/list}, exam-setting-api): the
 * body entity flattened with its nested children, assembled service-side from the
 * {@code up_id} references. Roots are the up_id-less bodies; every child attaches to
 * exactly one parent of the same group, so the shape is a forest per modality.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BodyTreeNode extends ExamBody {

    /** Nested children (empty for leaves - the tree-select feed). */
    private List<BodyTreeNode> children = new ArrayList<>();
}

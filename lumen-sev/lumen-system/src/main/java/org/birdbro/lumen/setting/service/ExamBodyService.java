package org.birdbro.lumen.setting.service;

import java.util.List;

import org.birdbro.lumen.setting.dto.BodyCreateRequest;
import org.birdbro.lumen.setting.dto.BodyTreeNode;
import org.birdbro.lumen.setting.dto.BodyUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBody;

/**
 * Body-part management (exam-setting-api task 2.2): CRUD over {@code exam_body} - the
 * anatomy concept of the Option-A split. A body anchors on a LIVE group of the SAME
 * modality and may nest under a parent of the SAME group (service-enforced, zero
 * physical FKs). {@code GET /list} assembles the up_id forest service-side. Deletion
 * is guarded twice: live child bodies, then live item references - each refusal NAMES
 * its blocking resource (部位/项目) so the console surfaces it verbatim. Updates are
 * D9 null-aware full-replaces.
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/ExamBodyServiceImpl} body is unchanged from the former concrete
 * class).</p>
 *
 * @author birdbro
 */
public interface ExamBodyService {

    /**
     * The modality's (optionally group-scoped) live bodies as an up_id-nested forest -
     * the item drawer's cascade feed and the 检查部位 page's table.
     */
    List<BodyTreeNode> getList(String modality, Integer groupId);

    /** One body (the edit drawer's echo), or null when the id misses a live row. */
    ExamBody getById(Integer id);

    /** Creates a body; returns the database-generated id. */
    Integer create(BodyCreateRequest request);

    /** Full-replaces the editable surface (D9 null-aware); a missing id rejects A0400. */
    Boolean update(Integer id, BodyUpdateRequest request);

    /**
     * Logical delete, guarded by live children AND live item references - each refusal
     * names its blocking resource.
     */
    Boolean delete(Integer id);
}

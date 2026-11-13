package org.birdbro.lumen.setting.service;

import java.util.List;

import org.birdbro.lumen.setting.dto.BodyGroupCreateRequest;
import org.birdbro.lumen.setting.dto.BodyGroupUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBodyGroup;

/**
 * Body-group management (exam-setting-api task 2.1): CRUD over {@code exam_body_group},
 * the flat business classification of the 2026-11-12 Option-A split. Every write
 * validates the modality against dict 1027 (the single platform vocabulary - the
 * mapper behind {@link #requireModality}); the name is unique within the modality among
 * LIVE rows (service check A0400, partial unique index backstop); deletion is guarded
 * by live bodies and NAMES the blocking count so the console can surface it verbatim.
 * Updates are D9 null-aware full-replaces (explicit wrapper {@code set}s, never the
 * null-skipping updateById).
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/ExamBodyGroupServiceImpl} body is unchanged from the former concrete
 * class). {@link #requireModality} was package-private and is shared by every sibling
 * setting service - it joins the interface contract (visibility widened, signature and
 * callers untouched).</p>
 *
 * @author birdbro
 */
public interface ExamBodyGroupService {

    /** The modality's live groups in sort order (the cascade/filter feed). */
    List<ExamBodyGroup> getList(String modality);

    /** Creates a group; returns the database-generated id. */
    Integer create(BodyGroupCreateRequest request);

    /** Full-replaces the editable surface (D9 null-aware); a missing id rejects A0400. */
    Boolean update(Integer id, BodyGroupUpdateRequest request);

    /**
     * Logical delete, guarded by live bodies (the group must empty out first) - the
     * refusal names the blocking body count.
     */
    Boolean delete(Integer id);

    /**
     * The modality write guard: the value must be a live, enabled detail of dict 1027
     * 检查类型 - the setting domain defines NO vocabulary of its own (D5). Shared by
     * every sibling setting service.
     */
    void requireModality(String modality);
}

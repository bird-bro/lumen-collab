package org.birdbro.lumen.setting.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.setting.dto.MethodCreateRequest;
import org.birdbro.lumen.setting.dto.MethodPageQuery;
import org.birdbro.lumen.setting.dto.MethodUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamMethod;

/**
 * Method management (exam-setting-api task 3.1): CRUD over {@code exam_method} - one
 * modality's examination-technique vocabulary. Every write validates the modality
 * against dict 1027; the code is unique within the modality among LIVE rows (service
 * check A0400, partial unique index backstop); deletion is guarded by live item
 * bindings (the {@code exam_item_method} join) and names the blocking count. Updates
 * are D9 null-aware full-replaces.
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/ExamMethodServiceImpl} body is unchanged from the former concrete
 * class).</p>
 *
 * @author birdbro
 */
public interface ExamMethodService {

    /**
     * Paginated method table: modality-scoped, the keyword matched against name OR
     * code (the console's 名称/编码 query types collapse into one box). Ordered
     * id-descending (the console's newest-first habit).
     */
    IPage<ExamMethod> getPage(MethodPageQuery query);

    /**
     * Picker feed for the item form: the modality's live methods in sort order,
     * optionally narrowed by the same keyword.
     */
    List<ExamMethod> getList(String modality, String keyword);

    /** Creates a method; returns the database-generated id. */
    Integer create(MethodCreateRequest request);

    /** Full-replaces the editable surface (D9 null-aware); a missing id rejects A0400. */
    Boolean update(Integer id, MethodUpdateRequest request);

    /**
     * Logical delete, guarded by live item bindings - the refusal names the blocking
     * count (the items must unbind first).
     */
    Boolean delete(Integer id);
}

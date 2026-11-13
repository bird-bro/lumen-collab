package org.birdbro.lumen.setting.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.setting.dto.ItemCreateRequest;
import org.birdbro.lumen.setting.dto.ItemPageQuery;
import org.birdbro.lumen.setting.dto.ItemUpdateRequest;
import org.birdbro.lumen.setting.dto.ItemVO;

/**
 * Item management (exam-setting-api task 4.1): CRUD over {@code exam_item} - the
 * hospital-wide catalog row. Every write validates the modality against dict 1027 AND
 * its lockstep with the body's; the code is unique within the modality among LIVE rows
 * (soft-deleted rows free theirs); the method selection rewrites
 * {@code exam_item_method} inside the item's transaction (physical wipe + batch
 * insert - a mid-rewrite failure rolls the whole update back, the
 * AuthExamItemTransactionTests proof). Reads resolve body/group/method display names
 * SERVER-SIDE (D2 - zero denormalized copies); the group filter derives through the
 * body join.
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/ExamItemServiceImpl} body is unchanged from the former concrete
 * class).</p>
 *
 * @author birdbro
 */
public interface ExamItemService {

    /**
     * Paginated item table: modality-scoped, the 部位分组 filter derived through the
     * body (NO denormalized group column), the keyword matched against name OR code,
     * the 常用项目 toggle applied when present. Records come back as {@link ItemVO} s
     * with the display names resolved server-side.
     */
    IPage<ItemVO> getPage(ItemPageQuery query);

    /** One item as a display-resolved VO (the edit drawer's echo), or null. */
    ItemVO getById(Integer id);

    /** Creates an item and its binding set in ONE transaction; returns the new id. */
    Integer create(ItemCreateRequest request);

    /**
     * Full-replaces the item and its binding set in ONE transaction (D9 null-aware);
     * a missing id or a validation failure leaves everything untouched.
     */
    Boolean update(Integer id, ItemUpdateRequest request);

    /** Logical delete (the bindings die with the rewrite convention, not here). */
    Boolean delete(Integer id);
}

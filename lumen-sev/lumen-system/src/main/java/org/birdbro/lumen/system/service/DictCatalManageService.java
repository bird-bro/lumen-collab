package org.birdbro.lumen.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.system.entity.DictCatalEntity;
import org.birdbro.lumen.system.dto.CatalCreate;
import org.birdbro.lumen.system.dto.CatalModify;
import org.birdbro.lumen.system.dto.CatalQuery;

/**
 * Dictionary-catalog management (system-data task 3.1): the lumen-system side of the copied
 * {@code framework/dict} data layer - duplicate-code rejection ({@code A0714}), the
 * {@code Byte}→{@code Integer} enable adaptation for the OPS-blood {@code CatalCreate},
 * and the audit identity left to the copied {@code MetaObjectConfig} fill.
 *
 * @author birdbro
 */
public interface DictCatalManageService {

    /**
     * Creates a catalog (duplicate live code rejected with {@code A0714}); returns the
     * new id.
     */
    Integer create(CatalCreate request);

    /** Partial update (null fields skipped); an unknown target is rejected {@code B0031}. */
    Boolean update(Integer id, CatalModify request);

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    Boolean delete(Integer id);

    /** Paginated catalog query (name/cls optional filters, id desc - the copied getPage). */
    IPage<DictCatalEntity> list(CatalQuery query);
}

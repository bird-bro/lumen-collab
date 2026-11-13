package org.birdbro.lumen.manage.service;

import java.util.List;

import org.birdbro.lumen.manage.entity.DictDetailEntity;
import org.birdbro.lumen.manage.dto.DetailCreate;
import org.birdbro.lumen.manage.dto.DetailModify;
import org.birdbro.lumen.manage.dto.DictItemDto;

/**
 * Dictionary-detail management (system-data task 3.1): create/update/delete over the
 * copied {@code framework/dict} data layer plus the by-code live list (scenario 4).
 *
 * @author birdbro
 */
public interface DictDetailManageService {

    /**
     * Creates a detail under a live catalog (unknown catalog rejected {@code A0400}, a
     * duplicate live (caId, value) pair rejected {@code A0714}); anchored by {@code ca_id}
     * alone (V9 dropped the denormalized ca_code);
     * returns the new id.
     */
    Integer create(DetailCreate request);

    /** Partial update (null fields skipped); an unknown target is rejected {@code B0031}. */
    Boolean update(Integer id, DetailModify request);

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    Boolean delete(Integer id);

    /**
     * The catalog's LIVE details ordered by {@code sort} (the by-id list endpoint; V12
     * re-anchored the former by-code {@code listByCaCode} onto {@code caId} - the
     * catalog {@code code} column is gone).
     */
    List<DictDetailEntity> listByCaId(Integer caId);

    /**
     * Label lookup by catalog NAME (role-type-dict-api; the {@code /items/{catalogName}}
     * endpoint): resolves the LIVE + ENABLED catalog by its unique name (V12 made the
     * name the only handle), then returns its LIVE, ENABLED details as the slim
     * {@code {code, value}[]} projection sorted by {@code sort} ascending. An unknown
     * or disabled catalog resolves to an EMPTY list - never an error (coordination
     * D2: a mis-named consumer cannot break a page).
     */
    List<DictItemDto> listEnabledItemsByName(String name);
}

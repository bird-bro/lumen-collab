package org.birdbro.lumen.system.service;

import java.util.List;

import org.birdbro.lumen.system.entity.DictDetailEntity;
import org.birdbro.lumen.system.dto.DetailCreate;
import org.birdbro.lumen.system.dto.DetailModify;

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
}

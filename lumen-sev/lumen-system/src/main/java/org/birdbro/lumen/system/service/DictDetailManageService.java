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
     * duplicate live (caCode, value) pair rejected {@code A0714}); the {@code ca_code}
     * anchor is SERVER-RESOLVED from the catalog (double-anchor consistency, scenario 3);
     * returns the new id.
     */
    Integer create(DetailCreate request);

    /** Partial update (null fields skipped); an unknown target is rejected {@code B0031}. */
    Boolean update(Integer id, DetailModify request);

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    Boolean delete(Integer id);

    /**
     * The catalog's LIVE details ordered by {@code sort} (the by-code list endpoint, the
     * copied service's additive {@code listByCaCode}).
     */
    List<DictDetailEntity> listByCaCode(String caCode);
}

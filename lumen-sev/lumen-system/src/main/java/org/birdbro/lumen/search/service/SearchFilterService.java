package org.birdbro.lumen.search.service;

import java.util.List;

import org.birdbro.lumen.search.dto.SearchFilterRequest;
import org.birdbro.lumen.search.dto.SearchFilterView;

/**
 * The saved-search service (us-registration-redesign-api tasks 5.1-5.2, design D3):
 * every access is UID-SCOPED - a user never sees or mutates another user's rows
 * (the RepTempServiceImpl ownership precedent: the refusal carries no row content).
 * The set-default is a FULL-REPLACE inside one transaction (the role_menu assign
 * precedent): the user's previous default for the same pageKey turns off, the new one
 * turns on - the partial unique index is the backstop, not the mechanism.
 *
 * @author birdbro
 */
public interface SearchFilterService {

    /** Lists the CALLER's live filters for the page key, update time descending. */
    List<SearchFilterView> list(Integer uid, String pageKey);

    /** Creates one uid-owned filter; the query jsonb rides verbatim. */
    SearchFilterView create(Integer uid, SearchFilterRequest request);

    /** Updates name/query (the ownership checked first; isDefault untouched here). */
    SearchFilterView update(Integer uid, Integer id, SearchFilterRequest request);

    /** Full-replace default: clears the caller's previous default for the page, sets this one. */
    SearchFilterView setDefault(Integer uid, Integer id);

    /** Soft-deletes the caller's filter. */
    void delete(Integer uid, Integer id);
}

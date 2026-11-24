package org.birdbro.lumen.search.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import cn.hutool.core.util.StrUtil;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.search.dto.SearchFilterRequest;
import org.birdbro.lumen.search.dto.SearchFilterView;
import org.birdbro.lumen.search.entity.SearchFilter;
import org.birdbro.lumen.search.mapper.SearchFilterMapper;
import org.birdbro.lumen.search.service.SearchFilterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved-search implementation (us-registration-redesign-api tasks 5.1-5.2, design D3).
 * Recorded decisions:
 *
 * <ol>
 *   <li>UID scoping on EVERY access: the ownership miss reads as NOT FOUND (no row
 *       content leaks - the RepTempServiceImpl precedent), so cross-user ids cannot
 *       even be probed.</li>
 *   <li>The query jsonb passes through VERBATIM - the service never interprets the
 *       per-page shape (its contract belongs to each workbench).</li>
 *   <li>The set-default runs in ONE transaction: clear every live default for
 *       (uid, pageKey), then set the target - the partial unique index backstops any
 *       concurrent violation, the endpoint is the mechanism.</li>
 *   <li>{@code isDefault} on the CREATE body is honored (the ③ UI can save-and-default
 *       in one call); the generic update NEVER touches it (the dedicated endpoint
 *       owns the transition).</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class SearchFilterServiceImpl implements SearchFilterService {

    private final SearchFilterMapper searchFilterMapper;

    public SearchFilterServiceImpl(SearchFilterMapper searchFilterMapper) {
        this.searchFilterMapper = searchFilterMapper;
    }

    @Override
    public List<SearchFilterView> list(Integer uid, String pageKey) {
        return searchFilterMapper.selectList(new LambdaQueryWrapper<SearchFilter>()
                        .eq(SearchFilter::getUid, uid)
                        .eq(StrUtil.isNotBlank(pageKey), SearchFilter::getPageKey, pageKey)
                        .orderByDesc(SearchFilter::getUpdateTime))
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    public SearchFilterView create(Integer uid, SearchFilterRequest request) {
        SearchFilter filter = SearchFilter.builder()
                .uid(uid)
                .pageKey(requirePageKey(request))
                .name(requireName(request))
                .query(request.getQuery())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()) ? 1 : 0)
                .build();
        searchFilterMapper.insert(filter);
        if (filter.getIsDefault() == 1) {
            clearOtherDefaults(uid, filter.getPageKey(), filter.getId());
        }
        return toView(filter);
    }

    @Override
    public SearchFilterView update(Integer uid, Integer id, SearchFilterRequest request) {
        SearchFilter filter = ownedLive(uid, id);
        filter.setPageKey(requirePageKey(request));
        filter.setName(requireName(request));
        filter.setQuery(request.getQuery());
        searchFilterMapper.updateById(filter);
        return toView(filter);
    }

    @Override
    @Transactional
    public SearchFilterView setDefault(Integer uid, Integer id) {
        SearchFilter filter = ownedLive(uid, id);
        clearOtherDefaults(uid, filter.getPageKey(), filter.getId());
        filter.setIsDefault(1);
        searchFilterMapper.updateById(filter);
        return toView(filter);
    }

    @Override
    public void delete(Integer uid, Integer id) {
        SearchFilter filter = ownedLive(uid, id);
        searchFilterMapper.deleteById(filter.getId());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The ownership miss reads as NOT FOUND - no row content leaks (the ownership is
     * not probed either: a cross-user id and an absent id answer identically).
     */
    private SearchFilter ownedLive(Integer uid, Integer id) {
        SearchFilter filter = id == null ? null
                : searchFilterMapper.selectById(id);
        if (filter == null || !filter.getUid().equals(uid)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "筛选项不存在: " + id);
        }
        return filter;
    }

    /** Turns OFF every OTHER live default of the (uid, pageKey) pair. */
    private void clearOtherDefaults(Integer uid, String pageKey, Integer keepId) {
        searchFilterMapper.update(null, new LambdaUpdateWrapper<SearchFilter>()
                .set(SearchFilter::getIsDefault, 0)
                .eq(SearchFilter::getUid, uid)
                .eq(SearchFilter::getPageKey, pageKey)
                .eq(SearchFilter::getIsDefault, 1)
                .ne(keepId != null, SearchFilter::getId, keepId));
    }

    private String requirePageKey(SearchFilterRequest request) {
        if (StrUtil.isBlank(request.getPageKey())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "筛选项必须携带页面区分键");
        }
        return request.getPageKey().trim();
    }

    private String requireName(SearchFilterRequest request) {
        if (StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "筛选项必须携带名称");
        }
        return request.getName().trim();
    }

    private SearchFilterView toView(SearchFilter filter) {
        return SearchFilterView.builder()
                .id(filter.getId())
                .pageKey(filter.getPageKey())
                .name(filter.getName())
                .query(filter.getQuery())
                .isDefault(filter.getIsDefault() != null && filter.getIsDefault() == 1)
                .updateTime(filter.getUpdateTime())
                .build();
    }
}

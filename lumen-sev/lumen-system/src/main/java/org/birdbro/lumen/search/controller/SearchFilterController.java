package org.birdbro.lumen.search.controller;

import java.util.List;

import io.swagger.annotations.ApiOperation;

import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.lumen.search.dto.SearchFilterRequest;
import org.birdbro.lumen.search.dto.SearchFilterView;
import org.birdbro.lumen.search.service.SearchFilterService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The saved-search surface (us-registration-redesign-api tasks 5.1-5.2, design D3;
 * 拍板 07-A): generic uid-owned REST under {@code /api/v1/system/search-filters} -
 * authenticated-only WITHOUT a permission family (personal workbench data; the
 * dict-lookup authenticated-only precedent). The uid comes ONLY from the session
 * identity (the UserHeaderBridgeFilter-derived header) - never the body.
 *
 * @author birdbro
 */
@RestController
@RequestMapping("/api/v1/system/search-filters")
public class SearchFilterController {

    private final SearchFilterService searchFilterService;

    public SearchFilterController(SearchFilterService searchFilterService) {
        this.searchFilterService = searchFilterService;
    }

    @ApiOperation("我的筛选项列表（uid 作用域，按更新时间倒序）")
    @GetMapping
    public List<SearchFilterView> list(
            @RequestParam(value = "pageKey", required = false) String pageKey) {
        return searchFilterService.list(currentUid(), pageKey);
    }

    @ApiOperation("新建筛选项（uid 服务端绑定；query jsonb 原文透传）")
    @PostMapping
    public SearchFilterView create(@RequestBody SearchFilterRequest request) {
        return searchFilterService.create(currentUid(), request);
    }

    @ApiOperation("编辑筛选项（所有权校验；默认标记不走此处）")
    @PutMapping("/{id}")
    public SearchFilterView update(@PathVariable Integer id,
            @RequestBody SearchFilterRequest request) {
        return searchFilterService.update(currentUid(), id, request);
    }

    @ApiOperation("设为页面默认（全量替换语义：同页旧默认熄灭）")
    @PutMapping("/{id}/default")
    public SearchFilterView setDefault(@PathVariable Integer id) {
        return searchFilterService.setDefault(currentUid(), id);
    }

    @ApiOperation("删除筛选项（软删）")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        searchFilterService.delete(currentUid(), id);
    }

    /** The session identity (the RepTempServiceImpl precedent - fail loudly on a
     *  header-less session; the JWT filter guarantees it on authenticated routes). */
    private Integer currentUid() {
        UserHeader header = SecurityUtils.getUserHeader();
        if (header == null || header.getUid() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "当前会话缺少用户身份");
        }
        return header.getUid();
    }
}

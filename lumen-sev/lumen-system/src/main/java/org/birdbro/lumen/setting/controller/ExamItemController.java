package org.birdbro.lumen.setting.controller;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.setting.dto.ItemCreateRequest;
import org.birdbro.lumen.setting.dto.ItemPageQuery;
import org.birdbro.lumen.setting.dto.ItemUpdateRequest;
import org.birdbro.lumen.setting.dto.ItemVO;
import org.birdbro.lumen.setting.service.ExamItemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Item endpoints (exam-setting-api task 4.2): the 检查项目 page's CRUD - the catalog
 * core the console maintains and the future 开单 flow will read. Class gate = the list
 * perm; mutating methods pin their button perms (the OrgDeviceController shape).
 *
 * @author birdbro
 */
@Api(tags = "检查项目")
@PreAuthorize("hasAuthority('setting:item:list')")
@RestController
@RequestMapping("/api/v1/setting/exam-items")
public class ExamItemController {

    private final ExamItemService examItemService;

    public ExamItemController(ExamItemService examItemService) {
        this.examItemService = examItemService;
    }

    /** Creates an item + its method bindings in one transaction; returns the new id. */
    @Log(title = "检查项目", type = "新增")
    @ApiOperation("创建检查项目")
    @PreAuthorize("hasAuthority('setting:item:create')") // button-perm pin
    @PostMapping
    public Integer create(@Valid @RequestBody ItemCreateRequest request) {
        return examItemService.create(request);
    }

    /** Full-replace update + binding rewrite in one transaction (D9 null-aware). */
    @Log(title = "检查项目", type = "修改")
    @ApiOperation("检查项目全量更新")
    @PreAuthorize("hasAuthority('setting:item:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody ItemUpdateRequest request) {
        return examItemService.update(id, request);
    }

    /** Logical delete. */
    @Log(title = "检查项目", type = "删除")
    @ApiOperation("逻辑删除检查项目")
    @PreAuthorize("hasAuthority('setting:item:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return examItemService.delete(id);
    }

    /** One item, display names resolved (the edit drawer's echo), or null. */
    @ApiOperation("检查项目详情")
    @GetMapping("/{id}")
    public ItemVO getById(@PathVariable Integer id) {
        return examItemService.getById(id);
    }

    /** Paginated item table (modality/分组/keyword/isCommon filters, display-resolved). */
    @ApiOperation("检查项目分页列表")
    @GetMapping("/page")
    public IPage<ItemVO> page(ItemPageQuery query) {
        return examItemService.getPage(query);
    }
}

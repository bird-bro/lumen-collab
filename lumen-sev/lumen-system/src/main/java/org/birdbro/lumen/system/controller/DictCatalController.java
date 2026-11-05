package org.birdbro.lumen.system.controller;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;
import org.birdbro.lumen.system.entity.DictCatalEntity;
import org.birdbro.lumen.system.dto.CatalCreate;
import org.birdbro.lumen.system.dto.CatalModify;
import org.birdbro.lumen.system.dto.CatalQuery;
import org.birdbro.lumen.system.service.DictCatalManageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Dictionary-catalog endpoints (system-data task 3.1): CRUD on the dropdown directories
 * ({@code public.dict_catal}) under the {@code /api/v1/system} namespace nginx routes to
 * this service (Gateway Path Convention). All endpoints stay token-guarded by the copied
 * security chain ({@code anyRequest().authenticated()}).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}). Mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE: the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204" - returning a value keeps the
 * "200" success code the spec's scenarios use.</p>
 *
 * @author birdbro
 */
@Api(tags = "字典目录")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:dict:list')")
@RestController
@RequestMapping("/api/v1/system/dicts/catal")
public class DictCatalController {

    private final DictCatalManageService dictCatalManageService;

    public DictCatalController(DictCatalManageService dictCatalManageService) {
        this.dictCatalManageService = dictCatalManageService;
    }

    /** Creates a catalog (duplicate live name rejected {@code A0714}; V12 dropped the code column); returns the new id. */
    @ApiOperation("创建字典目录（名称唯一，A0714 拒绝重复）")
    @Log(title = "字典目录", type = "新增")
    @PostMapping
    public Integer create(@Valid @RequestBody CatalCreate request) {
        return dictCatalManageService.create(request);
    }

    /** Partial update: rename / sort / enable (null fields skipped). */
    @ApiOperation("字典目录部分更新（改名/排序/启停）")
    @Log(title = "字典目录", type = "修改")
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody CatalModify request) {
        return dictCatalManageService.update(id, request);
    }

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    @ApiOperation("逻辑删除字典目录")
    @Log(title = "字典目录", type = "删除")
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return dictCatalManageService.delete(id);
    }

    /** Paginated catalogs (name/cls optional filters, id desc) - the copied getPage. */
    @ApiOperation("字典目录分页列表（name/cls 可选过滤）")
    @GetMapping("/list")
    public IPage<DictCatalEntity> list(CatalQuery query) {
        return dictCatalManageService.list(query);
    }
}

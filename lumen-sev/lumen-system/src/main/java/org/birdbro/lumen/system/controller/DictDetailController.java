package org.birdbro.lumen.system.controller;

import java.util.List;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;
import org.birdbro.lumen.system.entity.DictDetailEntity;
import org.birdbro.lumen.system.dto.DetailCreate;
import org.birdbro.lumen.system.dto.DetailModify;
import org.birdbro.lumen.system.service.DictDetailManageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Dictionary-detail endpoints (system-data task 3.1): CRUD on the dropdown options
 * ({@code public.dict_detail}) plus the by-code live list (spec scenario 4), under the
 * {@code /api/v1/system} namespace nginx routes to this service (Gateway Path Convention).
 * All endpoints stay token-guarded by the copied security chain
 * ({@code anyRequest().authenticated()}).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}). Mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE: the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204" - returning a value keeps the
 * "200" success code the spec's scenarios use.</p>
 *
 * @author birdbro
 */
@Api(tags = "字典明细")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:dict:list')")
@RestController
@RequestMapping("/api/v1/system/dicts/detail")
public class DictDetailController {

    private final DictDetailManageService dictDetailManageService;

    public DictDetailController(DictDetailManageService dictDetailManageService) {
        this.dictDetailManageService = dictDetailManageService;
    }

    /** Creates a detail under a live catalog (anchored by caId, value required); returns the new id. */
    @ApiOperation("创建字典明细（caId 锚定，(caId,value) 重复 A0714 拒绝）")
    @Log(title = "字典明细", type = "新增")
    @PreAuthorize("hasAuthority('system:dict-detail:create')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PostMapping
    public Integer create(@Valid @RequestBody DetailCreate request) {
        return dictDetailManageService.create(request);
    }

    /** Partial update: re-anchor / value / sort / enable (null fields skipped). */
    @ApiOperation("字典明细部分更新（改挂目录/值/排序/启停）")
    @Log(title = "字典明细", type = "修改")
    @PreAuthorize("hasAuthority('system:dict-detail:update')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody DetailModify request) {
        return dictDetailManageService.update(id, request);
    }

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    @ApiOperation("逻辑删除字典明细")
    @Log(title = "字典明细", type = "删除")
    @PreAuthorize("hasAuthority('system:dict-detail:delete')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return dictDetailManageService.delete(id);
    }

    /** The catalog's LIVE details ordered by sort ({@code ?caId=}; V12 - the catalog code column is gone). */
    @ApiOperation("按目录ID列启用明细（仅 live，sort 升序）")
    @GetMapping("/list")
    public List<DictDetailEntity> list(@RequestParam("caId") Integer caId) {
        return dictDetailManageService.listByCaId(caId);
    }
}

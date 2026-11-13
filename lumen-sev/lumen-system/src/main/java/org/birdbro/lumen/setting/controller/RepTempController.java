package org.birdbro.lumen.setting.controller;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.setting.dto.examsetting.TempCreateRequest;
import org.birdbro.lumen.setting.dto.examsetting.TempPageQuery;
import org.birdbro.lumen.setting.dto.examsetting.TempUpdateRequest;
import org.birdbro.lumen.setting.entity.RepTemp;
import org.birdbro.lumen.setting.service.RepTempService;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Report-template endpoints (exam-setting-api task 5.2): the 报告模板 page's tree +
 * paged file list + CRUD, scope-switched by {@code type} (the 公共/私有 tabs). Class
 * gate = the list perm; mutating methods pin their button perms. PRIVATE-scope
 * enforcement lives in the service (session-bound owner, leak-free refusals).
 *
 * @author birdbro
 */
@Api(tags = "报告模板")
@PreAuthorize("hasAuthority('setting:temp:list')")
@RestController
@RequestMapping("/api/v1/setting/rep-temps")
public class RepTempController {

    private final RepTempService repTempService;

    public RepTempController(RepTempService repTempService) {
        this.repTempService = repTempService;
    }

    /** Creates a tree node (private rows bind the session owner); returns the new id. */
    @Log(title = "报告模板", type = "新增")
    @ApiOperation("创建模板节点")
    @PreAuthorize("hasAuthority('setting:temp:create')") // button-perm pin
    @PostMapping
    public Integer create(@Valid @RequestBody TempCreateRequest request) {
        return repTempService.create(request);
    }

    /** Full-replace update (D9 null-aware; scope/ownership stay as stored). */
    @Log(title = "报告模板", type = "修改")
    @ApiOperation("模板节点全量更新")
    @PreAuthorize("hasAuthority('setting:temp:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody TempUpdateRequest request) {
        return repTempService.update(id, request);
    }

    /** Logical delete (private rows refuse non-owners). */
    @Log(title = "报告模板", type = "删除")
    @ApiOperation("逻辑删除模板节点")
    @PreAuthorize("hasAuthority('setting:temp:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return repTempService.delete(id);
    }

    /** The scope's full tree for one modality (private = the caller's own). */
    @ApiOperation("模板树（按检查类型+范围）")
    @GetMapping("/tree")
    public List<Map<String, Object>> tree(@RequestParam String modality,
            @RequestParam Integer type) {
        return repTempService.getTree(modality, type);
    }

    /** The templates under one parent node of one scope, name-filtered, paged. */
    @ApiOperation("模板文件列表（目录下分页）")
    @GetMapping("/page")
    public IPage<RepTemp> page(@Valid TempPageQuery query) {
        return repTempService.getPage(query);
    }
}

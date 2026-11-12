package org.birdbro.lumen.setting.controller;

import java.util.List;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.setting.dto.examsetting.MethodCreateRequest;
import org.birdbro.lumen.setting.dto.examsetting.MethodPageQuery;
import org.birdbro.lumen.setting.dto.examsetting.MethodUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamMethod;
import org.birdbro.lumen.setting.service.ExamMethodService;
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
 * Method endpoints (exam-setting-api task 3.1): the 检查方法 page's CRUD plus the
 * 检查项目 form's picker feed. Class gate = the list perm; mutating methods pin their
 * button perms (the OrgDeviceController shape).
 *
 * @author birdbro
 */
@Api(tags = "检查方法")
@PreAuthorize("hasAuthority('setting:method:list')")
@RestController
@RequestMapping("/api/v1/setting/exam-methods")
public class ExamMethodController {

    private final ExamMethodService examMethodService;

    public ExamMethodController(ExamMethodService examMethodService) {
        this.examMethodService = examMethodService;
    }

    /** Creates a method (modality/code validated service-side); returns the new id. */
    @Log(title = "检查方法", type = "新增")
    @ApiOperation("创建检查方法")
    @PreAuthorize("hasAuthority('setting:method:create')") // button-perm pin
    @PostMapping
    public Integer create(@Valid @RequestBody MethodCreateRequest request) {
        return examMethodService.create(request);
    }

    /** Full-replace update (D9 null-aware: absent optionals clear). */
    @Log(title = "检查方法", type = "修改")
    @ApiOperation("检查方法全量更新")
    @PreAuthorize("hasAuthority('setting:method:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody MethodUpdateRequest request) {
        return examMethodService.update(id, request);
    }

    /** Logical delete, guarded by live item bindings. */
    @Log(title = "检查方法", type = "删除")
    @ApiOperation("逻辑删除检查方法")
    @PreAuthorize("hasAuthority('setting:method:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return examMethodService.delete(id);
    }

    /** Paginated method table (modality + name/code keyword). */
    @ApiOperation("检查方法分页列表")
    @GetMapping("/page")
    public IPage<ExamMethod> page(MethodPageQuery query) {
        return examMethodService.getPage(query);
    }

    /** Picker feed for the item form: the modality's live methods in sort order. */
    @ApiOperation("检查方法列表（项目表单选择器）")
    @GetMapping("/list")
    public List<ExamMethod> list(@RequestParam String modality,
            @RequestParam(required = false) String keyword) {
        return examMethodService.getList(modality, keyword);
    }
}

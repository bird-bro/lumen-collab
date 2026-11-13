package org.birdbro.lumen.setting.controller;

import java.util.List;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.setting.dto.examsetting.BodyCreateRequest;
import org.birdbro.lumen.setting.dto.examsetting.BodyTreeNode;
import org.birdbro.lumen.setting.dto.examsetting.BodyUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBody;
import org.birdbro.lumen.setting.service.ExamBodyService;
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
 * Body endpoints (exam-setting-api task 2.2): the up_id-nested body forest feeding the
 * 检查部位 page's table and the 检查项目 drawer's cascade tree select. Class gate =
 * the list perm ({@code setting:body:list}); mutating methods pin their button perms.
 *
 * @author birdbro
 */
@Api(tags = "检查部位")
@PreAuthorize("hasAuthority('setting:body:list')")
@RestController
@RequestMapping("/api/v1/setting/exam-bodies")
public class ExamBodyController {

    private final ExamBodyService examBodyService;

    public ExamBodyController(ExamBodyService examBodyService) {
        this.examBodyService = examBodyService;
    }

    /** Creates a body (group/parent validated service-side); returns the new id. */
    @Log(title = "检查部位", type = "新增")
    @ApiOperation("创建检查部位")
    @PreAuthorize("hasAuthority('setting:body:create')") // button-perm pin
    @PostMapping
    public Integer create(@Valid @RequestBody BodyCreateRequest request) {
        return examBodyService.create(request);
    }

    /** Full-replace update (D9 null-aware: absent optionals clear). */
    @Log(title = "检查部位", type = "修改")
    @ApiOperation("检查部位全量更新")
    @PreAuthorize("hasAuthority('setting:body:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody BodyUpdateRequest request) {
        return examBodyService.update(id, request);
    }

    /** Logical delete, guarded by live children and item references. */
    @Log(title = "检查部位", type = "删除")
    @ApiOperation("逻辑删除检查部位")
    @PreAuthorize("hasAuthority('setting:body:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return examBodyService.delete(id);
    }

    /** One body (the edit drawer's echo), or null when the id misses a live row. */
    @ApiOperation("检查部位详情")
    @GetMapping("/{id}")
    public ExamBody getById(@PathVariable Integer id) {
        return examBodyService.getById(id);
    }

    /**
     * The modality's (optionally group-scoped) live bodies as an up_id-nested forest.
     */
    @ApiOperation("检查部位树（按检查类型，可按分组过滤）")
    @GetMapping("/list")
    public List<BodyTreeNode> list(@RequestParam String modality,
            @RequestParam(required = false) Integer groupId) {
        return examBodyService.getList(modality, groupId);
    }
}

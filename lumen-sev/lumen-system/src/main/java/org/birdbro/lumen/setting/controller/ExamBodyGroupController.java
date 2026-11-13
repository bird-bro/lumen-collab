package org.birdbro.lumen.setting.controller;

import java.util.List;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.setting.dto.BodyGroupCreateRequest;
import org.birdbro.lumen.setting.dto.BodyGroupUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBodyGroup;
import org.birdbro.lumen.setting.service.ExamBodyGroupService;
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
 * Body-group endpoints (exam-setting-api task 2.1), the 检查部位 page's left-column
 * feed and the 检查项目 page's cascade filter source. All endpoints stay token-guarded
 * by the copied security chain; the class gate is the list perm - groups and bodies
 * share the ONE {@code setting:body:*} perm family (the in-page management ruling,
 * coordination exam-setting.md decision 9), mutating methods pin their button perms
 * (the OrgDepController/OrgDeviceController shape).
 *
 * <p>Mutation methods return {@code Boolean.TRUE} / the new id rather than void ON
 * PURPOSE - the copied advice renders a null body through its 204 branch.</p>
 *
 * @author birdbro
 */
@Api(tags = "部位分组")
@PreAuthorize("hasAuthority('setting:body:list')")
@RestController
@RequestMapping("/api/v1/setting/exam-body-groups")
public class ExamBodyGroupController {

    private final ExamBodyGroupService examBodyGroupService;

    public ExamBodyGroupController(ExamBodyGroupService examBodyGroupService) {
        this.examBodyGroupService = examBodyGroupService;
    }

    /** Creates a group (modality/name validated service-side); returns the new id. */
    @Log(title = "部位分组", type = "新增")
    @ApiOperation("创建部位分组")
    @PreAuthorize("hasAuthority('setting:body:create')") // button-perm pin
    @PostMapping
    public Integer create(@Valid @RequestBody BodyGroupCreateRequest request) {
        return examBodyGroupService.create(request);
    }

    /** Full-replace update (D9 null-aware: absent optionals clear). */
    @Log(title = "部位分组", type = "修改")
    @ApiOperation("部位分组全量更新")
    @PreAuthorize("hasAuthority('setting:body:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody BodyGroupUpdateRequest request) {
        return examBodyGroupService.update(id, request);
    }

    /** Logical delete, guarded by live bodies (the group must empty out first). */
    @Log(title = "部位分组", type = "删除")
    @ApiOperation("逻辑删除部位分组")
    @PreAuthorize("hasAuthority('setting:body:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return examBodyGroupService.delete(id);
    }

    /** The modality's live groups in sort order (the cascade/filter feed). */
    @ApiOperation("部位分组列表（按检查类型）")
    @GetMapping("/list")
    public List<ExamBodyGroup> list(@RequestParam String modality) {
        return examBodyGroupService.getList(modality);
    }
}

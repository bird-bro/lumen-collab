package org.birdbro.lumen.system.controller;

import java.util.List;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.system.dto.CreateOrgDepRequest;
import org.birdbro.lumen.system.dto.UpdateOrgDepRequest;
import org.birdbro.lumen.system.entity.OrgDep;
import org.birdbro.lumen.system.service.OrgDepService;
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
 * OrgDep management endpoints (auth-rbac-foundation task 4.2): CRUD on the
 * {@code department} table under the {@code /api/v1/system} namespace nginx routes to
 * this service (Gateway Path Convention). All endpoints stay token-guarded by the
 * copied security chain ({@code anyRequest().authenticated()} - no
 * {@code WebSecurityCustomizer} addition).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}). Mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE: the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204" - returning a value keeps the
 * "200" success code the spec's scenarios use. The department ENTITY is a safe response
 * shape: it carries no secret fields (unlike {@code user_info}, which must go through a
 * DTO to keep the password hash out of the JSON).</p>
 *
 * @author birdbro
 */
@Api(tags = "科室管理")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:dep:list')")
@RestController
@RequestMapping("/api/v1/system/org-deps")
public class OrgDepController {

    private final OrgDepService orgDepService;

    public OrgDepController(OrgDepService orgDepService) {
        this.orgDepService = orgDepService;
    }

    /** Creates a department for a org node (FK validated service-side); returns the new id. */
    @Log(title = "科室管理", type = "新增")
    @ApiOperation("创建科室（FK 机构）")
    @PostMapping
    public Integer create(@Valid @RequestBody CreateOrgDepRequest request) {
        return orgDepService.create(request);
    }

    /** Partial update: re-anchor / change code / rename (null fields skipped). */
    @Log(title = "科室管理", type = "修改")
    @ApiOperation("科室部分更新（改挂机构/编码/名称）")
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateOrgDepRequest request) {
        return orgDepService.update(id, request);
    }

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    @Log(title = "科室管理", type = "删除")
    @ApiOperation("逻辑删除科室")
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return orgDepService.delete(id);
    }

    /** Live departments, all when {@code orgId} is absent, else scoped to that org. */
    @ApiOperation("科室列表（orgId 可选过滤）")
    @GetMapping("/list")
    public List<OrgDep> list(@RequestParam(required = false) Integer orgId) {
        return orgDepService.list(orgId);
    }
}

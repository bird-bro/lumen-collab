package org.birdbro.lumen.system.controller;

import java.util.List;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.system.dto.CreateOrgRequest;
import org.birdbro.lumen.system.dto.UpdateOrgRequest;
import org.birdbro.lumen.system.entity.OrgInfo;
import org.birdbro.lumen.system.service.OrgService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Org-tree management endpoints (auth-rbac-foundation task 4.1): CRUD on the
 * hierarchical org tree plus the tree query, under the {@code /api/v1/system} namespace
 * nginx routes to this service (Gateway Path Convention). All endpoints stay
 * token-guarded by the copied security chain ({@code anyRequest().authenticated()} -
 * no {@code WebSecurityCustomizer} addition; the batch-3 anonymous rule covers
 * {@code /api/v1/system/login} ONLY).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}). Mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE: the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204" - returning a value keeps the
 * "200" success code the spec's scenarios use.</p>
 *
 * @author birdbro
 */
@Api(tags = "机构管理")
@RestController
@RequestMapping("/api/v1/system/orgs")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    /** Creates a org ({@code upId = 0} = a root - multi-root capable); returns the new id. */
    @Log(title = "机构管理", type = "新增")
    @ApiOperation("创建机构（upId=0 为根，多机构部署）")
    @PostMapping
    public Integer create(@Valid @RequestBody CreateOrgRequest request) {
        return orgService.create(request);
    }

    /** Partial update: rename / re-parent / level / type (null fields skipped). */
    @Log(title = "机构管理", type = "修改")
    @ApiOperation("机构部分更新（改名/挂父/层级/类型）")
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateOrgRequest request) {
        return orgService.update(id, request);
    }

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    @Log(title = "机构管理", type = "删除")
    @ApiOperation("逻辑删除机构")
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return orgService.delete(id);
    }

    /** The assembled org tree (roots = {@code upId = 0}, children nested by {@code upId}). */
    @ApiOperation("机构树（upId 装配，多根）")
    @GetMapping("/tree")
    public List<OrgInfo> tree() {
        return orgService.tree();
    }
}

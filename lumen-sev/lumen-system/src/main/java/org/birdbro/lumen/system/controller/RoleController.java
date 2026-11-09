package org.birdbro.lumen.system.controller;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.system.dto.AssignRoleMenusRequest;
import org.birdbro.lumen.system.dto.CreateRoleRequest;
import org.birdbro.lumen.system.dto.RoleItemDto;
import org.birdbro.lumen.system.dto.RoleListQuery;
import org.birdbro.lumen.system.dto.SetRoleEnableRequest;
import org.birdbro.lumen.system.dto.UpdateRoleRequest;
import org.birdbro.lumen.system.service.RoleService;
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
 * Role management endpoints (auth-rbac-foundation task 4.4): CRUD plus the role-menu
 * assignment, under the {@code /api/v1/system} namespace nginx routes to this service
 * (Gateway Path Convention). All endpoints stay token-guarded by the copied security
 * chain ({@code anyRequest().authenticated()} - no {@code WebSecurityCustomizer}
 * addition).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}). Mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE: the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204" - returning a value keeps the
 * "200" success code the spec's scenarios use.</p>
 *
 * @author birdbro
 */
@Api(tags = "角色管理")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:role:list')")
@RestController
@RequestMapping("/api/v1/system/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** Creates a role (duplicate live name rejected {@code A0714}); returns the new id. */
    @Log(title = "角色管理", type = "新增")
    @ApiOperation("创建角色")
    @PreAuthorize("hasAuthority('system:role:create')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PostMapping
    public Integer create(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.create(request);
    }

    /** Partial update: rename / type / remark (null fields skipped). */
    @Log(title = "角色管理", type = "修改")
    @ApiOperation("角色部分更新")
    @PreAuthorize("hasAuthority('system:role:update')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.update(id, request);
    }

    /**
     * Logical delete - cascades to the role's {@code user_role}/{@code role_menu} rows
     * (recorded decision; see {@code RoleServiceImpl}).
     */
    @Log(title = "角色管理", type = "删除")
    @ApiOperation("删除角色（级联解绑 user_role/role_menu）")
    @PreAuthorize("hasAuthority('system:role:delete')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return roleService.delete(id);
    }

    /**
     * Paged list (role-list-api, the api spec's former gap endpoint): name/type
     * filters, id-ascending rows carrying the {@code menuIds} grant echo. Sits under
     * the class-level {@code system:role:list} page gate.
     */
    @ApiOperation("角色分页列表（名称/类型过滤，行内 menuIds 授权回显）")
    @GetMapping("/list")
    public IPage<RoleItemDto> list(RoleListQuery query) {
        return roleService.page(query);
    }

    /** FULL-REPLACE menu assignment: the given ids become the role's complete grant set. */
    @Log(title = "角色管理", type = "授权")
    @ApiOperation("角色-菜单全量分配（全量替换语义）")
    @PreAuthorize("hasAuthority('system:role:assign')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PutMapping("/{id}/menus")
    public Boolean assignMenus(@PathVariable Integer id,
            @Valid @RequestBody AssignRoleMenusRequest request) {
        return roleService.assignMenus(id, request);
    }

    /**
     * Sets the lifecycle flag ({@code 1} enabled / {@code 0} disabled) - the
     * org-status-toggle mirror (role-type-dict-api D7; same mutation authority as the
     * profile update, no new seed). Store/display/toggle ONLY: no behavioral wiring.
     */
    @Log(title = "角色管理", type = "修改")
    @ApiOperation("启用/停用角色")
    @PreAuthorize("hasAuthority('system:role:update')") // button-perm pin (same role mutation authority; no new seed)
    @PutMapping("/{id}/enable")
    public Boolean setEnable(@PathVariable Integer id,
            @Valid @RequestBody SetRoleEnableRequest request) {
        return roleService.setEnable(id, request.getEnable());
    }
}

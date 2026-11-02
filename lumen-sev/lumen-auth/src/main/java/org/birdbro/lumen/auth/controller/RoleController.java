package org.birdbro.lumen.auth.controller;

import javax.validation.Valid;

import org.birdbro.lumen.auth.dto.AssignRoleMenusRequest;
import org.birdbro.lumen.auth.dto.CreateRoleRequest;
import org.birdbro.lumen.auth.dto.UpdateRoleRequest;
import org.birdbro.lumen.auth.service.RoleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role management endpoints (auth-rbac-foundation task 4.4): CRUD plus the role-menu
 * assignment, under the {@code /api/v1/auth} namespace nginx routes to this service
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
@RestController
@RequestMapping("/api/v1/auth/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** Creates a role (duplicate live name rejected {@code A0714}); returns the new id. */
    @PostMapping
    public Integer create(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.create(request);
    }

    /** Partial update: rename / type / remark (null fields skipped). */
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.update(id, request);
    }

    /**
     * Logical delete - cascades to the role's {@code user_role}/{@code role_menu} rows
     * (recorded decision; see {@code RoleServiceImpl}).
     */
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return roleService.delete(id);
    }

    /** FULL-REPLACE menu assignment: the given ids become the role's complete grant set. */
    @PutMapping("/{id}/menus")
    public Boolean assignMenus(@PathVariable Integer id,
            @Valid @RequestBody AssignRoleMenusRequest request) {
        return roleService.assignMenus(id, request);
    }
}

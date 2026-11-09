package org.birdbro.lumen.system.controller;

import java.util.List;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.birdbro.common.core.pojo.user.MenuTree;
import org.birdbro.lumen.system.dto.CreateMenuRequest;
import org.birdbro.lumen.system.dto.UpdateMenuRequest;
import org.birdbro.lumen.system.service.MenuService;
import org.birdbro.lumen.system.service.support.MenuTreeService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Menu endpoints (auth-rbac-foundation task 4.4; management family added by
 * menu-management-api): the full menu tree (the {@code menu_info} upId tree assembled
 * into the copied {@code MenuTree} contract by the Batch 2 {@code MenuTreeService}) for
 * administration screens, plus the node lifecycle family (create / full-field update /
 * guarded delete) served by {@code MenuService} (renamed from MenuManageService by
 * role-list-api - the role_menu binding writes moved to the RoleService family). The login/me flows use the
 * USER-SCOPED variant ({@code AuthServiceImpl#menuTree}); the tree endpoint is the
 * unscoped whole tree. Token-guarded by the copied security chain (no
 * {@code WebSecurityCustomizer} addition); the copied {@code GlobalResponseAdvice}
 * wraps results into the standard {@code code:"200"} envelope, and the class-level
 * {@code system:menu:list} gate covers the whole family (the established role/user/org
 * pattern - no button-level backend enforcement).
 *
 * @author birdbro
 */
@Api(tags = "菜单管理")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:menu:list')")
@RestController
@RequestMapping("/api/v1/system/menus")
public class MenuController {

    private final MenuTreeService menuTreeService;

    /**
     * The management family's service (role-list-api renamed MenuManageService to
     * MenuService; the field type swap is that sanctioned re-point), still joined by
     * ADDITIVE DI (an {@code @Autowired} field - the documented LogController
     * precedent, lumen-sev CLAUDE.md 2026-11-05) so this constructor stays
     * byte-identical under the never-modify rule.
     */
    @Autowired
    private MenuService menuService;

    public MenuController(MenuTreeService menuTreeService) {
        this.menuTreeService = menuTreeService;
    }

    /** The full assembled menu tree (directories / menus / buttons). */
    @ApiOperation("完整菜单树（目录/菜单/按钮）")
    @GetMapping("/tree")
    public List<MenuTree> tree() {
        return menuTreeService.menuTree();
    }

    /**
     * Creates a menu node (directories / menus / buttons) and auto-grants it to the
     * admin role (explicit {@code role_menu} model, no wildcard). Structural guards
     * (live parent, button-leaf) answer the {@code A0400} envelope.
     *
     * @return the new node id
     */
    @ApiOperation("创建菜单节点（目录/菜单/按钮）")
    @PreAuthorize("hasAuthority('system:menu:create')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PostMapping
    public Integer create(@Valid @RequestBody CreateMenuRequest request) {
        return menuService.create(request);
    }

    /**
     * Full-field update including reparenting ({@code upId}); cycle and button-leaf
     * guards answer the {@code A0400} envelope. Grants are untouched.
     */
    @ApiOperation("更新菜单节点（含移动）")
    @PreAuthorize("hasAuthority('system:menu:update')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateMenuRequest request) {
        return menuService.update(id, request);
    }

    /**
     * Guarded delete: live children reject with {@code A0400}; a childless node
     * soft-deletes together with its {@code role_menu} bindings.
     */
    @ApiOperation("删除菜单节点（有下级时拒绝）")
    @PreAuthorize("hasAuthority('system:menu:delete')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return menuService.delete(id);
    }
}

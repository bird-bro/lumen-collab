package org.birdbro.lumen.auth.controller;

import java.util.List;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.birdbro.common.core.pojo.user.MenuTree;
import org.birdbro.lumen.auth.service.MenuTreeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Menu query endpoints (auth-rbac-foundation task 4.4): the full menu tree (the
 * {@code menu_info} upId tree assembled into the copied {@code MenuTree} contract by
 * the Batch 2 {@code MenuTreeService}) for administration screens. The login/me flows
 * use the USER-SCOPED variant ({@code AuthServiceImpl#menuTree}); this endpoint is the
 * unscoped whole tree. Token-guarded by the copied security chain (no
 * {@code WebSecurityCustomizer} addition); the copied {@code GlobalResponseAdvice}
 * wraps the tree into the standard {@code code:"200"} envelope.
 *
 * @author birdbro
 */
@Api(tags = "菜单管理")
@RestController
@RequestMapping("/api/v1/auth/menus")
public class MenuController {

    private final MenuTreeService menuTreeService;

    public MenuController(MenuTreeService menuTreeService) {
        this.menuTreeService = menuTreeService;
    }

    /** The full assembled menu tree (directories / menus / buttons). */
    @ApiOperation("完整菜单树（目录/菜单/按钮）")
    @GetMapping("/tree")
    public List<MenuTree> tree() {
        return menuTreeService.menuTree();
    }
}

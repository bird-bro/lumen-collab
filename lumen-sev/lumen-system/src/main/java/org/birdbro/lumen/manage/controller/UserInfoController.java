package org.birdbro.lumen.manage.controller;

import java.util.List;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.manage.dto.CreateUserRequest;
import org.birdbro.lumen.manage.dto.ResetPasswordRequest;
import org.birdbro.lumen.manage.dto.SetEnableRequest;
import org.birdbro.lumen.manage.dto.UpdateUserRequest;
import org.birdbro.lumen.manage.dto.UserDetailDto;
import org.birdbro.lumen.manage.dto.UserItemDto;
import org.birdbro.lumen.manage.service.UserInfoService;
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
 * User management endpoints (auth-rbac-foundation task 4.3, extending the Batch 3
 * minimal create into the full endpoint set). Kept separate from the token-loop
 * {@code AuthController} - this is resource management, not authentication - under the
 * same {@code /api/v1/system} namespace nginx routes to this service; the Batch 4
 * org/department/role controllers follow the same per-resource layout. All endpoints
 * stay token-guarded by the copied security chain ({@code anyRequest().authenticated()}
 * - no {@code WebSecurityCustomizer} addition).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}). Mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE: the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204" - returning a value keeps the
 * "200" success code the spec's scenarios use.</p>
 *
 * @author birdbro
 */
@Api(tags = "用户管理")
// Page-level server-side gate (server-side-perm-enforcement): the whole
// family requires the page perm string; login/me and city stay ungated.
@PreAuthorize("hasAuthority('system:user:list')")
@RestController
@RequestMapping("/api/v1/system/users")
public class UserInfoController {

    private final UserInfoService userInfoService;

    public UserInfoController(UserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    /**
     * Creates a user (duplicate username rejected with the {@code A0111} envelope;
     * audit identity proven by the task 3.3 test against the real DB); returns the new
     * id.
     */
    @Log(title = "用户管理", type = "新增")
    @ApiOperation("创建用户（BCrypt 散列 + 可选角色绑定）")
    @PreAuthorize("hasAuthority('system:user:create')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PostMapping
    public Integer create(@Valid @RequestBody CreateUserRequest request) {
        return userInfoService.createUser(request);
    }

    /** Lists every live user as {@link UserItemDto} items - the hash never serializes. */
    @ApiOperation("用户列表")
    @GetMapping("/list")
    public List<UserItemDto> list() {
        return userInfoService.listUsers();
    }

    /**
     * Lists every live user with the profile payload (user-edit-delete-profile-api task
     * 2.1): the V26 profile columns plus the batch-resolved roleNames/depName. The
     * richer read the console page consumes; the plain {@link #list()} stays for the
     * existing consumers (never-modify).
     */
    @ApiOperation("用户列表（含档案与角色/科室名）")
    @GetMapping("/list/profile")
    public List<UserItemDto> listWithProfile() {
        return userInfoService.listUsersWithProfile();
    }

    /** Returns one user's full profile detail - the edit dialog's echo (task 2.2). */
    @ApiOperation("用户档案详情")
    @GetMapping("/{id}")
    public UserDetailDto detail(@PathVariable Integer id) {
        return userInfoService.getUserDetail(id);
    }

    /** Partial-updates the user's profile (task 3.2); roleIds present = full-replace. */
    @Log(title = "用户管理", type = "修改")
    @ApiOperation("编辑用户档案（roleIds 全量替换绑定）")
    @PreAuthorize("hasAuthority('system:user:update')") // button-perm pin (V26 F node, the V15/V21 discipline)
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateUserRequest request) {
        return userInfoService.updateUser(id, request);
    }

    /**
     * Logically deletes the user and unbinds its roles (task 3.3); self and the seed
     * admin reject A-class service-side (the 双保护 ruling).
     */
    @Log(title = "用户管理", type = "删除")
    @ApiOperation("删除用户（逻辑删除 + 角色解绑）")
    @PreAuthorize("hasAuthority('system:user:delete')") // button-perm pin (V26 F node, the V15/V21 discipline)
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return userInfoService.deleteUser(id);
    }

    /** Admin password reset: BCrypt re-encode + {@code must_change_password = 1}. */
    @Log(title = "用户管理", type = "重置密码")
    @ApiOperation("管理员重置密码（强制首次登录改密）")
    @PreAuthorize("hasAuthority('system:user:reset-pwd')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PutMapping("/{id}/password")
    public Boolean resetPassword(@PathVariable Integer id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return userInfoService.resetPassword(id, request.getNewPassword());
    }

    /** Sets the enable flag ({@code 1} active / {@code 0} disabled). */
    @Log(title = "用户管理", type = "修改")
    @ApiOperation("启用/停用用户")
    @PreAuthorize("hasAuthority('system:user:enable')") // button-perm pin (D2: the method annotation replaces the class page gate for this action)
    @PutMapping("/{id}/enable")
    public Boolean setEnable(@PathVariable Integer id, @Valid @RequestBody SetEnableRequest request) {
        return userInfoService.setEnable(id, request.getEnable());
    }

}

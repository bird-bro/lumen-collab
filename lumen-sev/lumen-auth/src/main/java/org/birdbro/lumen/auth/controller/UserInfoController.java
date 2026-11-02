package org.birdbro.lumen.auth.controller;

import javax.validation.Valid;

import org.birdbro.lumen.auth.dto.CreateUserRequest;
import org.birdbro.lumen.auth.service.UserInfoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User management endpoints (auth-rbac-foundation task 3.3). Kept separate from the
 * token-loop {@code AuthController} - this is resource management, not authentication -
 * under the same {@code /api/v1/auth} namespace nginx routes to this service; the Batch
 * 4 department/unit/role CRUD follows the same per-resource controller layout. Only the
 * create operation exists at this stage (full CRUD is task 4.3).
 *
 * @author birdbro
 */
@RestController
@RequestMapping("/api/v1/auth/users")
public class UserInfoController {

    private final UserInfoService userInfoService;

    public UserInfoController(UserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    /** Creates a user (audit identity proven by the task 3.3 test against the real DB). */
    @PostMapping
    public Integer create(@Valid @RequestBody CreateUserRequest request) {
        return userInfoService.createUser(request);
    }
}

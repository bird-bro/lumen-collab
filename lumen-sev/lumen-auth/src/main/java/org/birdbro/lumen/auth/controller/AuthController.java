package org.birdbro.lumen.auth.controller;

import javax.validation.Valid;

import org.birdbro.lumen.auth.dto.LoginRequest;
import org.birdbro.lumen.auth.dto.LoginResponse;
import org.birdbro.lumen.auth.dto.MeResponse;
import org.birdbro.lumen.auth.service.AuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The login loop endpoints (auth-rbac-foundation tasks 3.1-3.2): the platform's FIRST
 * real REST endpoints. {@code POST /api/v1/auth/login} issues the JWT through the copied
 * {@code TokenService}; {@code GET /api/v1/auth/me} answers for the JWT-authenticated
 * principal. The copied {@code GlobalResponseAdvice} wraps the DTO returns into the
 * standard {@code code:"200"} envelope; the copied {@code AuthenticationEntryPointImpl}
 * rejects tokenless requests to /me with the "203" envelope before any controller code
 * runs. Anonymous access for /login comes from the lumen-side
 * {@code AuthWebSecurityConfig} {@code WebSecurityCustomizer} (the copied
 * {@code SecurityConfig} permitAll list is verbatim machinery and cannot be extended).
 *
 * @author birdbro
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Issues the JWT for valid credentials (envelope carries token + profile + menus +
     * perms); A-class envelopes A0201/A0202/A0210 for unknown/disabled/wrong-password.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }

    /** Profile + menu tree + perms set of the JWT-authenticated principal. */
    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }
}

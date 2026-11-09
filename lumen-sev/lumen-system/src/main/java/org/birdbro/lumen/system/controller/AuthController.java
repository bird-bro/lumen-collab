package org.birdbro.lumen.system.controller;

import javax.validation.Valid;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.lumen.system.dto.AuthKeyResponse;
import org.birdbro.lumen.system.dto.LoginRequest;
import org.birdbro.lumen.system.dto.LoginResponse;
import org.birdbro.lumen.system.dto.MeResponse;
import org.birdbro.lumen.system.service.AuthService;
import org.birdbro.lumen.system.service.support.PasswordCipherHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The login loop endpoints (auth-rbac-foundation tasks 3.1-3.2): the platform's FIRST
 * real REST endpoints. {@code POST /api/v1/system/login} issues the JWT through the copied
 * {@code TokenService}; {@code GET /api/v1/system/me} answers for the JWT-authenticated
 * principal. The copied {@code GlobalResponseAdvice} wraps the DTO returns into the
 * standard {@code code:"200"} envelope; the copied {@code AuthenticationEntryPointImpl}
 * rejects tokenless requests to /me with the "203" envelope before any controller code
 * runs. Anonymous access for /login comes from the framework AnonymousPathCustomizer via
 * the service-declared security.permit-all config (lumen adaptation (6); the copied
 * SecurityConfig permitAll list stays verbatim).
 *
 * @author birdbro
 */
@Api(tags = "登录认证")
@RestController
@RequestMapping("/api/v1/system")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Additive DI (never-modify rule; documented precedent {@code LogController}): the
     * existing constructor stays byte-identical, the password-crypto helper joins as a
     * field (password-transport-encryption-api task 2.3).
     */
    @Autowired
    private PasswordCipherHelper passwordCipherHelper;

    /**
     * Issues the JWT for valid credentials (envelope carries token + profile + menus +
     * perms); A-class envelopes A0201/A0202/A0210 for unknown/disabled/wrong-password.
     */
    @ApiOperation("登录：凭据换 JWT + profile + 菜单 + 权限")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }

    /**
     * The SM2 key distribution for password transport encryption
     * (password-transport-encryption-api task 2.3, coordination D3): ANONYMOUS (the
     * login page fetches the key before it can produce a login body; the permit-all
     * entry lives beside /login in the committed application.yml). Returns the
     * configured key id and the DERIVED public point - the private key never leaves
     * the server.
     */
    @ApiOperation("密码加密公钥分发（匿名）：keyId + SM2 公钥点")
    @GetMapping("/auth-key")
    public AuthKeyResponse authKey() {
        return AuthKeyResponse.builder()
                .keyId(passwordCipherHelper.getKeyId())
                .publicKey(passwordCipherHelper.getPublicKeyHex())
                .build();
    }

    /** Profile + menu tree + perms set of the JWT-authenticated principal. */
    @ApiOperation("当前用户信息（profile + 菜单树 + 权限集）")
    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }
}

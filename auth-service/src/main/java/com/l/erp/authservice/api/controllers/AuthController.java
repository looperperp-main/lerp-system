package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.*;
import com.l.erp.authservice.infra.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login administrativo (APP_OWNER / TENANT_OWNER)
     * Usado no módulo admin para gerenciamento do sistema
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    /**
     * Login de parceiros (portal de parceiros)
     * Apenas email + senha — não requer CNPJ
     */
    @PostMapping("/partner/login")
    public ResponseEntity<LoginResponse> loginPartner(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginPartner(request.email(), request.password()));
    }

    /**
     * Login de usuários de tenant (Sistema Principal)
     * Requer CNPJ da empresa + e-mail + senha
     */
    @PostMapping("/tenant/login")
    public ResponseEntity<TenantLoginResponse> loginTenant(@Valid @RequestBody TenantLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithTenant(
                request.cnpj(),
                request.email(),
                request.password()
        ));
    }

    @PostMapping("/criar-conta")
    public ResponseEntity<?> criarContaGratis(@RequestBody @Valid CriarContaGratisRequest request) {
        Optional<TenantLoginResponse> result = authService.criarContaGratis(request);
        if (result.isEmpty()) {
            return ResponseEntity.ok(Map.of("message",
                    "Se os dados forem válidos, você receberá um e-mail em breve."));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.get());
    }

    @PostMapping("/ativar")
    public ResponseEntity<Map<String, String>> ativarConta(@RequestBody @Valid AtivarContaRequest request) {
        authService.ativarConta(request);
        return ResponseEntity.ok(Map.of("message", "Conta ativada com sucesso"));
    }

    /**
     * Refresh token
     * @param request token a ser atualizado
     * @return RefreshResponse
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * Loguout do sistema
     * @param request Objeto de Requisição de logout
     * @return Void
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}

package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.EsqueciSenhaTenantRequest;
import com.l.erp.authservice.api.dto.RedefinirSenhaRequest;
import com.l.erp.authservice.services.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Esqueci minha senha (portal do tenant). Rotas públicas (sem JWT).
 */
@RestController
@RequestMapping("/auth")
public class PasswordResetController {

    private static final Map<String, String> GENERIC_OK =
            Map.of("message", "Se os dados forem válidos, você receberá um e-mail com as instruções.");

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** Solicita o reset. Resposta sempre 200 genérica (anti-enumeração). */
    @PostMapping("/tenant/esqueci-senha")
    public ResponseEntity<Map<String, String>> esqueciSenhaTenant(@RequestBody @Valid EsqueciSenhaTenantRequest request) {
        passwordResetService.solicitarResetTenant(request.email(), request.cnpj());
        return ResponseEntity.ok(GENERIC_OK);
    }

    /** Redefine a senha a partir do token (compartilhado tenant/parceiro). */
    @PostMapping("/redefinir-senha")
    public ResponseEntity<Map<String, String>> redefinirSenha(@RequestBody @Valid RedefinirSenhaRequest request) {
        passwordResetService.redefinirSenha(request.token(), request.novaSenha(), request.confirmacaoSenha());
        return ResponseEntity.ok(Map.of("message", "Senha redefinida com sucesso."));
    }
}
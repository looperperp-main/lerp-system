package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.EstabelecimentoProprioResponseDTO;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.services.EstabelecimentoService;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCurrentTenantId;

/**
 * Endpoint tenant-wide (fora do path /pessoas/{pessoaId}) pro operacoes-service achar o
 * estabelecimento emitente do tenant no faturamento (Fase 6, spec/estabelecimentos-filiais.md §6.1).
 */
@RestController
@RequestMapping("/api/v1/estabelecimentos")
public class EstabelecimentoProprioController {

    private final EstabelecimentoService estabelecimentoService;

    public EstabelecimentoProprioController(EstabelecimentoService estabelecimentoService) {
        this.estabelecimentoService = estabelecimentoService;
    }

    @GetMapping("/proprio")
    public ResponseEntity<EstabelecimentoProprioResponseDTO> buscarProprio() {
        Long tenantId = getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        Estabelecimento estabelecimento = estabelecimentoService.buscarProprio(tenantId);
        return ResponseEntity.ok(new EstabelecimentoProprioResponseDTO(estabelecimento.getPessoa().getId()));
    }
}

package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.EstoqueConfigRefDTO;
import com.l.erp.cadastroservice.domain.ProdutoEstoqueConfig;
import com.l.erp.cadastroservice.repository.ProdutoEstoqueConfigRepository;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.l.erp.cadastroservice.util.SecurityUtils.getCurrentTenantId;

/**
 * Endpoint interno pro operacoes-service ler {@code estoque_minimo} em lote (spec/estoque.md
 * §5.1/E6 — badge "abaixo do mínimo" do GET /saldos). Protegido pelo {@code InternalRequestFilter}
 * como qualquer outro endpoint do cadastro-service (segredo interno + X-User-Id), sem RBAC próprio
 * — é leitura pura, não há ação a autorizar.
 */
@RestController
@RequestMapping("/api/v1/interno/estoque-config")
public class EstoqueConfigInternoController {

    private final ProdutoEstoqueConfigRepository repository;

    public EstoqueConfigInternoController(ProdutoEstoqueConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<EstoqueConfigRefDTO>> buscar(
            @RequestParam List<UUID> produtoIds,
            @RequestParam UUID depositoId) {
        Long tenantId = getCurrentTenantId()
                .orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        List<ProdutoEstoqueConfig> configs = repository.buscarPorProdutosEDeposito(tenantId, depositoId, produtoIds);
        List<EstoqueConfigRefDTO> resposta = configs.stream()
                .map(c -> new EstoqueConfigRefDTO(c.getProduto().getId(), c.getEstoqueMinimo()))
                .toList();
        return ResponseEntity.ok(resposta);
    }
}

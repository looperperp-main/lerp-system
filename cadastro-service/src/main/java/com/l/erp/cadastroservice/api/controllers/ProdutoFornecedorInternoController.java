package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.ProdutoFornecedorRefDTO;
import com.l.erp.cadastroservice.domain.ProdutoFornecedor;
import com.l.erp.cadastroservice.repository.ProdutoFornecedorRepository;
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
 * Endpoint interno (sem @PreAuthorize — protegido pelo InternalRequestFilter via X-Internal-Secret)
 * consumido pelo operacoes-service pro alerta de preço fora da faixa do P2P
 * (spec/p2p-compras.md RN-P2P-04). Mesmo padrão de EstoqueConfigInternoController.
 */
@RestController
@RequestMapping("/api/v1/interno/produto-fornecedor")
public class ProdutoFornecedorInternoController {

    private final ProdutoFornecedorRepository repository;

    public ProdutoFornecedorInternoController(ProdutoFornecedorRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<ProdutoFornecedorRefDTO>> buscar(
            @RequestParam List<UUID> produtoIds,
            @RequestParam UUID fornecedorId) {
        Long tenantId = getCurrentTenantId()
                .orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        List<ProdutoFornecedor> vinculos = repository.buscarPorProdutosEFornecedor(tenantId, fornecedorId, produtoIds);
        List<ProdutoFornecedorRefDTO> resposta = vinculos.stream()
                .map(v -> new ProdutoFornecedorRefDTO(v.getProduto().getId(), v.getPrecoCusto()))
                .toList();
        return ResponseEntity.ok(resposta);
    }
}

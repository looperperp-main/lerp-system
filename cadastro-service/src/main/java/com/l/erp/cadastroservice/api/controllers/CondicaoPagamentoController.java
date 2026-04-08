package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoDTO;
import com.l.erp.cadastroservice.api.dto.GrupoClienteDTO;
import com.l.erp.cadastroservice.services.CondicaoPagamentoService;
import com.l.erp.cadastroservice.services.GrupoClienteService;
import com.l.erp.cadastroservice.util.Constants;
import com.l.erp.cadastroservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cond-pagamentos")
@RequiredArgsConstructor
public class CondicaoPagamentoController {

    private final Logger logger = LoggerFactory.getLogger(CondicaoPagamentoController.class);

    private final CondicaoPagamentoService service;

    @GetMapping
    public ResponseEntity<Page<CondicaoPagamentoDTO>> getAllGroups(Pageable pageable) {
        logger.info("Listando Condição de Pagamentos");
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        logger.info("Tenant ID: {}", tenantId);
        return ResponseEntity.ok(service.getAllConditions(tenantId.orElseThrow(() -> new RuntimeException("Tenant Id não encontrado!")), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CondicaoPagamentoDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Condição por ID: {}", id);
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(service.findById(id, tenantId.orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO))));
    }

    @PostMapping
    public ResponseEntity<CondicaoPagamentoDTO> save(@RequestBody @Valid CondicaoPagamentoDTO dto) {
        logger.info("Criando Condicao de Pagamento: {}", dto);

        Long tenantID = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        CondicaoPagamentoDTO salvo = service.save(dto, tenantID, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CondicaoPagamentoDTO> update(@PathVariable UUID id, @RequestBody @Valid CondicaoPagamentoDTO dto) {
        logger.info("Atualizando Condição de Pagamento: {}", dto);

        Long tenantID = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        CondicaoPagamentoDTO salvo = service.update(id, dto, tenantID, userId);
        return ResponseEntity.ok(salvo);
    }

}

package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.DepositoDTO;
import com.l.erp.cadastroservice.services.DepositoService;
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
@RequestMapping("/api/v1/depositos")
@RequiredArgsConstructor
public class DepositoController {

    private final Logger logger = LoggerFactory.getLogger(DepositoController.class);

    private final DepositoService service;

    @GetMapping
    public ResponseEntity<Page<DepositoDTO>> findAllDepositos(Pageable pageable) {
        logger.info("Listando Depositos");
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        logger.info("Tenant ID: {}", tenantId);
        return ResponseEntity.ok(service.findAllDepositos(tenantId.orElseThrow(() -> new RuntimeException("Tenant Id não encontrado!")), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepositoDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Deposito por ID: {}", id);
        Optional<Long> tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(service.findById(id, tenantId.orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO))));
    }

    @PostMapping
    public ResponseEntity<DepositoDTO> save(@RequestBody @Valid DepositoDTO dto) {
        logger.info("Criando Deposito: {}", dto);

        Long tenantID = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        DepositoDTO salvo = service.save(dto, tenantID, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepositoDTO> update(@PathVariable UUID id, @RequestBody @Valid DepositoDTO dto) {
        logger.info("Atualizando grupo de cliente: {}", dto);

        Long tenantID = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        DepositoDTO salvo = service.update(id, dto, tenantID, userId);
        return ResponseEntity.ok(salvo);
    }

}

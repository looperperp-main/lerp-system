package com.l.erp.fiscalservice.api.controllers;

import com.l.erp.common.util.Constants;
import com.l.erp.fiscalservice.api.dto.MotorFiscalRequest;
import com.l.erp.fiscalservice.api.dto.OperacaoFiscalDTO;
import com.l.erp.fiscalservice.services.MotorFiscalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint do motor fiscal (Fatia B). Cálculo de SAÍDA por operação, sem persistência.
 *
 * <p>A barreira de autenticação é o gateway (valida o JWT e injeta os headers X-*); este
 * serviço confia neles, como os demais downstream. Erros de cálculo ({@code FiscalException})
 * viram HTTP 400 pelo {@code GlobalExceptionHandler} do common.
 */
@RestController
@RequestMapping("/fiscal")
public class MotorFiscalController {

    private final MotorFiscalService motorFiscal;

    public MotorFiscalController(MotorFiscalService motorFiscal) {
        this.motorFiscal = motorFiscal;
    }

    @PostMapping("/calcular")
    public ResponseEntity<OperacaoFiscalDTO> calcular(
            @Valid @RequestBody MotorFiscalRequest req,
            @RequestHeader(name = Constants.HEADER_TENANT_ID, required = false) String tenantId) {
        return ResponseEntity.ok(motorFiscal.calcular(req, tenantId));
    }
}

package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados do pagamento no payload de webhook do Asaas (spec §8.7).
 * {@code subscription} é o id da assinatura (String) — não confundir com o objeto subscription do payload.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasPaymentData {
    private String id;
    private String customer;
    private String subscription;
    private String status;
    private BigDecimal value;
    private String billingType;
    private LocalDate dueDate;
    private LocalDate paymentDate;
}
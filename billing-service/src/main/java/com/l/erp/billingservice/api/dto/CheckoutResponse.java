package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Retorno do checkout (spec §7.2). {@code paymentUrl} é a página hospedada do Asaas (boleto + PIX),
 * {@code boletoUrl} o PDF do boleto e {@code pixQrCode}/{@code pixCopyPaste} o QR Code do PIX —
 * estes dois podem vir nulos se o Asaas ainda não tiver gerado o PIX no momento da resposta.
 */
public record CheckoutResponse(
        String paymentUrl,
        String boletoUrl,
        String pixQrCode,
        String pixCopyPaste,
        LocalDate dueDate,
        String planType,
        String planName,
        BigDecimal value
) {}
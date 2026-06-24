package com.l.erp.billingservice.services;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.repository.CommissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de comissão (extrato do parceiro). A <b>geração</b> da comissão fica no
 * {@code services.commission.CommissionEngine} (trilha Kafka {@code partner.commission.calculated}).
 *
 * <p>O <b>repasse</b> (payout PIX) é da Fase 6 — {@link #processarRepasses()} está desabilitado para não
 * falsificar pagamentos: o stub {@code AsaasClient.transferPix} não move dinheiro. Ver auditoria §2.1.</p>
 */
@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private final CommissionRepository commissionRepository;

    public CommissionService(CommissionRepository commissionRepository) {
        this.commissionRepository = commissionRepository;
    }

    /**
     * Repasse de comissões — DESABILITADO até a Fase 6 (payout PIX real via {@code AsaasTransferClient}).
     * O {@code @Scheduled} foi removido de propósito: a versão anterior marcava {@code PAGO} usando um stub
     * que não transfere, falsificando o repasse no banco.
     */
    public void processarRepasses() {
        log.warn("processarRepasses() desabilitado — payout PIX real só na Fase 6. Nenhuma comissão alterada.");
    }

    @Transactional(readOnly = true)
    public List<Commission> findByPartner(UUID partnerId) {
        return commissionRepository.findByPartnerIdOrderByCalculatedAtDesc(partnerId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getComissaoMesAtual(UUID partnerId) {
        return commissionRepository.sumPendenteByPartnerAndPeriod(partnerId, YearMonth.now().toString());
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalPago(UUID partnerId) {
        return commissionRepository.sumPagoByPartner(partnerId);
    }
}
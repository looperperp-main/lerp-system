package com.l.erp.billingservice.services;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.services.payout.CommissionPayoutService;
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
 * <p>O <b>repasse</b> (payout PIX, Fase 6) é orquestrado pelo {@code CommissionPayoutJob}/{@code
 * CommissionPayoutService}; aqui só fica o trigger manual ({@link #processarRepasses()}) para dev/teste.</p>
 */
@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private final CommissionRepository commissionRepository;
    private final CommissionPayoutService payoutService;

    public CommissionService(CommissionRepository commissionRepository,
                             CommissionPayoutService payoutService) {
        this.commissionRepository = commissionRepository;
        this.payoutService = payoutService;
    }

    /**
     * Trigger manual de repasse (dev/teste). Processa as comissões PENDENTE do <b>período atual</b>
     * — diferente do {@code CommissionPayoutJob}, que roda D+1 sobre o mês anterior. Payout real via
     * {@code AsaasTransferClient} (Fase 6); comissões vão a EM_TRANSFERENCIA até o webhook TRANSFER_COMPLETED.
     */
    public void processarRepasses() {
        YearMonth period = YearMonth.now();
        log.info("Trigger manual de repasse — período {}", period);
        payoutService.processPayouts(period);
    }

    @Transactional(readOnly = true)
    public List<Commission> findByPartner(UUID partnerId) {
        return commissionRepository.findByPartnerIdOrderByCalculatedAtDesc(partnerId);
    }

    /** Listagem admin (tela Pagamentos) — todas as comissões paginadas. */
    public org.springframework.data.domain.Page<Commission> listAll(org.springframework.data.domain.Pageable pageable) {
        return commissionRepository.findAll(pageable);
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
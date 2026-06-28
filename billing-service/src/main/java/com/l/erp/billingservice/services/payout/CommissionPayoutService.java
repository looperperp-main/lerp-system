package com.l.erp.billingservice.services.payout;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.partner.PartnerPayoutInfo;
import com.l.erp.billingservice.infra.partner.PartnerPayoutReader;
import com.l.erp.billingservice.repository.CommissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repasse de comissões PENDENTE por parceiro (Fase 6 — spec §10.2). Um PIX agregado por parceiro
 * por período. As comissões vão para {@code EM_TRANSFERENCIA} no disparo; só viram {@code PAGO} no
 * webhook {@code TRANSFER_COMPLETED}.
 *
 * <p>Sem {@code @Transactional} no método público: o HTTP do transfer acontece FORA de transação
 * (não segura conexão do pool). A atualização das comissões roda numa transação curta
 * ({@link TransactionTemplate}) imediatamente após o transfer ser confirmado. Falha de um parceiro
 * não bloqueia os outros.</p>
 */
@Service
public class CommissionPayoutService {

    private static final Logger log = LoggerFactory.getLogger(CommissionPayoutService.class);

    private final CommissionRepository commissionRepository;
    private final PartnerPayoutReader partnerPayoutReader;
    private final AsaasGateway asaasGateway;
    private final TransactionTemplate txTemplate;

    public CommissionPayoutService(CommissionRepository commissionRepository,
                                   PartnerPayoutReader partnerPayoutReader,
                                   AsaasGateway asaasGateway,
                                   PlatformTransactionManager txManager) {
        this.commissionRepository = commissionRepository;
        this.partnerPayoutReader = partnerPayoutReader;
        this.asaasGateway = asaasGateway;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public void processPayouts(YearMonth period) {
        String p = period.toString();
        Map<UUID, List<Commission>> byPartner = commissionRepository
                .findByStatusAndPeriod(CommissionStatus.PENDENTE, p)
                .stream()
                .collect(Collectors.groupingBy(Commission::getPartnerId));

        if (byPartner.isEmpty()) {
            log.info("Nenhuma comissão PENDENTE no período {} — nada a repassar", p);
            return;
        }

        byPartner.forEach((partnerId, commissions) -> {
            try {
                processPartnerPayout(partnerId, commissions, period);
            } catch (Exception e) {
                // Falha de um parceiro (ex.: chave PIX inválida → 4xx) não bloqueia os outros
                log.error("Falha no repasse — parceiro={} período={}", partnerId, p, e);
            }
        });
    }

    private void processPartnerPayout(UUID partnerId, List<Commission> commissions, YearMonth period) {
        PartnerPayoutInfo info = partnerPayoutReader.find(partnerId).orElse(null);
        if (info == null || info.pixKey() == null || info.pixKey().isBlank()) {
            log.warn("Parceiro {} sem chave PIX — repasse adiado (continua PENDENTE)", partnerId);
            return;
        }

        BigDecimal total = commissions.stream()
                .map(Commission::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Total zerado para parceiro={} — nenhum repasse", partnerId);
            return;
        }

        String externalRef = "payout-" + partnerId + "-" + period;
        String description = "Comissão Syax — Referência " + period + " — Parceiro " + partnerId;

        String transferId = asaasGateway.createPixTransfer(
                total, info.pixKey(), info.pixKeyType(), description, externalRef);

        if (transferId == null) {
            log.error("Transfer não criado/confirmado — parceiro={} período={} — reprocessa no próximo ciclo",
                    partnerId, period);
            return;
        }

        markAsInTransfer(commissions, transferId);
        log.info("Transfer criado (PENDING) — parceiro={} total={} transferId={} ({} comissões)",
                partnerId, total, transferId, commissions.size());
    }

    private void markAsInTransfer(List<Commission> commissions, String transferId) {
        txTemplate.executeWithoutResult(tx -> {
            OffsetDateTime now = OffsetDateTime.now();
            commissions.forEach(c -> {
                c.setStatus(CommissionStatus.EM_TRANSFERENCIA);
                c.setAsaasTransferId(transferId);
            });
            commissionRepository.saveAll(commissions);
            log.debug("{} comissões marcadas EM_TRANSFERENCIA em {} transferId={}",
                    commissions.size(), now, transferId);
        });
    }
}

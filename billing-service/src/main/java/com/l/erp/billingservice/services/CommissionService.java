package com.l.erp.billingservice.services;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.infra.asaas.AsaasClient;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private final CommissionRepository commissionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AsaasClient asaasClient;

    public CommissionService(CommissionRepository commissionRepository,
                              SubscriptionRepository subscriptionRepository,
                              AsaasClient asaasClient) {
        this.commissionRepository = commissionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.asaasClient = asaasClient;
    }

    @Transactional
    public void createCommission(UUID partnerId, Long tenantId, String asaasSubscriptionId,
                                  BigDecimal amount, String asaasPaymentId) {
        if (asaasPaymentId != null && !asaasPaymentId.isBlank()
                && commissionRepository.existsByAsaasPaymentId(asaasPaymentId)) {
            log.info("Commission para asaasPaymentId={} já existe — idempotência, ignorando", asaasPaymentId);
            return;
        }

        Subscription sub = subscriptionRepository.findByAsaasSubscriptionId(asaasSubscriptionId).orElse(null);
        if (sub == null) {
            log.warn("Subscription asaasSubscriptionId={} não encontrada ao criar comissão", asaasSubscriptionId);
            return;
        }

        String period = YearMonth.now().toString(); // YYYY-MM

        Commission commission = new Commission();
        commission.setPartnerId(partnerId);
        commission.setTenantId(tenantId);
        commission.setSubscription(sub);
        commission.setAmount(amount);
        commission.setPeriod(period);
        commission.setStatus("PENDENTE");
        commission.setAsaasPaymentId(asaasPaymentId);
        commission.setCalculatedAt(OffsetDateTime.now());

        commissionRepository.save(commission);
        log.info("Commission criada: partnerId={} tenantId={} amount={} period={}", partnerId, tenantId, amount, period);
    }

    // Roda no dia 2 de cada mês às 08:00 — processa repasses do mês anterior
    @Scheduled(cron = "0 0 8 2 * *")
    @Transactional
    public void processarRepasses() {
        String periodoAnterior = YearMonth.now().minusMonths(1).toString();
        log.info("Iniciando processamento de repasses para período={}", periodoAnterior);

        List<Commission> pendentes = commissionRepository.findByStatusAndPeriod("PENDENTE", periodoAnterior);
        if (pendentes.isEmpty()) {
            log.info("Nenhuma comissão PENDENTE para período={}", periodoAnterior);
            return;
        }

        Map<UUID, List<Commission>> porParceiro = pendentes.stream()
                .collect(Collectors.groupingBy(Commission::getPartnerId));

        log.info("Processando repasses para {} parceiros, total={} comissões", porParceiro.size(), pendentes.size());

        for (Map.Entry<UUID, List<Commission>> entry : porParceiro.entrySet()) {
            try {
                processarRepasseParceiro(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Falha ao processar repasse para parceiro={}", entry.getKey(), e);
            }
        }
    }

    private void processarRepasseParceiro(UUID partnerId, List<Commission> comissoes) {
        BigDecimal total = comissoes.stream()
                .map(Commission::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Processando repasse parceiro={} total={} comissões={}", partnerId, total, comissoes.size());

        try {
            String transferId = asaasClient.transferPix(partnerId, total);
            OffsetDateTime now = OffsetDateTime.now();
            for (Commission c : comissoes) {
                c.setStatus("PAGO");
                c.setAsaasTransferId(transferId);
                c.setPaidAt(now);
                commissionRepository.save(c);
            }
            log.info("Repasse confirmado: parceiro={} total={} transferId={}", partnerId, total, transferId);
        } catch (Exception e) {
            log.error("Falha na transferência Asaas para parceiro={} total={}", partnerId, total, e);
        }
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
package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.AssinaturaResumoDTO;
import com.l.erp.billingservice.api.dto.CancelSubscriptionResponse;
import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Cancelamento manual de assinatura (onboarding §6/§7). O cliente para a renovação no Asaas, mas
 * mantém o acesso até {@code next_due_date} (mensal: aviso prévio; anual: sem reembolso). A
 * assinatura fica em {@code CANCELAMENTO_SOLICITADO} — que NÃO propaga bloqueio ao auth — e o
 * {@code DunningJob} a finaliza para {@code CANCELADO} quando {@code next_due_date} vence.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final List<String> CANCELAVEIS = List.of(SubscriptionStatus.ATIVA, SubscriptionStatus.SUSPENSO);

    private final SubscriptionRepository subscriptionRepository;
    private final CommissionRepository commissionRepository;
    private final AsaasGateway asaasGateway;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                CommissionRepository commissionRepository,
                                AsaasGateway asaasGateway) {
        this.subscriptionRepository = subscriptionRepository;
        this.commissionRepository = commissionRepository;
        this.asaasGateway = asaasGateway;
    }

    /** Resumo de assinatura + última comissão paga de um tenant, para o drawer "Ver assinatura" do portal do parceiro. */
    @Transactional(readOnly = true)
    public AssinaturaResumoDTO getResumoAssinatura(Long tenantId) {
        Subscription sub = subscriptionRepository.findByTenantId(tenantId).stream()
                .findFirst()
                .orElse(null);
        if (sub == null) {
            return new AssinaturaResumoDTO(null, null, null, null, null, null, null, null);
        }

        Commission ultimoPago = commissionRepository.findByTenantIdAndStatus(tenantId, "PAGO").stream()
                .max(Comparator.comparing(Commission::getPaidAt))
                .orElse(null);

        return new AssinaturaResumoDTO(
                sub.getStatus(),
                statusCobrancaLabel(sub.getStatus()),
                sub.getValue(),
                sub.getPaymentMethod(),
                sub.getActivatedAt(),
                sub.getNextDueDate(),
                ultimoPago != null ? ultimoPago.getAmount() : null,
                ultimoPago != null ? ultimoPago.getPeriod() : null);
    }

    private static String statusCobrancaLabel(String status) {
        return switch (status) {
            case SubscriptionStatus.ATIVA -> "Em dia";
            case SubscriptionStatus.AGUARDANDO_PAGAMENTO -> "Aguardando pagamento";
            case SubscriptionStatus.SUSPENSO -> "Suspenso";
            case SubscriptionStatus.CANCELAMENTO_SOLICITADO -> "Cancelamento solicitado";
            case SubscriptionStatus.CANCELADO -> "Cancelado";
            default -> status;
        };
    }

    @Transactional
    public CancelSubscriptionResponse cancelForTenant(Long tenantId) {
        List<Subscription> subs = subscriptionRepository.findByTenantId(tenantId);

        // Idempotência: já em processo de cancelamento ou cancelada → devolve o estado atual.
        Subscription jaCancelando = subs.stream()
                .filter(s -> SubscriptionStatus.CANCELAMENTO_SOLICITADO.equals(s.getStatus())
                        || SubscriptionStatus.CANCELADO.equals(s.getStatus()))
                .findFirst().orElse(null);
        if (jaCancelando != null) {
            return new CancelSubscriptionResponse(jaCancelando.getStatus(), jaCancelando.getNextDueDate());
        }

        Subscription sub = subs.stream()
                .filter(s -> CANCELAVEIS.contains(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Nenhuma assinatura ativa para cancelar"));

        if (sub.getAsaasSubscriptionId() != null) {
            asaasGateway.cancelSubscription(sub.getAsaasSubscriptionId());
        }

        sub.setStatus(SubscriptionStatus.CANCELAMENTO_SOLICITADO);
        sub.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(sub);

        log.info("Cancelamento solicitado — tenant {} acesso mantido até {}", tenantId, sub.getNextDueDate());
        return new CancelSubscriptionResponse(sub.getStatus(), sub.getNextDueDate());
    }
}

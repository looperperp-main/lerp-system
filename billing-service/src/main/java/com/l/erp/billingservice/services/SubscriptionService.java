package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.CancelSubscriptionResponse;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
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
    private final AsaasGateway asaasGateway;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, AsaasGateway asaasGateway) {
        this.subscriptionRepository = subscriptionRepository;
        this.asaasGateway = asaasGateway;
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

package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.AssinaturaResumoDTO;
import com.l.erp.billingservice.api.dto.CancelSubscriptionResponse;
import com.l.erp.billingservice.api.dto.CobrancaAdminDTO;
import com.l.erp.billingservice.api.dto.ReprocessResultDTO;
import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentData;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.kafka.KafkaBillingProducerService;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.webhook.handler.PaymentReceivedHandler;
import com.l.erp.common.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
    private final PaymentReceivedHandler paymentReceivedHandler;
    private final KafkaBillingProducerService kafkaProducer;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                CommissionRepository commissionRepository,
                                AsaasGateway asaasGateway,
                                PaymentReceivedHandler paymentReceivedHandler,
                                KafkaBillingProducerService kafkaProducer) {
        this.subscriptionRepository = subscriptionRepository;
        this.commissionRepository = commissionRepository;
        this.asaasGateway = asaasGateway;
        this.paymentReceivedHandler = paymentReceivedHandler;
        this.kafkaProducer = kafkaProducer;
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

    // =========================================================================
    // Ações do admin (portal-admin-tasks item 2)
    // =========================================================================

    /**
     * Consulta a 1ª cobrança no Asaas e, se paga, reprocessa a ativação pelo fluxo normal do
     * webhook ({@link PaymentReceivedHandler}). Usado pelo botão do admin e pela reconciliação.
     *
     * @return true se o pagamento estava confirmado e a ativação foi reprocessada
     */
    public boolean reprocessarPagamento(Subscription sub) {
        AsaasPaymentResponse payment = asaasGateway.getFirstPayment(sub.getAsaasSubscriptionId());
        if (payment == null || !Constants.ASAAS_PAID_STATUSES.contains(payment.status())) {
            return false;
        }
        log.warn("Pagamento {} ({}) confirmado no Asaas sem webhook — ativando tenant {}",
                payment.id(), payment.status(), sub.getTenantId());

        AsaasPaymentData pay = new AsaasPaymentData();
        pay.setId(payment.id());
        pay.setSubscription(sub.getAsaasSubscriptionId());
        pay.setStatus(payment.status());
        pay.setValue(payment.value());

        AsaasWebhookPayload payload = new AsaasWebhookPayload();
        payload.setEvent(Constants.ASAAS_EVENT_PAYMENT_RECEIVED);
        payload.setPayment(pay);

        paymentReceivedHandler.handle(payload);
        return true;
    }

    /** Reprocessamento manual pelo admin ({@code POST /{id}/reprocess}). Idempotente. */
    public ReprocessResultDTO reprocessar(UUID id) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura não encontrada"));

        if (SubscriptionStatus.ATIVA.equals(sub.getStatus())) {
            return new ReprocessResultDTO(false, sub.getStatus(), "Assinatura já está ativa");
        }
        if (sub.getAsaasSubscriptionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assinatura sem vínculo com o Asaas");
        }

        boolean ativou = reprocessarPagamento(sub);
        kafkaProducer.sendAuditEvent(Constants.ASSINATURA_REPROCESS, Constants.SYSTEM_ACTOR_ID,
                Constants.ASSINATURA, sub.getId(), Constants.SUCCESS,
                "{\"tenantId\":" + sub.getTenantId() + ",\"ativada\":" + ativou + "}", UUID.randomUUID());

        String statusAtual = subscriptionRepository.findById(id).map(Subscription::getStatus).orElse(sub.getStatus());
        return new ReprocessResultDTO(ativou, statusAtual,
                ativou ? "Pagamento confirmado no Asaas — ativação reprocessada"
                       : "Cobrança ainda não consta como paga no Asaas");
    }

    /** Cobranças da assinatura direto do Asaas (drill-down do admin). */
    public List<CobrancaAdminDTO> listarCobrancas(UUID id) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura não encontrada"));
        if (sub.getAsaasSubscriptionId() == null) {
            return List.of();
        }
        return asaasGateway.listPayments(sub.getAsaasSubscriptionId()).stream()
                .map(p -> new CobrancaAdminDTO(p.id(), p.status(), p.value(), p.dueDate(), p.invoiceUrl()))
                .toList();
    }

    /** Cancelamento pelo admin, por id da assinatura. Mesmas regras do self-service + auditoria. */
    @Transactional
    public CancelSubscriptionResponse cancelForAdmin(UUID id) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura não encontrada"));

        // Idempotência: já cancelando/cancelada → devolve o estado atual.
        if (SubscriptionStatus.CANCELAMENTO_SOLICITADO.equals(sub.getStatus())
                || SubscriptionStatus.CANCELADO.equals(sub.getStatus())) {
            return new CancelSubscriptionResponse(sub.getStatus(), sub.getNextDueDate());
        }
        if (!CANCELAVEIS.contains(sub.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Assinatura no status " + sub.getStatus() + " não pode ser cancelada");
        }

        if (sub.getAsaasSubscriptionId() != null) {
            asaasGateway.cancelSubscription(sub.getAsaasSubscriptionId());
        }

        sub.setStatus(SubscriptionStatus.CANCELAMENTO_SOLICITADO);
        sub.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(sub);

        kafkaProducer.sendAuditEvent(Constants.ASSINATURA_CANCEL_ADMIN, Constants.SYSTEM_ACTOR_ID,
                Constants.ASSINATURA, sub.getId(), Constants.SUCCESS,
                "{\"tenantId\":" + sub.getTenantId() + "}", UUID.randomUUID());

        log.info("Cancelamento solicitado pelo admin — subscription {} tenant {}", sub.getId(), sub.getTenantId());
        return new CancelSubscriptionResponse(sub.getStatus(), sub.getNextDueDate());
    }
}

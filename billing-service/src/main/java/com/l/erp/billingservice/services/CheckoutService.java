package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.CheckoutRequest;
import com.l.erp.billingservice.api.dto.CheckoutResponse;
import com.l.erp.billingservice.domain.Plan;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.domain.SubscriptionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasException;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.asaas.dto.AsaasCustomerRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPixQrCodeResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionResponse;
import com.l.erp.billingservice.repository.PlanRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Criação de assinatura no Asaas a partir do checkout do tenant (spec §7 — itens 19/20 da Fase 3).
 *
 * <p>É o endpoint de criação de assinatura do billing-service: cria o customer e a subscription
 * recorrente no Asaas, persiste os IDs com status {@code AGUARDANDO_PAGAMENTO} e devolve o link de
 * pagamento + boleto + QR Code PIX. A ativação acontece depois, no webhook {@code PAYMENT_RECEIVED}.</p>
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AsaasGateway asaasGateway;

    public CheckoutService(PlanRepository planRepository,
                           SubscriptionRepository subscriptionRepository,
                           AsaasGateway asaasGateway) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.asaasGateway = asaasGateway;
    }

    @Transactional
    public CheckoutResponse createCheckout(Long tenantId, CheckoutRequest req) {
        Plan plan = planRepository.findByPlanTypeAndActiveTrue(req.planType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plano '" + req.planType() + "' não encontrado ou inativo"));

        String asaasCustomerId = asaasGateway.createCustomer(
                AsaasCustomerRequest.of(req.razaoSocial(), req.cnpj(), req.email()));

        LocalDate firstDueDate = LocalDate.now().plusDays(1);
        AsaasSubscriptionResponse asaasSub = asaasGateway.createSubscription(new AsaasSubscriptionRequest(
                asaasCustomerId,
                "UNDEFINED",            // pagador escolhe boleto/PIX/cartão
                plan.getBillingCycle(),
                plan.getValue(),
                firstDueDate,
                "Plano " + plan.getName() + " — Syax",
                String.valueOf(tenantId)));

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanType(plan.getPlanType());
        subscription.setBillingCycle(plan.getBillingCycle());
        subscription.setValue(plan.getValue());
        subscription.setStatus(SubscriptionStatus.AGUARDANDO_PAGAMENTO);
        subscription.setAsaasCustomerId(asaasCustomerId);
        subscription.setAsaasSubscriptionId(asaasSub.id());
        subscription.setCreatedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        return buildResponse(plan, asaasSub.id());
    }

    private CheckoutResponse buildResponse(Plan plan, String asaasSubscriptionId) {
        AsaasPaymentResponse firstPayment = asaasGateway.getFirstPayment(asaasSubscriptionId);

        // QR Code PIX é uma chamada extra e pode não existir ainda — falha aqui não invalida o checkout
        String pixQrCode = null;
        String pixCopyPaste = null;
        try {
            AsaasPixQrCodeResponse qr = asaasGateway.getPixQrCode(firstPayment.id());
            if (qr != null) {
                pixQrCode = qr.encodedImage();
                pixCopyPaste = qr.payload();
            }
        } catch (AsaasException e) {
            log.warn("PIX QR Code indisponível para payment={} — seguindo só com boleto/link. {}",
                    firstPayment.id(), e.getMessage());
        }

        return new CheckoutResponse(
                firstPayment.invoiceUrl(),
                firstPayment.bankSlipUrl(),
                pixQrCode,
                pixCopyPaste,
                firstPayment.dueDate(),
                plan.getPlanType(),
                plan.getName(),
                plan.getValue());
    }
}
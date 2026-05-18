package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.CheckoutRequest;
import com.l.erp.billingservice.api.dto.CheckoutResponse;
import com.l.erp.billingservice.domain.Plan;
import com.l.erp.billingservice.domain.Subscription;
import com.l.erp.billingservice.infra.asaas.AsaasClient;
import com.l.erp.billingservice.repository.PlanRepository;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
public class CheckoutService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AsaasClient asaasClient;

    public CheckoutService(PlanRepository planRepository,
                           SubscriptionRepository subscriptionRepository,
                           AsaasClient asaasClient) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.asaasClient = asaasClient;
    }

    @Transactional
    public CheckoutResponse createCheckout(Long tenantId, CheckoutRequest req) {
        Plan plan = planRepository.findByPlanTypeAndActiveTrue(req.planType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plano '" + req.planType() + "' não encontrado ou inativo"));

        String asaasCustomerId = asaasClient.createCustomer(req.cnpj(), req.email(), req.razaoSocial());

        AsaasClient.AsaasSubscriptionResult sub = asaasClient.createSubscription(
                asaasCustomerId, plan.getBillingCycle(), plan.getValue(), LocalDate.now());

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanType(plan.getPlanType());
        subscription.setBillingCycle(plan.getBillingCycle());
        subscription.setValue(plan.getValue());
        subscription.setStatus("AGUARDANDO_PAGAMENTO");
        subscription.setAsaasCustomerId(asaasCustomerId);
        subscription.setAsaasSubscriptionId(sub.id());
        subscription.setCreatedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        String paymentUrl = sub.paymentLink() != null ? sub.paymentLink()
                : "https://www.asaas.com/c/" + sub.id();

        return new CheckoutResponse(paymentUrl, plan.getPlanType(), plan.getName(), plan.getValue());
    }
}
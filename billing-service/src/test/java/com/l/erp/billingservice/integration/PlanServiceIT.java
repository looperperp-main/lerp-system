package com.l.erp.billingservice.integration;

import com.l.erp.billingservice.domain.Plan;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlanServiceIT extends AbstractIntegrationTest {

    @Autowired
    PlanRepository planRepository;

    @MockitoBean
    AsaasGateway asaasGateway;

    @BeforeEach
    void clean() {
        planRepository.deleteAll();
    }

    @Test
    void shouldSaveAndRetrievePlan() {
        Plan plan = buildPlan("Plano Starter", "STARTER");
        Plan saved = planRepository.save(plan);

        Optional<Plan> found = planRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Plano Starter");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void shouldListOnlyActivePlans() {
        planRepository.save(buildPlan("Plano Ativo", "STARTER"));
        Plan inativo = buildPlan("Plano Inativo", "PRO");
        inativo.setActive(false);
        planRepository.save(inativo);

        List<Plan> ativos = planRepository.findAll().stream()
                .filter(Plan::isActive)
                .toList();

        assertThat(ativos).hasSize(1);
        assertThat(ativos.get(0).getName()).isEqualTo("Plano Ativo");
    }

    private static Plan buildPlan(String name, String type) {
        Plan p = new Plan();
        p.setName(name);
        p.setPlanType(type);
        p.setBillingCycle("MONTHLY");
        p.setValue(new BigDecimal("99.90"));
        p.setActive(true);
        p.setCreatedAt(OffsetDateTime.now());
        p.setCreatedBy("test");
        return p;
    }
}
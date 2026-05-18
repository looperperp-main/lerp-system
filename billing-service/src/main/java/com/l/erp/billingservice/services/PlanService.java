package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.PlanRequest;
import com.l.erp.billingservice.domain.Plan;
import com.l.erp.billingservice.infra.kafka.KafkaBillingProducerService;
import com.l.erp.billingservice.repository.PlanRepository;
import com.l.erp.common.util.Constants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final KafkaBillingProducerService kafkaProducer;

    public PlanService(PlanRepository planRepository, KafkaBillingProducerService kafkaProducer) {
        this.planRepository = planRepository;
        this.kafkaProducer = kafkaProducer;
    }

    public Page<Plan> getPlansAtivos(Pageable pageable) {
        return planRepository.findByActiveTrue(pageable);
    }

    public Page<Plan> getAllPlans(Pageable pageable) {
        return planRepository.findAll(pageable);
    }

    public Plan findById(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.PLAN_NOT_FOUND));
    }

    @Transactional
    public Plan createPlan(PlanRequest req, String actorId, String tenantId) {
        Plan plan = new Plan();
        plan.setName(req.name());
        plan.setPlanType(req.planType());
        plan.setBillingCycle(req.billingCycle());
        plan.setValue(req.value());
        plan.setDescription(req.description());
        plan.setActive(true);
        plan.setCreatedAt(OffsetDateTime.now());
        plan.setCreatedBy(actorId);
        Plan saved = planRepository.save(plan);
        kafkaProducer.sendAuditEvent(Constants.PLAN_CREATION, toUUID(actorId), Constants.PLAN, saved.getId(), Constants.SUCCESS,
                "{\"name\":\"" + saved.getName() + "\"}", toUUID(tenantId));
        return saved;
    }

    @Transactional
    public Plan updatePlan(UUID id, PlanRequest req, String actorId, String tenantId) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.PLAN_NOT_FOUND));
        plan.setName(req.name());
        plan.setPlanType(req.planType());
        plan.setBillingCycle(req.billingCycle());
        plan.setValue(req.value());
        plan.setDescription(req.description());
        plan.setUpdatedAt(OffsetDateTime.now());
        plan.setUpdatedBy(actorId);
        Plan saved = planRepository.save(plan);
        kafkaProducer.sendAuditEvent(Constants.PLAN_UPDATE, toUUID(actorId), Constants.PLAN, saved.getId(), Constants.SUCCESS,
                "{\"name\":\"" + saved.getName() + "\"}", toUUID(tenantId));
        return saved;
    }

    @Transactional
    public Plan toggleActive(UUID id, String actorId, String tenantId) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.PLAN_NOT_FOUND));
        plan.setActive(!plan.isActive());
        plan.setUpdatedAt(OffsetDateTime.now());
        plan.setUpdatedBy(actorId);
        Plan saved = planRepository.save(plan);
        String action = saved.isActive() ? "ACTIVATE_PLAN" : "DEACTIVATE_PLAN";
        kafkaProducer.sendAuditEvent(action, toUUID(actorId), Constants.PLAN, saved.getId(), Constants.SUCCESS, null, toUUID(tenantId));
        return saved;
    }

    private UUID toUUID(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException _) { return null; }
    }
}
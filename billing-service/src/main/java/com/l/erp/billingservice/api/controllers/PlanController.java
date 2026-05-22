package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.PlanRequest;
import com.l.erp.billingservice.api.dto.PlanResponse;
import com.l.erp.billingservice.api.mappers.PlanAssembler;
import com.l.erp.billingservice.domain.Plan;
import com.l.erp.billingservice.services.PlanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;
    private final PlanAssembler assembler;

    public PlanController(PlanService planService, PlanAssembler assembler) {
        this.planService = planService;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<PlanResponse>> listar(
            Pageable pageable,
            PagedResourcesAssembler<Plan> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(planService.getPlansAtivos(pageable), assembler));
    }

    @GetMapping("/all")
    public ResponseEntity<PagedModel<PlanResponse>> listarTodos(
            Pageable pageable,
            PagedResourcesAssembler<Plan> pagedAssembler) {
        return ResponseEntity.ok(pagedAssembler.toModel(planService.getAllPlans(pageable), assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(assembler.toModel(planService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> criar(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody @Valid PlanRequest req) {
        PlanResponse response = assembler.toModel(planService.createPlan(req, userId, tenantId));
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> atualizar(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody @Valid PlanRequest req) {
        return ResponseEntity.ok(assembler.toModel(planService.updatePlan(id, req, userId, tenantId)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PlanResponse> toggleStatus(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(assembler.toModel(planService.toggleActive(id, userId, tenantId)));
    }
}